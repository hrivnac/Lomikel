// Tag the ten LSST Solar-System objects with the most indexed sky positions.
// Read-only by default. At CC run with: lomikel_all -b -s ../src/work/CC/tagLongSS.groovy -o apply=true
// Uses ss_radec (as plotES-ss-radec-top.py does); ss_mjd may be backfilling independently.
import com.Lomikel.Januser.JanusClient
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG
import com.astrolabsoftware.FinkBrowser.Januser.Classifier
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

String endpoint = binding.hasVariable('esUrl') ? binding.getVariable('esUrl').toString() : 'http://134.158.243.139:24499'
int limit = binding.hasVariable('topN') ? Integer.parseInt(binding.getVariable('topN').toString()) : 10
if (limit < 1 || limit > 100) throw new IllegalArgumentException('topN must be between 1 and 100')
def requestedApply = binding.hasVariable('apply') ? binding.getVariable('apply') : false
if (!(requestedApply == true || requestedApply == false || requestedApply == 'true' || requestedApply == 'false')) {
  throw new IllegalArgumentException('apply must be true or false')
}
boolean writeGraph = requestedApply == true || requestedApply == 'true'
if (!(endpoint ==~ /^https?:\/\/[^\s\/]+(?::\d+)?\/?$/)) throw new IllegalArgumentException('invalid Elasticsearch URL')

// Ranking is calculated by Elasticsearch across the whole ss_radec index;
// neither a sample of documents nor the largest MJD value is a point count.
def query = [size:limit, _source:false, track_total_hits:true,
             query:[exists:[field:'location']],
             sort:[[_script:[type:'number', script:[lang:'painless', source:"doc['location'].size()"], order:'desc']]]]
def conn = (HttpURLConnection)new URL(endpoint.replaceAll('/+$', '') + '/ss_radec/_search').openConnection()
conn.requestMethod = 'POST'
conn.connectTimeout = 10000
conn.readTimeout = 120000
conn.instanceFollowRedirects = false
conn.doOutput = true
conn.setRequestProperty('Content-Type', 'application/json')
try {
  conn.outputStream.withCloseable { it.write(JsonOutput.toJson(query).getBytes('UTF-8')) }
  if (conn.responseCode != 200) throw new IllegalStateException("Elasticsearch search returned HTTP ${conn.responseCode}")
  def data = new JsonSlurper().parse(conn.inputStream, 'UTF-8')
  if (data.timed_out != false || !(data._shards instanceof Map) ||
      data._shards.failed != 0 || data._shards.successful != data._shards.total ||
      !(data.hits?.hits instanceof List)) {
    throw new IllegalStateException('Elasticsearch search timed out, failed a shard, or omitted hits')
  }
  def hits = data.hits.hits
  def total = data.hits.total
  if (!(total instanceof Map) || total.relation != 'eq' ||
      !(total.value instanceof Number) || total.value.longValue() < 0 ||
      total.value.doubleValue() != total.value.longValue() ||
      hits.size() != Math.min(limit, total.value.longValue())) {
    throw new IllegalStateException('Elasticsearch top results are truncated or total hit count is invalid')
  }
  if (hits.isEmpty()) throw new IllegalStateException('No SS objects with positions found')
  def seen = [] as Set
  def ranked = hits.collect { hit ->
    String oid = hit._id?.toString()
    if (hit._index != 'ss_radec' || !(oid ==~ /[0-9]+/) || !seen.add(oid)) {
      throw new IllegalArgumentException('Invalid or duplicate SS object ID in search response')
    }
    def n = hit.sort instanceof List && hit.sort.size() == 1 ? hit.sort[0] : null
    if (!(n instanceof Number) || n.longValue() < 1 || n.doubleValue() != n.longValue()) {
      throw new IllegalStateException('Invalid location cardinality for an SS object')
    }
    [oid:oid, points:n.longValue()]
  }
  if (ranked.size() > limit || ranked != ranked.sort(false) { a, b -> b.points <=> a.points }) {
    throw new IllegalStateException('Invalid Elasticsearch ranking order or result count')
  }
  println "LongSS candidates (ss_radec.location cardinality): " + ranked.collect { "${it.oid}=${it.points}" }.join(', ')
  if (!writeGraph) {
    println 'DRY RUN: no JanusGraph connection or writes; pass apply=true to register the LongSS tag'
    return ranked
  }

  // registerOCol creates the SS object vertex if it is not yet in JanusGraph,
  // then attaches it to the LongSS OCol; each call commits independently.
  JanusClient jc = null
  try {
    def gr
    if (binding.hasVariable('graphFactory')) gr = binding.getVariable('graphFactory').call()
    else {
      jc = new JanusClient('/opt/janusgraph-1/conf/gremlin-server/CC.properties')
      gr = new FinkGremlinRecipiesG(jc)
    }
    def classifier = Classifier.instance('TAG', 'LSST', '')
    def g = gr.g()
    // TagClassifier.survey() is ANY even for instance('TAG','LSST','').
    // Select the exact OCol identity registerOCol will create/use.
    def ocol = { -> g.V().has('lbl','OCol').has('classifier',classifier.name())
                      .has('survey',classifier.survey()).has('flavor',classifier.flavor())
                      .has('cls','LongSS') }
    def members = { -> ocol().out('deepcontains').values('objectId').toList().collect { it.toString() } }
    def desired = ranked.collect { it.oid } as Set
    // registerOCol commits each object separately. If any step fails, rerun
    // apply=true: the selected memberships are idempotent and stale edges are
    // removed only after every selected registration succeeds.
    try {
    ranked.each { row ->
      gr.registerOCol(classifier, 'LongSS', row.oid, 1.0d, '', '')
      println "Tagged ${row.oid} (${row.points} positions)"
    }
    (members().toSet() - desired).each { oldId ->
      // Drop only this OCol's stale edge, never the shared object vertex.
      ocol().outE('deepcontains').as('oldEdge').inV().has('lbl','object')
            .has('objectId',oldId).select('oldEdge').drop().iterate()
      gr.commit()
      println "Removed stale LongSS membership ${oldId}"
    }
    def actual = members()
    if (actual.size() != desired.size() || actual.toSet() != desired) {
      throw new IllegalStateException('LongSS membership verification failed; rerun apply=true to reconcile')
    }
    println "Verified ${actual.size()} LongSS members"
    } catch (Exception failure) {
      System.err.println('LongSS update incomplete; earlier registrations may have committed. Rerun apply=true to reconcile after resolving the error.')
      throw failure
    }
    return ranked
  } finally {
    if (jc != null) jc.close()
  }
} finally {
  conn.disconnect()
}

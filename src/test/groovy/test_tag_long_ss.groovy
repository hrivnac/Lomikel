import com.sun.net.httpserver.HttpServer
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG
import groovy.json.JsonOutput
import java.net.InetSocketAddress
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph

File script = new File('src/work/CC/tagLongSS.groovy')
assert script.text.contains("new JanusClient('/opt/janusgraph-1/conf/gremlin-server/CCRW.properties')") :
  'production graph mutations must use the existing writable CC configuration'
def requests = []
def response = [
  timed_out:false, _shards:[total:2,successful:2,failed:0],
  hits:[total:[value:2,relation:'eq'], hits:[
    [_index:'ss_radec',_id:'17002850058135155',sort:[14]],
    [_index:'ss_radec',_id:'17002850058135156',sort:[11]]]]
]
def server = HttpServer.create(new InetSocketAddress('127.0.0.1', 0), 0)
server.createContext('/ss_radec/_search') { exchange ->
  requests << [path:exchange.requestURI.path, body:exchange.requestBody.text]
  byte[] payload = JsonOutput.toJson(response).bytes
  exchange.responseHeaders.set('Content-Type','application/json')
  exchange.sendResponseHeaders(200,payload.length)
  exchange.responseBody.withCloseable { it.write(payload) }
}
server.start()
def url = "http://127.0.0.1:${server.address.port}"
def registered = []
class RecordingTraversal {
  String id
  Set known
  Closure members
  RecordingTraversal V() { this }
  RecordingTraversal has(String key, Object value) { if (key == 'objectId') id = value; this }
  RecordingTraversal count() { this }
  long next() { known.contains(id) ? 1L : 0L }
  RecordingTraversal out(String edge) { this }
  RecordingTraversal values(String key) { this }
  List toList() { members() }
}
def graph = [g:{ -> new RecordingTraversal(known:['17002850058135155','17002850058135156'] as Set, members:{ -> registered*.getAt(2).unique() })},
  registerOCol:{ Object classifier, String cls, String id, double weight, String instances, String weights ->
    registered << [classifier.toString(), cls, id, weight, instances, weights]
  }]
def run = { Map overrides ->
  def b = new Binding([esUrl:url, topN:2, apply:false, graphFactory:{ -> graph }] + overrides)
  new GroovyShell(this.class.classLoader,b).evaluate(script)
}
try {
  def dry = run([:])
  assert dry*.oid == ['17002850058135155','17002850058135156']
  assert dry*.points == [14,11]
  assert registered.empty
  assert requests.size() == 1
  def query = new groovy.json.JsonSlurper().parseText(requests[0].body)
  assert requests[0].path == '/ss_radec/_search'
  assert query.size == 2
  assert query._source == false
  assert query.sort[0]._script.script.source == "doc['location'].size()"

  run([apply:true])
  assert registered.size() == 2
  assert registered.every { it[0] == 'TAG[ANY]' && it[1] == 'LongSS' && it[3] == 1.0d && it[4] == '' && it[5] == '' }
  assert registered*.getAt(2) == ['17002850058135155','17002850058135156']

  registered.clear()
  response._shards.failed = 1
  try {run([apply:true]); assert false: 'partial shards must fail'}
  catch (IllegalStateException expected) { assert expected.message.contains('shard') }
  assert registered.empty
  response._shards.failed = 0
  response.hits.total.value = 3
  def dropped = response.hits.hits.remove(1)
  try {run([apply:true]); assert false: 'truncated top results must fail before pruning'}
  catch (IllegalStateException expected) { assert expected.message.contains('truncated') }
  assert registered.empty
  response.hits.hits << dropped
  response.hits.total.value = 2
  response.hits.hits[0]._id = "17002850058135155');g.V().drop();//"
  try {run([apply:true]); assert false: 'invalid object ID must fail'}
  catch (IllegalArgumentException expected) { assert expected.message.contains('ID') }
  assert registered.empty
  response.hits.hits[0]._id = '17002850058135155'
  graph.g = { -> new RecordingTraversal(known:[] as Set, members:{ -> registered*.getAt(2).unique() }) }
  run([apply:true])
  assert registered*.getAt(2) == ['17002850058135155','17002850058135156'],
    'registerOCol is responsible for creating absent SS object vertices'

  def g = TinkerGraph.open().traversal()
  def real = new FinkGremlinRecipiesG(g)
  def live = [graphFactory:{ -> real },apply:true]
  run(live)
  def other = g.addV('OCol').property('lbl','OCol').property('classifier','TAG')
    .property('survey','ANY').property('flavor','').property('cls','KeepThisTag').next()
  def shared = g.V().has('lbl','object').has('objectId','17002850058135156').next()
  other.addEdge('deepcontains',shared,'lbl','deepcontains')
  def members = { -> g.V().has('lbl','OCol').has('classifier','TAG')
    .has('survey','ANY').has('flavor','').has('cls','LongSS')
    .out('deepcontains').values('objectId').toList() as Set }
  assert members() == ['17002850058135155','17002850058135156'] as Set
  response.hits.hits[1]._id = '17002850058135157'
  run(live)
  assert members() == ['17002850058135155','17002850058135157'] as Set : 'rerun must remove stale LongSS edges'
  assert g.V().has('lbl','object').has('objectId','17002850058135156').count().next() == 1L :
    'pruning a tag must never delete the object vertex'
  assert g.V(other).out('deepcontains').has('objectId','17002850058135156').count().next() == 1L :
    'pruning LongSS must not remove another OCol membership'

  response.hits.hits[0]._id = '17002850058135158'
  response.hits.hits[1]._id = '17002850058135159'
  int calls = 0
  def interrupted = [g:{ -> g },commit:{ -> real.commit() },
    registerOCol:{ Object... args ->
      if (++calls == 2) throw new IllegalStateException('simulated second registration failure')
      real.registerOCol(*args)
    }]
  try {run([graphFactory:{ -> interrupted },apply:true]); assert false: 'simulated failure must propagate'}
  catch (IllegalStateException expected) { assert expected.message.contains('simulated') }
  assert members().contains('17002850058135158'), 'first registration commits before failure'
  run(live)
  assert members() == ['17002850058135158','17002850058135159'] as Set :
    'retry must converge after a partial committed failure'
  g.close()
  println 'tagLongSS offline tests passed'
} finally { server.stop(0) }

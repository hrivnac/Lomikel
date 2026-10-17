import groovy.json.JsonSlurper

// Run from repository root: groovy src/test/lsst_physical_identity/test_groovy.groovy
class FakeClient {
  List calls = []
  Map rows = [:]
  boolean closed = false
  boolean schemaPresent = true
  def schema() { schemaPresent ? new Object() : null }
  Closure failure
  void connect(String table, String schema) { calls << ['connect', table, schema] }
  void setLimit(int n) { calls << ['limit', n] }
  void setSearchLimit(int n) { calls << ['searchLimit', n] }
  Map exactRows(List keys, String projection) {
    calls << ['exactRows', keys.toList(), projection]
    if (failure != null) failure.call()
    rows.findAll { k, v -> keys.contains(k) }
  }
  void close() { closed = true }
}
def failAs = { Class type, Closure block ->
  boolean failed = false
  try { block() } catch (Exception e) { if (!type.isInstance(e)) throw e; failed = true }
  assert failed: "Expected ${type.simpleName}"
}
def identityClass = new GroovyShell(new Binding(LSST_IDENTITY_TEST:true)).evaluate(
  new File('src/work/Clusters/LSST/export_alert_identity.groovy'))
def exporter = identityClass.newInstance()
def dir = java.nio.file.Files.createTempDirectory('lsst-identity-').toFile()
try {
  def input = new File(dir, 'alerts.jsonl')
  def target = new File(dir, 'identity.jsonl')
  def opts = [kind:'dia', table:'rubin.diaSource_static', schema:'schema_5.0rc0_8.52.0',
              input:input.path, output:target.path, maxRows:3, batchSize:2]
  def alert = { k, kind='dia', table=opts.table, schema=opts.schema ->
    groovy.json.JsonOutput.toJson([kind:kind,table:table,schema:schema,row_key:k,cells:[:]]) }
  input.text = [alert('a'),alert('b'),alert('c')].join('\n')+'\n'
  def client = new FakeClient(rows:[a:['r:diaObjectId':'123','r:diaSourceId':'876','r:midpointMjdTai':'61234.125'],
                                    b:['r:diaObjectId':'456','r:midpointMjdTai':'61234.5'],c:['r:diaObjectId':'789','r:midpointMjdTai':'61235.0']])
  exporter.runExport(client, opts)
  assert client.closed
  assert client.calls[0] == ['connect',opts.table,opts.schema]
  assert client.calls.findAll { it[0]=='exactRows' } == [
    ['exactRows',['a','b'],'r:diaObjectId,r:diaSourceId,r:midpointMjdTai'],
    ['exactRows',['c'],'r:diaObjectId,r:diaSourceId,r:midpointMjdTai']]
  assert client.calls.findAll { it[0]=='limit' || it[0]=='searchLimit' }.every { it[1] <= 2 }
  assert target.readLines().collect { new JsonSlurper().parseText(it) } == [
    [row_key:'a',kind:'dia',object_id:'123',source_id:'876',mjd:'61234.125'],
    [row_key:'b',kind:'dia',object_id:'456',source_id:null,mjd:'61234.5'],
    [row_key:'c',kind:'dia',object_id:'789',source_id:null,mjd:'61235.0']]
  println 'PASS bounded exact-key DIA projection and sidecar'

  target.text = 'previous\n'
  def bad = { List<String> lines, Map rows, Map overrides=[:] ->
    input.text = lines.join('\n')+'\n'
    def c = new FakeClient(rows:rows)
    failAs(Exception) { exporter.runExport(c, opts + overrides) }
    assert c.closed && target.text == 'previous\n'
    assert dir.listFiles()*.name.toSet() == ['alerts.jsonl','identity.jsonl'] as Set
  }
  def valid = ['r:diaObjectId':'123','r:midpointMjdTai':'61234.125']
  bad([alert('a'),alert('a')],[a:valid])
  bad([alert('a'),alert('b')],[a:valid])
  bad([alert('a')],[a:valid + ['r:diaObjectId':'12.0']])
  bad([alert('a')],[a:valid + ['r:ssObjectId':'5']])
  bad([alert('a')],[a:valid + ['r:diaSourceId':'broken']])
  bad([alert('a','ss')],[a:valid])
  bad([alert('a','dia','other')],[a:valid])
  bad([alert('a','dia',opts.table,'other')],[a:valid])
  bad([alert('a'),alert('b'),alert('c'),alert('d')],[a:valid], [maxRows:3])
  bad([alert('a,b')],['a,b':valid])
  bad([alert('a')],[a:['r:diaObjectId':'123']])
  bad([alert('a')],[a:valid + ['r:midpointMjdTai':'NaN']])
  println 'PASS fail-closed invalid, incomplete, duplicate, excessive, mismatched input'

  input.text = alert('a')+'\n'
  def failure = new FakeClient(rows:[a:['r:diaObjectId':'123']], failure:{ throw new IOException('Get failed') })
  failAs(IOException) { exporter.runExport(failure,opts) }
  assert failure.closed && target.text == 'previous\n'
  println 'PASS HBase I/O failure preserves previous sidecar'
  def noSchema = new FakeClient(schemaPresent:false, rows:[a:['r:diaObjectId':'123','r:midpointMjdTai':'61234']])
  failAs(IllegalStateException) { exporter.runExport(noSchema, opts) }
  assert noSchema.closed && target.text == 'previous\n'
  println 'PASS unavailable requested schema fails closed'

  input.text = alert('s','ss','rubin.diaSource_sso')+'\n'
  def ssOpts = opts + [kind:'ss',table:'rubin.diaSource_sso']
  client = new FakeClient(rows:[s:['r:ssObjectId':'9007199254740993','r:diaSourceId':'7','r:midpointMjdTai':'61234.125']])
  exporter.runExport(client,ssOpts)
  assert client.calls.find { it[0]=='exactRows' } == ['exactRows',['s'],'r:ssObjectId,r:diaSourceId,r:midpointMjdTai']
  assert new JsonSlurper().parseText(target.text.trim()) == [row_key:'s',kind:'ss',object_id:'9007199254740993',source_id:'7',mjd:'61234.125']
  println 'PASS SS canonical ID remains a string (no alias inference or float coercion)'

  def parsed = identityClass.parseArgs(['--host','localhost','--port','2183','--table','rubin.diaSource_sso',
    '--schema','schema_5.0rc0_8.52.0','--kind','ss','--input','in.jsonl','--output','out.jsonl',
    '--max-rows','3','--batch-size','2'] as String[])
  assert parsed.port == 2183 && parsed.batchSize == '2'
  failAs(IllegalArgumentException) { identityClass.parseArgs(['--host','localhost','--port','2183',
    '--kind','ss','--table','rubin.diaSource_static','--schema','s','--input','in','--output','out',
    '--max-rows','3','--batch-size','2'] as String[]) }
  failAs(IllegalArgumentException) { identityClass.validate(opts + [batchSize:101]) }
  failAs(IllegalArgumentException) { identityClass.validate(opts + [maxRows:10001]) }
  println 'PASS CLI explicit provenance and finite bounds'
} finally { dir.listFiles().each { it.delete() }; dir.delete() }

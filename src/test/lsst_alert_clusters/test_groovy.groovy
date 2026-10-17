import groovy.json.JsonSlurper

// Run with groovy src/test/lsst_alert_clusters/test_groovy.groovy from repository root.
def root = new File('.').canonicalFile
def shell = new GroovyShell(new Binding(LSST_ALERT_TEST: true))
def ExportAlerts = shell.evaluate(new File(root, 'src/work/Clusters/LSST/export_alerts.groovy'))
def ClassifyAlert = shell.evaluate(new File(root, 'src/work/Clusters/LSST/classify_alert.groovy'))
def fails = { Closure action ->
  try { action(); assert false: 'expected IllegalArgumentException' }
  catch (IllegalArgumentException expected) { }
}

def failsState = { Closure action ->
  try { action(); assert false: 'expected IllegalStateException' }
  catch (IllegalStateException expected) { }
}

class FakeHBaseClient {
  FakeHBaseClient(String host, int port) { }
  protected void handleScanIOException(java.io.IOException failure) { }
  void simulateReadFailure() { handleScanIOException(new java.io.IOException('injected scanner failure')) }
}
// Lomikel HBaseClient normally logs and swallows scan I/O failures.
// The exporter must override that hook to fail the run rather than publish partial results.
def strictClient = ExportAlerts.strictClientClass(FakeHBaseClient).getConstructor(String, int.class).newInstance('test', 1)
failsState { strictClient.simulateReadFailure() }
println 'PASS scanner I/O error cannot be swallowed'

// A failed export must not publish partial JSONL or overwrite the previous file.
def testDir = java.nio.file.Files.createTempDirectory(new File('.').toPath(), '.lsst-export-test-').toFile()
try {
  def target = new File(testDir, 'alerts.jsonl')
  target.text = 'previous complete export\n'
  failsState { ExportAlerts.writeAtomic(target) { Writer writer ->
    writer.write('partial row\n')
    throw new IllegalStateException('injected read failure')
  } }
  assert target.text == 'previous complete export\n'
  assert testDir.listFiles()*.name == ['alerts.jsonl']
} finally {
  testDir.listFiles().each { it.delete() }
  testDir.delete()
}
println 'PASS failed export preserves prior output'

class FakeSchema {
  Map types
  Set columnNames() { new TreeSet(types.keySet()) }
  boolean isNumber(String name) { types[name] in ['float', 'double', 'integer', 'long', 'short'] }
}
class FakeClient {
  def schema
  def schema() { schema }
  def rows = [:]
  def calls = []
  boolean closed = false
  int limit, searchLimit
  void connect(String table, String schemaName) { calls << ['connect', table, schemaName] }
  void setLimit(int value) { limit = value }
  void setSearchLimit(int value) { searchLimit = value }
  Map scan(String key, Object search, String projection, long start, long stop, boolean ifkey, boolean iftime) {
    if (search == null) throw new IllegalArgumentException('null search map is invalid for exact-key Get')
    calls << ['scan', key, search, projection, start, stop, ifkey, iftime]
    rows
  }
  void close() { closed = true }
}

def schema = new FakeSchema(types: [
  'r:psfFlux':'double', 'r:ixx':'float', 'r:ra':'double', 'r:dec':'double',
  'r:mjd':'double', 'r:diaObjectId':'long', 'r:lightcurveFlux':'double',
  'r:historyCount':'integer', 'r:foo':'string', 'r:timeScale':'double'])
def client = new FakeClient(schema:schema, rows:['dia-1':[
  'r:psfFlux':'2.5', 'r:ixx':'3', 'r:ra':'22', 'r:foo':'abc', 'r:timeScale':'4']])
class FakeSearchMap { Map values; Map map() { values } }
def exporter = ExportAlerts.newInstance()
exporter.searchMapFactory = { Map m -> new FakeSearchMap(values:m) }
def out = new StringWriter()
exporter.exportRows(client, [kind:'dia', table:'alerts', schema:'s1', prefix:'dia-', limit:2], out)
assert client.closed
assert client.limit == 2 && client.searchLimit == 2
assert client.calls[0] == ['connect', 'alerts', 's1']
assert client.calls[1][1] == null
assert client.calls[1][2].map()['key:key'] == 'dia-'
assert client.calls[1][3] == 'r:ixx,r:psfFlux'
assert client.calls[1][4..7] == [0L, 0L, false, false]
assert new JsonSlurper().parseText(out.toString().trim()) == [kind:'dia',row_key:'dia-1',table:'alerts',schema:'s1',cells:['r:psfFlux':'2.5','r:ixx':'3']]
println 'PASS exporter bounded numeric projection and JSONL'

// The CLI reports actual export stats only after replacing the destination.
def summaryDir = java.nio.file.Files.createTempDirectory(new File('.').toPath(), '.lsst-summary-test-').toFile()
try {
  def target = new File(summaryDir, 'alerts.jsonl')
  def opts = [kind:'dia',table:'alerts',schema:'s1',prefix:'dia-',limit:2,output:target.path]
  def console = new ByteArrayOutputStream()
  def original = System.out
  try {
    System.setOut(new PrintStream(console, true, 'UTF-8'))
    ExportAlerts.runExport(new FakeClient(schema:schema, rows:client.rows), opts, exporter)
  } finally { System.setOut(original) }
  assert target.readLines().size() == 1
  assert console.toString('UTF-8').trim() == "Exported kind=dia alert_rows=1 output=${target.path} mode=prefix"
  assert exporter.exportRows(new FakeClient(schema:schema,rows:client.rows),opts,new StringWriter()) == [alertRows:1]
  def keysOpts = opts + [prefix:null,keys:'dia-1']
  console.reset()
  try {
    System.setOut(new PrintStream(console, true, 'UTF-8'))
    ExportAlerts.runExport(new FakeClient(schema:schema,rows:client.rows), keysOpts, exporter)
  } finally { System.setOut(original) }
  assert console.toString('UTF-8').trim() == "Exported kind=dia alert_rows=1 output=${target.path} mode=keys"
  target.text = 'previous\n'
  console.reset()
  try {
    System.setOut(new PrintStream(console, true, 'UTF-8'))
    failsState { ExportAlerts.runExport(new FakeClient(schema:schema,rows:[:]), opts, exporter) }
  } finally { System.setOut(original) }
  assert console.toString('UTF-8') == '' && target.text == 'previous\n'
} finally { summaryDir.listFiles().each { it.delete() }; summaryDir.delete() }
println 'PASS success-only alert CLI summary and returned stats'

fails { exporter.exportRows(new FakeClient(schema:schema), [kind:'dia',table:'t',schema:'s',limit:1], new StringWriter()) }
println 'PASS exporter rejects unbounded request'

def model = [format:'lomikel-lsst-pca-kmeans-v1', kind:'dia', features:['r:psfFlux','r:ixx'],
  impute:[2.0,3.0], mean:[1.0,1.0], scale:[1.0,2.0],
  components:[[1.0,0.0],[0.0,1.0]], centers:[[0.0,0.0],[2.0,1.0]],
  radii:[0.5,0.5], explained_variance_ratio:[0.6,0.3]]
def classifier = ClassifyAlert.newInstance()
def prediction = classifier.classify(model, [kind:'dia',row_key:'dia-1',cells:['r:psfFlux':'3.0']])
assert prediction.class_id == 1 && prediction.distance == 0.0 && prediction.outlier == false
assert prediction.row_key == 'dia-1' && prediction.kind == 'dia'
println 'PASS inference median imputation, standardization and nearest center'

assert ExportAlerts.usableColumn('r:ixx')
assert !ExportAlerts.usableColumn('r:width')
assert !ExportAlerts.usableColumn('r:diaObjectId')
assert !ExportAlerts.usableColumn('r:ra')
assert !ExportAlerts.usableColumn('r:lightcurveFlux')
assert !ExportAlerts.usableColumn('r:mjd')
assert !ExportAlerts.usableColumn('r:midPointTai')
assert !ExportAlerts.usableColumn('r:centroidX')
assert !ExportAlerts.usableColumn('r:visit')
assert !ExportAlerts.usableColumn('r:forcedFlux')
assert !ExportAlerts.usableColumn('history:flux')
assert !ExportAlerts.usableColumn('f:clf_varstar')
assert !ExportAlerts.usableColumn('f:xm_distance')
assert ExportAlerts.usableColumn('r:psfFlux')
assert ExportAlerts.usableColumn('r:apFlux')
assert ExportAlerts.usableColumn('r:snr')
assert ExportAlerts.usableColumn('r:trailLength')
assert ExportAlerts.usableColumn('r:extendedness')
assert !ExportAlerts.usableColumn('r:trailRa')
println 'PASS feature exclusion preserves width'

def parsed = ExportAlerts.parseArgs(['--host','localhost','--port','2181','--table','t','--schema','s',
  '--kind','ss','--keys','ss-1,ss-2','--limit','2','--output','alerts.jsonl'] as String[])
assert parsed.limit == 2 && parsed.port == 2181
fails { ExportAlerts.parseArgs(['--host','localhost','--port','2181','--table','t','--schema','s',
  '--kind','ss','--prefix','ss-','--keys','ss-1','--limit','2','--output','alerts.jsonl'] as String[]) }
fails { ExportAlerts.parseArgs(['--host','localhost','--port','2181','--table','t','--schema','s',
  '--kind','ss','--prefix','ss-','--limit','0','--output','alerts.jsonl'] as String[]) }
fails { ExportAlerts.parseArgs(['--host','localhost','--port','2181','--table','t','--schema','s',
  '--kind','ss','--prefix','ss-,','--limit','2','--output','alerts.jsonl'] as String[]) }
println 'PASS CLI explicit bounds'

def keysClient = new FakeClient(schema:schema, rows:['ss-1':['r:psfFlux':'not-a-number','r:ixx':'5']])
exporter.exportRows(keysClient, [kind:'ss',table:'t',schema:'s',keys:'ss-1',limit:1], out = new StringWriter())
assert keysClient.calls[1][1] == 'ss-1' && keysClient.calls[1][2] == [:]
assert new JsonSlurper().parseText(out.toString().trim()).cells == ['r:ixx':'5']
// A partial or empty exact-key response cannot silently become training input.
failsState { exporter.exportRows(new FakeClient(schema:schema, rows:['ss-1':['r:ixx':'5']]),
  [kind:'ss',table:'t',schema:'s',keys:'ss-1,ss-2',limit:2], new StringWriter()) }
failsState { exporter.exportRows(new FakeClient(schema:schema, rows:['ss-1':[:]]),
  [kind:'ss',table:'t',schema:'s',keys:'ss-1',limit:1], new StringWriter()) }
println 'PASS exact-key completeness is mandatory'

def far = classifier.classify(model, [kind:'dia',row_key:'dia-2',cells:['r:psfFlux':'20','r:ixx':'3']])
assert far.class_id == 1 && far.outlier && far.distance > model.radii[1]
fails { classifier.classify(model, [kind:'ss',row_key:'ss-1',cells:[:]]) }
fails { classifier.classify(model, [kind:'dia',row_key:'dia-1',cells:['r:psfFlux':'NaN']]) }
fails { classifier.classify(model + [scale:[0,1]], [kind:'dia',row_key:'dia-1',cells:[:]]) }
fails { classifier.classify(model + [centers:[[1.0]]], [kind:'dia',row_key:'dia-1',cells:[:]]) }
fails { classifier.classify(model + [radii:[Double.NaN,1]], [kind:'dia',row_key:'dia-1',cells:[:]]) }
fails { classifier.classify(model + [features:['r:raErr','r:ixx']], [kind:'dia',row_key:'dia-1',cells:['r:ixx':'3']]) }
fails { classifier.classify(model + [features:['r:arbitraryMetric','r:ixx']], [kind:'dia',row_key:'dia-1',cells:['r:ixx':'3']]) }
fails { classifier.classify(model, [kind:'dia',row_key:'dia-empty',cells:[:]]) }
println 'PASS model and row validation, outlier rule'

fails { classifier.classify(model + [provenance:[table:'rubin.diaSource_static',schema:'s1']],
  [kind:'dia',row_key:'dia-1',table:'rubin.diaSource_sso',schema:'s1',cells:['r:psfFlux':'3']]) }
fails { classifier.classify(model + [provenance:[table:'rubin.diaSource_static',schema:'s1']],
  [kind:'dia',row_key:'dia-1',table:'rubin.diaSource_static',schema:'s2',cells:['r:psfFlux':'3']]) }
println 'PASS JSON-row inference refuses provenance mismatch'

fails { classifier.classifyFromClient(model + [provenance:[table:'rubin.diaSource_static',schema:'s1']],
  new FakeClient(schema:schema, rows:['dia-1':['r:psfFlux':'3']]),
  [kind:'dia',table:'rubin.diaSource_sso',schema:'s1','row-key':'dia-1']) }
println 'PASS model provenance must match HBase table'

// HBase inference must fetch one exact key, project only model features and close.
def inferenceClient = new FakeClient(schema:schema, rows:['dia-1':['r:psfFlux':'3']])
def fromHbase = classifier.classifyFromClient(model, inferenceClient,
  [kind:'dia',table:'alerts',schema:'s1','row-key':'dia-1'])
assert fromHbase.class_id == 1 && fromHbase.distance == 0d
assert inferenceClient.closed && inferenceClient.limit == 1 && inferenceClient.searchLimit == 1
assert inferenceClient.calls[1][1] == 'dia-1' && inferenceClient.calls[1][2] == [:]
assert inferenceClient.calls[1][3] == 'r:psfFlux,r:ixx'
println 'PASS HBase exact-key inference projection and close'

def gcliBinding = new Binding([cli:new Object(), host:'localhost', port:2181,
  table:'rubin.diaSource_static', schema:'schema_5.0rc0_8.52.0', kind:'dia',
  prefix:'dia-', limit:3, output:'dia.jsonl'])
assert ExportAlerts.argvFromBinding(gcliBinding).toList().containsAll(['--table','rubin.diaSource_static','--prefix','dia-'])
println 'PASS GCLI named binding options'
assert ClassifyAlert.argvFromBinding(new Binding([cli:new Object(),model:'dia.json',
  host:'localhost',port:2181,table:'rubin.diaSource_static',schema:'schema_5.0rc0_8.52.0',
  kind:'dia',rowKey:'dia-1'])).toList().containsAll(['--row-key','dia-1','--model','dia.json'])
println 'PASS GCLI inference named binding options'

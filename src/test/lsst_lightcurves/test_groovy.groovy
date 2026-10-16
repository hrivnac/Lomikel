import groovy.json.JsonSlurper

// Offline integration test: /opt/groovy-4.0.9/bin/groovy src/test/lsst_lightcurves/test_groovy.groovy
def root = new File('.').canonicalFile
def shell = new GroovyShell(new Binding(LSST_LC_TEST:true))
def Export = shell.evaluate(new File(root, 'src/work/LightCurves/LSST/export_lightcurves.groovy'))
def Classify = shell.evaluate(new File(root, 'src/work/LightCurves/LSST/classify_lightcurve.groovy'))
def rejects = { Closure action ->
  boolean thrown = false
  try { action() } catch (IllegalArgumentException | IllegalStateException expected) { thrown = true }
  assert thrown: 'expected validation failure'
}
class FakeSearchMap { Map values; Map map() { values } }
class FakeClient {
  Map rows = [:]
  List calls = []
  int limit, searchLimit
  boolean closed = false
  def schema() { new Object() }
  void connect(String table, String schema) { calls << ['connect',table,schema] }
  void setLimit(int n) { limit = n }
  void setSearchLimit(int n) { searchLimit = n }
  Map scan(String key, Object search, String columns, long a, long b, boolean c, boolean d) {
    calls << ['scan',key,search,columns,a,b,c,d]
    rows.findAll { k,v -> k.startsWith(search.map()['key:key']) }
  }
  void close() { closed = true }
}
class FakeBase {
  FakeBase(String host, int port) { }
  protected void handleScanIOException(java.io.IOException failure) { }
  void failRead() { handleScanIOException(new java.io.IOException('partial scan')) }
}
def prefix = '000_170019696133800000_'
def anchor = prefix + '61088.09744609636'
def cells = { String id, String source, String band, String mjd, String flux ->
  ['r:diaObjectId':id,'r:diaSourceId':source,'r:band':band,
   'r:midpointMjdTai':mjd,'r:psfFlux':flux,'r:psfFluxErr':'2']
}
def exporter = Export.newInstance()
exporter.searchMapFactory = { Map m -> new FakeSearchMap(values:m) }
def client = new FakeClient(rows:[(anchor):cells('170019696133800000','170019696133800000','r','61088.09744609636','1327.615'),
  (prefix+'61089.0'):cells('170019696133800000','170019696133800001','g','61089.0','-20'),
  (prefix+'61090.0'):cells('170019696133800000','170019696133800002','y','61090.0','30')])
def out = new StringWriter()
exporter.exportObjects(client,[kind:'dia',table:'rubin.diaSource_static',schema:'schema_5.0rc0_8.52.0',prefixes:prefix,'max-objects':1,'max-points':3],out)
def row = new JsonSlurper().parseText(out.toString().trim())
assert row.kind == 'dia' && row.object_id == '170019696133800000' && row.object_key == prefix
assert row.complete == true && row.sources*.band == ['r','g','y']
assert row.sources[0] == [row_key:anchor,source_id:'170019696133800000',mjd:'61088.09744609636',band:'r',flux:'1327.615',flux_err:'2']
assert client.limit == 4 && client.searchLimit == 4 && client.closed
assert client.calls[1][1] == null && client.calls[1][2].map() == ['key:key':prefix]
assert client.calls[1][3] == 'r:diaObjectId,r:ssObjectId,r:diaSourceId,r:midpointMjdTai,r:band,r:psfFlux,r:psfFluxErr'
println 'PASS DIA bounded object export'

rejects { exporter.exportObjects(new FakeClient(rows:client.rows),[kind:'dia',table:'t',schema:'s',prefixes:'000_','max-objects':1,'max-points':3],new StringWriter()) }
rejects { exporter.exportObjects(new FakeClient(rows:client.rows),[kind:'dia',table:'t',schema:'s',prefixes:prefix,'max-objects':1,'max-points':2],new StringWriter()) }
rejects { exporter.exportObjects(new FakeClient(rows:client.rows + [(prefix+'61091'):cells('other','new','u','61091','1')]),[kind:'dia',table:'t',schema:'s',prefixes:prefix,'max-objects':1,'max-points':4],new StringWriter()) }
rejects { exporter.exportObjects(new FakeClient(rows:client.rows + [(prefix+'61091'):cells('170019696133800000','170019696133800000','u','61091','1')]),[kind:'dia',table:'t',schema:'s',prefixes:prefix,'max-objects':1,'max-points':4],new StringWriter()) }
rejects { exporter.exportObjects(new FakeClient(rows:client.rows.collectEntries { k,v -> [(k):v + ['r:psfFluxErr':'0']] }),[kind:'dia',table:'t',schema:'s',prefixes:prefix,'max-objects':1,'max-points':4],new StringWriter()) }
rejects { exporter.exportObjects(new FakeClient(rows:client.rows + [(prefix):cells('170019696133800000','999','u','61091','1')]),[kind:'dia',table:'t',schema:'s',prefixes:prefix,'max-objects':1,'max-points':4],new StringWriter()) }
println 'PASS incomplete/unsafe scans rejected'

// A client violating the prefix request must never leak a foreign row into output.
def foreignClient = new FakeClient() {
  Map scan(String key, Object search, String columns, long a, long b, boolean c, boolean d) {
    [(anchor):rows[anchor], 'other_1':rows[anchor]]
  }
}
foreignClient.rows = client.rows
rejects { exporter.exportObjects(foreignClient,[kind:'dia',table:'t',schema:'s',prefixes:prefix,'max-objects':1,'max-points':3],new StringWriter()) }
assert foreignClient.closed
// A late object failure cannot leave a partially written caller output.
def secondPrefix = '000_222_'
def partial = new FakeClient(rows:client.rows)
def untouched = new StringWriter()
rejects { exporter.exportObjects(partial,[kind:'dia',table:'t',schema:'s',prefixes:prefix+','+secondPrefix,
  'max-objects':2,'max-points':3],untouched) }
assert untouched.toString() == '' && partial.closed
println 'PASS foreign rows and late failures never produce partial output'

def ssPrefix = '00_J00U00G_'
def ssClient = new FakeClient(rows:[(ssPrefix+'61230.39294164734'):[
  'r:ssObjectId':'20882132422373447','r:diaSourceId':'170644270566342686',
  'r:band':'z','r:midpointMjdTai':'61230.39294164734','r:psfFlux':'5','r:psfFluxErr':'1']])
exporter.exportObjects(ssClient,[kind:'ss',table:'rubin.diaSource_sso',schema:'schema_5.0rc0_8.52.0',prefixes:ssPrefix,'max-objects':1,'max-points':3],out = new StringWriter())
assert new JsonSlurper().parseText(out.toString().trim()).object_id == '20882132422373447'
println 'PASS SS alias prefix uses canonical cell ID'

assert Export.parseArgs(['--host','localhost','--port','2183','--table','t','--schema','s','--kind','dia',
  '--prefixes',prefix,'--max-objects','1','--max-points','3','--output','x'] as String[]).'max-points' == 3
rejects { Export.parseArgs(['--host','h','--port','2183','--table','t','--schema','s','--kind','dia','--prefixes','000_',
  '--max-objects','1','--max-points','3','--output','x'] as String[]) }
rejects { Export.parseArgs(['--host','h','--port','2183','--table','t','--schema','s','--kind','dia','--prefixes',prefix+',',
  '--max-objects','1','--max-points','3','--output','x'] as String[]) }
rejects { Export.parseArgs(['--host','h','--port','2183','--table','t','--schema','s','--kind','dia','--prefixes',prefix+',00_J00U00G_',
  '--max-objects','1','--max-points','3','--output','x'] as String[]) }
rejects { Export.parseArgs(['--host','h','--port','2183','--table','t','--schema','s','--kind','dia','--prefixes',prefix,
  '--max-objects','101','--max-points','3','--output','x'] as String[]) }
rejects { Export.parseArgs(['--host','h','--port','2183','--table','t','--schema','s','--kind','dia','--prefixes',prefix,
  '--max-objects','1','--max-points','513','--output','x'] as String[]) }

def strict = Export.strictClientClass(FakeBase).getConstructor(String,int.class).newInstance('test',1)
rejects { strict.failRead() }
def dir = java.nio.file.Files.createTempDirectory(new File('.').canonicalFile.toPath(), '.lsst-lc-test-').toFile()
try {
  def target = new File(dir,'out.jsonl'); target.text = 'previous\n'
  rejects { Export.writeAtomic(target) { Writer w -> w.write('partial\n'); throw new IllegalStateException('fail') } }
  assert target.text == 'previous\n' && dir.listFiles()*.name == ['out.jsonl']
} finally { dir.listFiles().each { it.delete() }; dir.delete() }
println 'PASS strict scan and atomic publication'

def n = 6 * 2 * 3 + 2
def weights = (0..<n).collect { i -> [i == 0 ? 1d : 0d,0d] }
def model = [format:'lomikel-lsst-lc-nn-kmeans-v1',kind:'dia',bands:['u','g','r','i','z','y'],bins:2,
  feature_mean:(0..<n).collect { 0d },feature_scale:(0..<n).collect { 1d },
  encoder_weights:weights,encoder_bias:[0d,0d],centers:[[0d,0d],[0.5d,0d]],radii:[0.1d,0.3d],
  provenance:[table:'rubin.diaSource_static',schema:'schema_5.0rc0_8.52.0'],model_id:'model-1']
model.model_id = Classify.modelDigest(model)
def classifier = Classify.newInstance()
classifier.searchMapFactory = { Map m -> new FakeSearchMap(values:m) }
def prediction = classifier.classify(model,row)
assert prediction.keySet() == ['kind','object_id','class_id','distance','outlier','model_id','bands_present','source_count'] as Set
assert prediction.object_id == '170019696133800000' && prediction.model_id == model.model_id
assert prediction.bands_present == ['g','r','y'] && prediction.source_count == 3
assert prediction.class_id == 0 && prediction.distance == 0d && !prediction.outlier
println 'PASS NN object inference and missing bands'

// Binning uses mean asinh per occupied bin, then tanh of the standardized feature.
def numerical = row + [sources:[
  [row_key:prefix+'61088.0',source_id:'1',mjd:61088d,band:'u',flux:1d,flux_err:1d],
  [row_key:prefix+'61088.1',source_id:'2',mjd:61088.1d,band:'u',flux:3d,flux_err:1d],
  [row_key:prefix+'61090.0',source_id:'3',mjd:61090d,band:'r',flux:-2d,flux_err:1d]]]
def numericalPrediction = classifier.classify(model,numerical)
def expectedFlux = (Math.log(0.5d + Math.sqrt(1.25d)) + Math.log(1.5d + Math.sqrt(3.25d))) / 2d
assert Math.abs(numericalPrediction.distance - Math.abs(Math.tanh(expectedFlux) - 0.5d)) < 1e-12
assert numericalPrediction.class_id == 1
// A very large negative flux must remain finite rather than cancelling log arguments.
def extreme = numerical + [sources:numerical.sources.collect { source ->
  int i = Integer.parseInt(source.source_id) - 1
  source + [flux:['-1e308','1','1'][i],band:['u','g','r'][i]] }]
assert Double.isFinite(classifier.classify(model,extreme).distance)
def extremeFeatures = Classify.features(extreme,2)[0]
assert Math.abs(extremeFeatures[0] + Math.log(1e308d) + Math.log(2d)) < 1e-12
// Asinh(x) is x to machine precision near zero; log(1+x) would round to zero.
def tiny = numerical + [sources:numerical.sources.collect { source ->
  int i = Integer.parseInt(source.source_id) - 1
  source + [flux:['1e-20','1','1'][i],band:['u','g','r'][i]] }]
def tinyFeatures = Classify.features(tiny,2)[0]
assert tinyFeatures[0] > 0d && Math.abs(tinyFeatures[0] - 1e-20d) < 1e-35d
def nearThreshold = tiny + [sources:tiny.sources.collect { source ->
  source.source_id == '1' ? source + [flux:'1e-8'] : source }]
assert Math.abs(Classify.features(nearThreshold,2)[0][0] - 1e-8d) < 1e-20d
println 'PASS equal-weight binning, embedding, and extreme negative flux'

rejects { classifier.classify(model + [encoder_weights:([[0.9d,0d]] + weights.tail())],row) }
rejects { classifier.classify(model + [centers:[[0.1d,0d],[0.5d,0d]]],row) }
rejects { classifier.classify(model + [radii:[0.2d,0.3d]],row) }
rejects { classifier.classify(model + [provenance:model.provenance + [schema:'changed']],row) }
rejects { classifier.classify(model + [model_id:'0' * 64],row) }
rejects { classifier.classify(model,row + [complete:false]) }
rejects { classifier.classify(model,row + [schema:'wrong']) }
rejects { classifier.classify(model,row + [sources:row.sources.take(2)]) }
rejects { classifier.classify(model,row + [sources:[row.sources[0] + [row_key:prefix]] + row.sources.tail()]) }
rejects { classifier.classify(model,row + [sources:(0..512).collect { i ->
  row.sources[0] + [row_key:prefix+'extra'+i,source_id:(100000+i).toString(),mjd:(61090d+i)] }]) }
rejects { classifier.classify(model,row + [sources:row.sources + row.sources[0]]) }
rejects { classifier.classify(model + [encoder_weights:weights.take(n-1)],row) }
rejects { classifier.classify(model + [radii:[Double.NaN,1d]],row) }
rejects { classifier.classify(model + [feature_scale:([0d] + model.feature_scale.tail())],row) }
rejects { classifier.classify(model + [bins:0],row) }
println 'PASS model, provenance, and input validation'

def inference = new FakeClient(rows:client.rows)
def fromClient = classifier.classifyFromClient(model,inference,[kind:'dia',table:'rubin.diaSource_static',schema:'schema_5.0rc0_8.52.0','row-key':anchor,'max-points':3])
assert fromClient == prediction && inference.closed && inference.limit == 4 && inference.searchLimit == 4
assert inference.calls[1][2].map() == ['key:key':prefix]
rejects { classifier.classifyFromClient(model,new FakeClient(rows:client.rows.findAll { it.key != anchor }),
  [kind:'dia',table:'rubin.diaSource_static',schema:'schema_5.0rc0_8.52.0','row-key':anchor,'max-points':3]) }
println 'PASS anchor inference bounded and anchor verified'
assert Export.argvFromBinding(new Binding([cli:1,prefixes:prefix,maxObjects:1,maxPoints:3])).toList().containsAll(['--prefixes',prefix,'--max-points','3'])
assert Classify.argvFromBinding(new Binding([cli:1,rowKey:anchor,maxPoints:3])).toList().containsAll(['--row-key',anchor,'--max-points','3'])
println 'PASS GCLI bindings'
assert Classify.parseArgs(['--model','m.json','--row-json','o.json'] as String[]).'row-json' == 'o.json'
assert Classify.parseArgs(['--model','m.json','--row-key',anchor,'--host','localhost','--port','2183',
  '--table','rubin.diaSource_static','--schema','schema_5.0rc0_8.52.0','--kind','dia','--max-points','3'] as String[]).'max-points' == '3'
rejects { Classify.parseArgs(['--model','m','--row-key',anchor,'--host','h','--port','2183',
  '--table','t','--schema','s','--kind','dia'] as String[]) }
rejects { Classify.parseArgs(['--model','m','--row-key',anchor,'--row-json','o'] as String[]) }
rejects { Classify.parseArgs(['--model','m','--row-key',anchor,'--host','h','--port','2183',
  '--table','t','--schema','s','--kind','dia','--max-points','513'] as String[]) }
println 'PASS classifier CLI requires bounded exclusive input mode'

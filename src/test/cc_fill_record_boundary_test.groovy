/* Offline integration test of the actual CC PR classes; run with Groovy, Parquet,
 * Hadoop and compiled Lomikel classes on the classpath. No top-level CC script runs. */
import com.Lomikel.ElasticSearcher.ESClient
import org.apache.hadoop.fs.Path
import org.apache.parquet.example.data.Group
import org.apache.parquet.example.data.simple.SimpleGroupFactory
import org.apache.parquet.hadoop.ParquetWriter
import org.apache.parquet.hadoop.example.ExampleParquetWriter
import org.apache.parquet.schema.MessageTypeParser

class RecordingESClient extends ESClient {
  final List<List> writes = []
  RecordingESClient() { super('http://127.0.0.1:1') }
  @Override void putGeoPoint(String index, String field, String id, double ra, double dec) {
    writes.add([index, field, id, ra, dec])
  }
  @Override void updateGeoPointArrayWithRetry(String index, String field, String id,
                                               double ra, double dec, int retries) {
    writes.add([index, field, id, ra, dec, retries])
  }
  @Override void updateDoubleArrayWithRetry(String index, String field, String id,
                                             double mjd, int retries) {
    writes.add([index, field, id, mjd, retries])
  }
  @Override void commitWithRetry(int retries) { /* no network */ }
}

// Use the scripts' real class bodies, but discard every top-level statement.
Class loadCaller(java.nio.file.Path file, boolean oldGroupCallback = false) {
  String source = java.nio.file.Files.readString(file)
  int start = source.indexOf('public class PR extends ParquetReader {')
  int end = source.indexOf('\nreader = new PR(', start)
  assert start >= 0 && end > start : "Cannot isolate PR from ${file}"
  // NotifierURL/Info belong only to the discarded top-level execution.
  String imports = source.readLines().findAll {
    it.startsWith('import ') && !it.contains('NotifierURL') && !it.contains('Utils.Info')
  }.join('\n')
  String body = source.substring(start, end)
  if (oldGroupCallback) {
    // Mutation check: the previous callback ran on nested groups, before
    // ID-first rows had their measurements, and must not satisfy this test.
    assert body.contains('protected void endRecord()')
    body = body.replace('protected void endRecord()', 'public void endGroup()')
  }
  String safeSource = imports + '\n' + body
  new GroovyClassLoader(this.class.classLoader).parseClass(safeSource, file.fileName.toString())
    .classLoader.loadClass('PR')
}

String schemaText = '''message alert {
  optional group diaObject { optional binary diaObjectId (STRING); }
  optional group ssSource { optional binary ssObjectId (STRING); }
  optional group diaSource {
    optional double ra; optional double dec; optional double midpointMjdTai;
  }
  optional group extra { optional int32 flag; }
}'''
def schema = MessageTypeParser.parseMessageType(schemaText)
def factory = new SimpleGroupFactory(schema)
def rows = [
  [kind: 'dia', id: 'dia-one', ra: 12.5d, dec: -5.25d, mjd: 60001.25d],
  [kind: 'ss', id: 'ss-one', ra: 34.5d, dec: 6.75d, mjd: 60002.5d],
  [kind: 'none', id: null, ra: 999d, dec: 999d, mjd: 999d],
  [kind: 'dia', id: 'dia-two', ra: 78.25d, dec: 9.5d, mjd: 60004.75d]
]
java.nio.file.Path temp = java.nio.file.Files.createTempDirectory('cc-fill-record-')
try {
  Path file = new Path(temp.resolve('rows.parquet').toUri())
  try (ParquetWriter<Group> writer = ExampleParquetWriter.builder(file).withType(schema).build()) {
    rows.each { row ->
      Group group = factory.newGroup()
      if (row.kind == 'dia') group.addGroup('diaObject').append('diaObjectId', row.id)
      if (row.kind == 'ss') group.addGroup('ssSource').append('ssObjectId', row.id)
      group.addGroup('diaSource').append('ra', row.ra).append('dec', row.dec)
           .append('midpointMjdTai', row.mjd)
      group.addGroup('extra').append('flag', 1)
      writer.write(group)
    }
  }

  java.nio.file.Path cc = java.nio.file.Paths.get('src/work/CC')
  [
    'fillES-radec.groovy': [
      ['dia_radec', 'location', 'dia-one', 12.5d, -5.25d],
      ['ss_radec', 'location', 'ss-one', 34.5d, 6.75d, 10],
      ['dia_radec', 'location', 'dia-two', 78.25d, 9.5d]
    ],
    'fillES-mjd.groovy': [
      ['dia_mjd', 'mjd', 'dia-one', 60001.25d, 10],
      ['ss_mjd', 'mjd', 'ss-one', 60002.5d, 10],
      ['dia_mjd', 'mjd', 'dia-two', 60004.75d, 10]
    ]
  ].each { name, expected ->
    // PR is defined in both scripts: isolate binary names in independent loaders.
    Class caller = loadCaller(cc.resolve(name))
    def reader = caller.getConstructor(String).newInstance('file:/')
    def recorder = new RecordingESClient()
    def esclient = caller.getDeclaredField('esclient')
    esclient.accessible = true
    esclient.set(reader, recorder)
    reader.processFile(file)
    assert recorder.writes == expected : "${name}: ${recorder.writes} != ${expected}"
    assert reader.props().isEmpty() : "${name}: properties retained after final row"
    println "${name} PASS: ${recorder.writes.size()} record-boundary writes"

    Class oldCaller = loadCaller(cc.resolve(name), true)
    def oldReader = oldCaller.getConstructor(String).newInstance('file:/')
    def oldRecorder = new RecordingESClient()
    def oldField = oldCaller.getDeclaredField('esclient')
    oldField.accessible = true
    oldField.set(oldReader, oldRecorder)
    boolean failed = false
    try {
      oldReader.processFile(file)
    } catch (RuntimeException expectedFailure) {
      failed = true
    }
    assert failed || oldRecorder.writes != expected : "${name}: old endGroup mutation escaped detection"
    println "${name} old-endGroup mutation rejected"
  }
} finally {
  java.nio.file.Files.list(temp).withCloseable { files ->
    files.forEach { java.nio.file.Files.deleteIfExists(it) }
  }
  java.nio.file.Files.deleteIfExists(temp)
}

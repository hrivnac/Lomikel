// Read-only PCA alert row-key -> canonical HBase object identity and alert MJD.
// Groovy CLI: --host HOST --port PORT --table rubin.diaSource_static|rubin.diaSource_sso
//   --schema SCHEMA --kind dia|ss --input alerts.jsonl --output identity.jsonl
//   --max-rows N --batch-size N (max 100 keys per batch, 10000 rows per run).
// Lomikel GCLI: -b -s export_alert_identity.groovy -o
//   "host='...',port=2183,table='...',schema='...',kind='dia',input='...',output='...',maxRows=100,batchSize=20"
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ExportAlertIdentity {
  static final Map TABLES = [dia:'rubin.diaSource_static', ss:'rubin.diaSource_sso']
  static final String SOURCE = 'r:diaSourceId'
  static final String MJD = 'r:midpointMjdTai'

  static int bounded(def value, int ceiling, String name) {
    if (!(value?.toString() ==~ /[1-9][0-9]*/)) throw new IllegalArgumentException("Invalid ${name}")
    def number = new BigInteger(value.toString())
    if (number > ceiling) throw new IllegalArgumentException("${name} exceeds ${ceiling}")
    number.intValue()
  }

  static void validate(Map opts, boolean connection = false) {
    if (!TABLES.containsKey(opts.kind) || opts.table != TABLES[opts.kind] ||
        !(opts.schema instanceof String) || !opts.schema.trim() || !opts.input || !opts.output)
      throw new IllegalArgumentException('Require matching kind/table, explicit schema, input and output')
    bounded(opts.maxRows, 10000, 'maxRows')
    bounded(opts.batchSize, 100, 'batchSize')
    if (connection && (!(opts.host instanceof String) || !opts.host.trim() ||
        bounded(opts.port, 65535, 'port') == 0))
      throw new IllegalArgumentException('Require host and port')
    if (new File(opts.input.toString()).canonicalFile == new File(opts.output.toString()).canonicalFile)
      throw new IllegalArgumentException('Input and output must differ')
  }

  static String numericId(def value, String field, boolean required = true) {
    if (value == null && !required) return null
    if (!(value instanceof String) || !(value ==~ /[0-9]+/))
      throw new IllegalStateException("Missing or invalid ${field}")
    value
  }

  static String mjdValue(def value) {
    if (!(value instanceof String) || !(value ==~ /(?:[0-9]+(?:\.[0-9]+)?|\.[0-9]+)(?:[eE][+-]?[0-9]+)?/) ||
        !Double.isFinite(Double.parseDouble(value)))
      throw new IllegalStateException('Missing or invalid r:midpointMjdTai')
    value
  }

  static List parseBatch(List<String> keys, Map rows, String kind) {
    if (!(rows instanceof Map) || rows.size() != keys.size() ||
        rows.keySet() != (keys as Set))
      throw new IllegalStateException('Exact-key read is missing, duplicated or contains unexpected rows')
    String objectColumn = kind == 'dia' ? 'r:diaObjectId' : 'r:ssObjectId'
    String otherColumn = kind == 'dia' ? 'r:ssObjectId' : 'r:diaObjectId'
    keys.collect { key ->
      def cells = rows[key]
      if (!(cells instanceof Map) || cells.isEmpty() || cells.containsKey(otherColumn) ||
          (cells.keySet() - [objectColumn, SOURCE, MJD]))
        throw new IllegalStateException("Unexpected or empty cells for ${key}")
      [row_key:key, kind:kind, object_id:numericId(cells[objectColumn], objectColumn),
       source_id:numericId(cells[SOURCE], SOURCE, false), mjd:mjdValue(cells[MJD])]
    }
  }

  static void readBatch(def client, List<String> keys, Map opts, Writer output) {
    if (!keys) return
    int n = keys.size()
    client.setLimit(n)
    client.setSearchLimit(n)
    String projection = "${opts.kind == 'dia' ? 'r:diaObjectId' : 'r:ssObjectId'},${SOURCE},${MJD}"
    parseBatch(keys, client.exactRows(keys, projection), opts.kind.toString()).each { row ->
      output.write(JsonOutput.toJson(row) + '\n')
    }
  }

  int exportRows(def client, Map opts, Writer output) {
    validate(opts)
    int max = bounded(opts.maxRows, 10000, 'maxRows')
    int batchSize = bounded(opts.batchSize, 100, 'batchSize')
    int count = 0
    Set<String> seen = new HashSet<>()
    List<String> batch = []
    try {
      client.connect(opts.table.toString(), opts.schema.toString())
      if (client.schema() == null) throw new IllegalStateException('Requested schema unavailable')
      new File(opts.input.toString()).withReader('UTF-8') { reader ->
        reader.eachLine { line ->
          if (++count > max) throw new IllegalStateException('Input exceeds maxRows')
          def row
          try { row = new JsonSlurper().parseText(line) }
          catch (Exception e) { throw new IllegalArgumentException("Invalid JSONL line ${count}", e) }
          if (!(row instanceof Map) || row.kind != opts.kind || row.table != opts.table ||
              row.schema != opts.schema || !(row.row_key instanceof String) ||
              !row.row_key || row.row_key.trim() != row.row_key || row.row_key.contains(',') ||
              !seen.add(row.row_key))
            throw new IllegalStateException("Invalid provenance, key or duplicate at line ${count}")
          batch.add(row.row_key)
          if (batch.size() == batchSize) {
            readBatch(client, batch, opts, output)
            batch.clear()
          }
        }
      }
      if (!count) throw new IllegalStateException('Empty alert input')
      readBatch(client, batch, opts, output)
      output.flush()
      count
    } finally { client.close() }
  }

  static void writeAtomic(File target, Closure writer) {
    def temporary = Files.createTempFile(target.absoluteFile.parentFile.toPath(), '.lsst-identity-', '.jsonl')
    try {
      temporary.toFile().withWriter('UTF-8') { out -> writer(out) }
      Files.move(temporary, target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                 StandardCopyOption.REPLACE_EXISTING)
    } finally { Files.deleteIfExists(temporary) }
  }

  void runExport(def client, Map opts) {
    validate(opts)
    writeAtomic(new File(opts.output.toString())) { out -> exportRows(client, opts, out) }
  }

  static Map parseArgs(String[] argv) {
    Map opts = [:]
    Set names = ['host','port','table','schema','kind','input','output','max-rows','batch-size'] as Set
    for (int i = 0; i < argv.length; i += 2) {
      if (i + 1 >= argv.length || !argv[i].startsWith('--') ||
          !names.contains(argv[i].substring(2)) || opts.containsKey(argv[i].substring(2)))
        throw new IllegalArgumentException('Invalid or repeated option')
      opts[argv[i].substring(2)] = argv[i+1]
    }
    opts.maxRows = opts.remove('max-rows')
    opts.batchSize = opts.remove('batch-size')
    validate(opts, true)
    opts.port = bounded(opts.port, 65535, 'port')
    opts
  }

  static String[] argvFromBinding(Binding source) {
    List argv = []
    ['host','port','table','schema','kind','input','output','maxRows','batchSize'].each { name ->
      if (source.hasVariable(name) && source.getVariable(name) != null)
        argv.addAll(["--${name.replaceAll(/([A-Z])/, '-$1').toLowerCase()}", source.getVariable(name).toString()])
    }
    argv as String[]
  }

  static Class strictClientClass(Class base) {
    // HBaseClient.scan's Get branch logs and swallows IOException. Use a dedicated
    // exact Get path instead, projecting only identity/MJD and propagating failures.
    // The scanner hook remains strict if future reads ever take that path.
    new GroovyClassLoader(base.classLoader).parseClass("""
      class StrictLSSTIdentityClient extends ${base.name} {
        StrictLSSTIdentityClient(String host, int port) { super(host, port) }
        @Override protected void handleScanIOException(java.io.IOException error) {
          throw new IllegalStateException('HBase scanner failed', error)
        }
        java.util.Map exactRows(java.util.List keys, String projection) {
          java.util.Map result = new java.util.LinkedHashMap()
          for (String key : keys) {
            org.apache.hadoop.hbase.client.Get get = new org.apache.hadoop.hbase.client.Get(
              org.apache.hadoop.hbase.util.Bytes.toBytes(key))
            for (String column : projection.split(',')) {
              String[] parts = column.split(':')
              get.addColumn(org.apache.hadoop.hbase.util.Bytes.toBytes(parts[0]),
                            org.apache.hadoop.hbase.util.Bytes.toBytes(parts[1]))
            }
            def fetched = table().get(get) // IOException propagates; scan() swallows it.
            if (fetched == null || fetched.isEmpty() ||
                !key.equals(org.apache.hadoop.hbase.util.Bytes.toString(fetched.getRow())))
              throw new IllegalStateException('Missing or mismatched exact HBase row')
            java.util.Map cells = new java.util.LinkedHashMap()
            if (!addResult(fetched, cells, projection, false, false))
              throw new IllegalStateException('Empty exact HBase row')
            result.put(key, cells)
          }
          return result
        }
      }
    """)
  }

  static void main(String[] args) {
    Map opts = parseArgs(args)
    def base = Class.forName('com.Lomikel.HBaser.HBaseClient')
    def client = strictClientClass(base).getConstructor(String, int.class)
      .newInstance(opts.host.toString(), opts.port as int)
    new ExportAlertIdentity().runExport(client, opts)
  }
}

if (!binding.hasVariable('LSST_IDENTITY_TEST'))
  ExportAlertIdentity.main(binding.hasVariable('cli') ? ExportAlertIdentity.argvFromBinding(binding) :
    (binding.hasVariable('args') ? (String[])binding.getVariable('args') : new String[0]))
ExportAlertIdentity

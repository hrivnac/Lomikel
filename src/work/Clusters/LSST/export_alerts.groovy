// Run via Lomikel GCLI (-b -s path/to/export_alerts.groovy -o
// "host='cchbase1.in2p3.fr',port=2183,table='rubin.diaSource_static',schema='schema_5.0rc0_8.52.0',kind='dia',prefix='KNOWN',limit=100,output='dia.jsonl'")
// or directly with Groovy and the Lomikel/HBase classpath. No table writes.
// DIA example: --host cchbase1.in2p3.fr --port 2183 --table rubin.diaSource_static
//   --schema schema_5.0rc0_8.52.0 --kind dia --prefix <known-prefix> --limit 100 --output dia.jsonl
// SS example: same options with --table rubin.diaSource_sso --kind ss;
//   explicit --keys <known-key>[,<known-key>] may replace --prefix. No default table.
import groovy.json.JsonOutput

class ExportAlerts {
  Closure searchMapFactory = { Map values -> Class.forName('com.Lomikel.DB.SearchMap').getConstructor(Map).newInstance(values) }

  static boolean usableColumn(String column) {
    if (!(column instanceof String) || !column.startsWith('r:')) return false
    String name = column.substring(2)
    // Keep this allowlist aligned with MEASURED in train_clusters.py.
    // Never export coordinates, time, history, classifier/crossmatch or unknown cells.
    return name ==~ /(?:psfFlux|psfFluxErr|apFlux|apFluxErr|scienceFlux|scienceFluxErr|templateFlux|templateFluxErr|snr|extendedness|ixx|ixy|iyy|dipole(?:Flux|FluxErr|Length|Angle|Separation)|trail(?:Flux|FluxErr|Length|Width|Angle|Snr))/
  }

  static Map parseArgs(String[] argv) {
    Map opts = [:]
    Set allowed = ['host','port','table','schema','kind','prefix','keys','limit','output'] as Set
    for (int i = 0; i < argv.length; i += 2) {
      if (i + 1 >= argv.length || !argv[i].startsWith('--')) throw new IllegalArgumentException('Options require --name value')
      String name = argv[i].substring(2)
      if (!allowed.contains(name) || opts.containsKey(name) || !argv[i+1]) throw new IllegalArgumentException("Invalid option: ${argv[i]}")
      opts[name] = argv[i+1]
    }
    validate(opts, true)
    opts.limit = Integer.parseInt(opts.limit.toString())
    opts.port = Integer.parseInt(opts.port.toString())
    return opts
  }

  static void validate(Map opts, boolean connection = false) {
    if (!(opts.kind in ['dia','ss']) || !opts.table || !opts.schema || !opts.limit ||
        !(opts.limit.toString() ==~ /[1-9][0-9]*/) || Long.parseLong(opts.limit.toString()) > Integer.MAX_VALUE)
      throw new IllegalArgumentException('Require kind dia|ss, table, explicit schema and positive finite limit')
    if (connection && (!opts.host || !(opts.port?.toString() ==~ /[1-9][0-9]*/) ||
        Integer.parseInt(opts.port.toString()) > 65535 || !opts.output))
      throw new IllegalArgumentException('Require host, port (1..65535), and output')
    boolean prefix = opts.prefix != null, keys = opts.keys != null
    if (prefix == keys || (prefix && !(opts.prefix.toString() ==~ /[^,\s]+/)) ||
        (keys && !(opts.keys instanceof Collection ? opts.keys : opts.keys.toString().split(',',-1).toList()).every {
          it?.toString()?.trim() && !it.toString().contains(',') }))
      throw new IllegalArgumentException('Exactly one nonempty --prefix or --keys list is required')
    if (keys && (opts.keys instanceof Collection ? opts.keys.size() : opts.keys.toString().split(',',-1).size()) > Integer.parseInt(opts.limit.toString()))
      throw new IllegalArgumentException('Number of keys must not exceed limit')
  }

  void exportRows(def client, Map opts, Writer output) {
    validate(opts)
    try {
      client.connect(opts.table.toString(), opts.schema.toString())
      def schema = client.schema()
      if (schema == null) throw new IllegalArgumentException('Requested schema unavailable')
      List<String> columns = schema.columnNames().findAll { schema.isNumber(it) && usableColumn(it) }.sort().toList()
      if (!columns) throw new IllegalArgumentException('No eligible numeric columns in schema')
      int max = Integer.parseInt(opts.limit.toString())
      client.setLimit(max)
      client.setSearchLimit(max)
      String key = opts.keys instanceof Collection ? opts.keys.join(',') : opts.keys?.toString()
      def search = opts.prefix == null ? [:] : searchMapFactory(['key:key':opts.prefix.toString()])
      Map rows = client.scan(key, search, columns.join(','), 0L, 0L, false, false)
      if (rows.size() > max) throw new IllegalStateException('Client exceeded requested row limit')
      if (opts.keys != null) {
        List<String> requested = opts.keys instanceof Collection ? opts.keys.collect { it.toString().trim() } :
          opts.keys.toString().split(',').collect { it.trim() }
        if (rows.keySet().collect { it.toString() }.toSet() != requested.toSet() ||
            rows.size() != requested.size() || rows.values().any { !(it instanceof Map) || it.isEmpty() })
          throw new IllegalStateException('Exact-key read is incomplete or contains empty rows')
      }
      if (rows.isEmpty()) throw new IllegalStateException('No alert rows returned')
      rows.each { rowKey, cells ->
        if (opts.prefix != null && !rowKey.toString().startsWith(opts.prefix.toString()))
          throw new IllegalStateException('Returned row outside requested prefix')
        Map clean = [:]
        columns.each { col ->
          String value = cells?.get(col)?.toString()
          if (value != null && value.trim()) {
            try { if (Double.isFinite(Double.parseDouble(value))) clean[col] = value }
            catch (NumberFormatException ignored) { /* missing/non-numeric cell */ }
          }
        }
        output.write(JsonOutput.toJson([kind:opts.kind, row_key:rowKey.toString(), table:opts.table,
          schema:opts.schema, cells:clean]) + '\n')
      }
      output.flush()
    } finally { client.close() }
  }

  static String[] argvFromBinding(Binding source) {
    List<String> argv = []
    ['host','port','table','schema','kind','prefix','keys','limit','output'].each { name ->
      if (source.hasVariable(name) && source.getVariable(name) != null)
        argv.addAll(["--${name}", source.getVariable(name).toString()])
    }
    return argv as String[]
  }

  static Class strictClientClass(Class base) {
    // HBaseClient's protected hook swallows scanner IOException by default.
    // Override only for this read-only exporter, leaving the shared client unchanged.
    new GroovyClassLoader(base.classLoader).parseClass("""
      class StrictLSSTAlertExportClient extends ${base.name} {
        StrictLSSTAlertExportClient(String host, int port) { super(host, port) }
        @Override protected void handleScanIOException(java.io.IOException failure) {
          throw new IllegalStateException('HBase alert scan failed; discard export', failure)
        }
      }
    """)
  }

  static void writeAtomic(File target, Closure writeRows) {
    def temporary = java.nio.file.Files.createTempFile(target.absoluteFile.parentFile.toPath(),
      '.lsst-alert-export-', '.jsonl')
    try {
      temporary.toFile().withWriter('UTF-8') { Writer writer -> writeRows(writer) }
      java.nio.file.Files.move(temporary, target.toPath(),
        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
        java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    } finally {
      java.nio.file.Files.deleteIfExists(temporary)
    }
  }

  static void main(String[] argv) {
    Map opts = parseArgs(argv)
    Class base = Class.forName('com.Lomikel.HBaser.HBaseClient')
    def client = strictClientClass(base).getConstructor(String, int.class)
      .newInstance(opts.host.toString(), opts.port as int)
    File file = new File(opts.output.toString())
    writeAtomic(file) { Writer writer -> new ExportAlerts().exportRows(client, opts, writer) }
  }
}

if (!binding.hasVariable('LSST_ALERT_TEST')) ExportAlerts.main(binding.hasVariable('cli') ? ExportAlerts.argvFromBinding(binding) :
  (binding.hasVariable('args') ? (String[])binding.getVariable('args') : new String[0]))
ExportAlerts

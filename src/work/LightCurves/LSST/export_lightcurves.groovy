// Read-only bounded LSST DIA/SS object light curves; no JanusGraph writes.
// Requires a Lomikel HBaseClient built with the protected handleScanIOException hook.
import groovy.json.JsonOutput

class ExportLightcurves {
  static final String COLUMNS = 'r:diaObjectId,r:ssObjectId,r:diaSourceId,r:midpointMjdTai,r:band,r:psfFlux,r:psfFluxErr'
  static final List BANDS = ['u','g','r','i','z','y']
  Closure searchMapFactory = { Map values -> Class.forName('com.Lomikel.DB.SearchMap').getConstructor(Map).newInstance(values) }

  static int positive(def value, String name) {
    if (!(value?.toString() ==~ /[1-9][0-9]*/) || new BigInteger(value.toString()) > Integer.MAX_VALUE)
      throw new IllegalArgumentException("${name} must be a positive integer")
    Integer.parseInt(value.toString())
  }

  static List<String> prefixes(Map opts) {
    if (!(opts.kind in ['dia','ss']) || !opts.table || !opts.schema || !opts.prefixes)
      throw new IllegalArgumentException('Require kind dia|ss, table, schema and explicit object prefixes')
    List<String> result = opts.prefixes.toString().split(',',-1).toList()
    String pattern = opts.kind == 'dia' ? /[0-9]{3}_[0-9]+_/ : /[0-9]{2}_[A-Za-z0-9]+_/
    if (result.any { !(it ==~ pattern) } || result.toSet().size() != result.size())
      throw new IllegalArgumentException('Each prefix must be a distinct full object prefix ending in underscore')
    int objects = positive(opts.'max-objects','max-objects')
    int points = positive(opts.'max-points','max-points')
    if (points > 512 || objects > 100 || result.size() > objects)
      throw new IllegalArgumentException('At most 100 objects and 512 source rows per object per export')
    result
  }

  static Map parseArgs(String[] argv) {
    Map opts = [:]
    Set allowed = ['host','port','table','schema','kind','prefixes','max-objects','max-points','output'] as Set
    for (int i=0; i<argv.length; i+=2) {
      if (i+1 >= argv.length || !argv[i].startsWith('--') || !(argv[i].substring(2) in allowed) ||
          opts.containsKey(argv[i].substring(2)) || !argv[i+1])
        throw new IllegalArgumentException('Invalid option; use --name value')
      opts[argv[i].substring(2)] = argv[i+1]
    }
    prefixes(opts)
    int port = positive(opts.port,'port')
    if (!opts.host || !opts.output || port > 65535) throw new IllegalArgumentException('Require host, port and output')
    opts.port = port
    opts.'max-objects' = positive(opts.'max-objects','max-objects')
    opts.'max-points' = positive(opts.'max-points','max-points')
    opts
  }

  static double numeric(def value, String name) {
    double number
    try { if (value == null || !value.toString().trim()) throw new NumberFormatException(); number = Double.parseDouble(value.toString()) }
    catch (NumberFormatException ex) { throw new IllegalArgumentException("Missing or invalid ${name}") }
    if (!Double.isFinite(number)) throw new IllegalArgumentException("Nonfinite ${name}")
    number
  }

  static Map objectFromRows(Map rows, Map opts, String prefix) {
    if (!rows || rows.size() > positive(opts.'max-points','max-points'))
      throw new IllegalStateException('Empty or capped object scan; completeness unverified')
    String idField = opts.kind == 'dia' ? 'r:diaObjectId' : 'r:ssObjectId'
    String objectId = null
    Set sources = [] as Set
    List values = []
    rows.each { key, cells ->
      if (!(key instanceof String) || !key.startsWith(prefix) || key == prefix || !(cells instanceof Map))
        throw new IllegalStateException('Returned row outside requested object or malformed cells')
      String id = cells[idField]?.toString()
      if (!(id ==~ /[0-9]+/) || (opts.kind == 'dia' && prefix.split('_')[1] != id) ||
          (objectId != null && objectId != id)) throw new IllegalStateException('Object identity mismatch')
      objectId = id
      String source = cells['r:diaSourceId']?.toString()
      if (!(source ==~ /[0-9]+/) || !sources.add(source)) throw new IllegalStateException('Duplicate or invalid source ID')
      String band = cells['r:band']?.toString()
      if (!(band in BANDS)) throw new IllegalArgumentException('Invalid band')
      double mjd = numeric(cells['r:midpointMjdTai'],'mjd')
      double flux = numeric(cells['r:psfFlux'],'flux')
      double err = numeric(cells['r:psfFluxErr'],'flux_err')
      if (err <= 0) throw new IllegalArgumentException('flux_err must be positive')
      values << [row_key:key,source_id:source,mjd:cells['r:midpointMjdTai'].toString(),
        band:band,flux:cells['r:psfFlux'].toString(),flux_err:cells['r:psfFluxErr'].toString()]
    }
    values.sort { a,b -> (Double.parseDouble(a.mjd) <=> Double.parseDouble(b.mjd)) ?: (a.source_id <=> b.source_id) }
    [kind:opts.kind,object_id:objectId,object_key:prefix,table:opts.table,schema:opts.schema,complete:true,sources:values]
  }

  void exportObjects(def client, Map opts, Writer output) {
    List<String> requested = prefixes(opts)
    StringWriter staged = new StringWriter()
    try {
      client.connect(opts.table.toString(),opts.schema.toString())
      if (client.schema() == null) throw new IllegalArgumentException('Requested schema unavailable')
      int cap = positive(opts.'max-points','max-points') + 1
      Set ids = [] as Set
      requested.each { prefix ->
        client.setLimit(cap)
        client.setSearchLimit(cap)
        Map rows = client.scan(null,searchMapFactory(['key:key':prefix]),COLUMNS,0L,0L,false,false)
        Map record = objectFromRows(rows,opts,prefix)
        if (!ids.add(record.object_id)) throw new IllegalStateException('Duplicate canonical object')
        staged.write(JsonOutput.toJson(record) + '\n')
      }
      output.write(staged.toString())
      output.flush()
    } finally { client.close() }
  }

  static Class strictClientClass(Class base) {
    new GroovyClassLoader(base.classLoader).parseClass("""
      class StrictLSSTLightcurveExportClient extends ${base.name} {
        StrictLSSTLightcurveExportClient(String host, int port) { super(host,port) }
        @Override protected void handleScanIOException(java.io.IOException failure) {
          throw new IllegalStateException('HBase lightcurve scan failed; discard export',failure)
        }
      }
    """)
  }

  static void writeAtomic(File target, Closure writer) {
    def temporary = java.nio.file.Files.createTempFile(target.absoluteFile.parentFile.toPath(),'.lsst-lc-export-','.jsonl')
    try {
      temporary.toFile().withWriter('UTF-8') { Writer out -> writer(out) }
      java.nio.file.Files.move(temporary,target.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,
        java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    } finally { java.nio.file.Files.deleteIfExists(temporary) }
  }

  static String[] argvFromBinding(Binding binding) {
    List<String> args = []
    [host:'host',port:'port',table:'table',schema:'schema',kind:'kind',prefixes:'prefixes',
     maxObjects:'max-objects',maxPoints:'max-points',output:'output'].each { name,flag ->
      if (binding.hasVariable(name) && binding.getVariable(name) != null)
        args.addAll(["--${flag}",binding.getVariable(name).toString()])
    }
    args as String[]
  }

  static void main(String[] argv) {
    Map opts = parseArgs(argv)
    Class base = Class.forName('com.Lomikel.HBaser.HBaseClient')
    def client = strictClientClass(base).getConstructor(String,int.class).newInstance(opts.host,opts.port as int)
    writeAtomic(new File(opts.output.toString())) { Writer out -> new ExportLightcurves().exportObjects(client,opts,out) }
  }
}

if (!binding.hasVariable('LSST_LC_TEST')) ExportLightcurves.main(binding.hasVariable('cli') ? ExportLightcurves.argvFromBinding(binding) :
  (binding.hasVariable('args') ? (String[])binding.getVariable('args') : new String[0]))
ExportLightcurves

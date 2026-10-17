// Read-only bounded LSST DIA/SS object light curves; no JanusGraph writes.
// Requires a Lomikel HBaseClient built with the protected handleScanIOException hook.
import groovy.json.JsonOutput

class ExportLightcurves {
  static final String COLUMNS = 'r:diaObjectId,r:ssObjectId,r:diaSourceId,r:midpointMjdTai,r:band,r:psfFlux,r:psfFluxErr'
  static final String ID_COLUMNS = 'r:diaObjectId,r:ssObjectId'
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

  static boolean discovery(Map opts) { opts.containsKey('candidate-prefix') }

  static void validateDiscovery(Map opts) {
    if (!(opts.kind in ['dia','ss']) || !opts.table || !opts.schema || opts.containsKey('prefixes'))
      throw new IllegalArgumentException('Discovery requires kind, table, schema and no explicit prefixes')
    String candidate = opts.'candidate-prefix'?.toString()
    if (!(candidate ==~ /[0-9]{2}/))
      throw new IllegalArgumentException('candidate-prefix must be a two-digit broad row-key prefix')
    int rows = positive(opts.'candidate-rows','candidate-rows')
    int minimum = positive(opts.'min-points','min-points')
    int objects = positive(opts.'max-objects','max-objects')
    int points = positive(opts.'max-points','max-points')
    if (rows > 5000 || objects > 100 || points > 512 || minimum < 3 || minimum > points)
      throw new IllegalArgumentException('Discovery bounds: candidate-rows <= 5000, max-objects <= 100, 3 <= min-points <= max-points <= 512')
  }

  static Map parseArgs(String[] argv) {
    Map opts = [:]
    Set allowed = ['host','port','table','schema','kind','prefixes','candidate-prefix','candidate-rows','min-points','max-objects','max-points','output'] as Set
    for (int i=0; i<argv.length; i+=2) {
      if (i+1 >= argv.length || !argv[i].startsWith('--') || !(argv[i].substring(2) in allowed) ||
          opts.containsKey(argv[i].substring(2)) || !argv[i+1])
        throw new IllegalArgumentException('Invalid option; use --name value')
      opts[argv[i].substring(2)] = argv[i+1]
    }
    if (discovery(opts)) validateDiscovery(opts)
    else {
      if (opts.containsKey('candidate-rows') || opts.containsKey('min-points'))
        throw new IllegalArgumentException('Discovery options require candidate-prefix')
      prefixes(opts)
    }
    int port = positive(opts.port,'port')
    if (!opts.host || !opts.output || port > 65535) throw new IllegalArgumentException('Require host, port and output')
    opts.port = port
    opts.'max-objects' = positive(opts.'max-objects','max-objects')
    opts.'max-points' = positive(opts.'max-points','max-points')
    if (discovery(opts)) {
      opts.'candidate-rows' = positive(opts.'candidate-rows','candidate-rows')
      opts.'min-points' = positive(opts.'min-points','min-points')
    }
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

  List<Map> discover(def client, Map opts, Map stats = [:]) {
    int rowLimit = positive(opts.'candidate-rows','candidate-rows')
    client.setLimit(rowLimit + 1)
    client.setSearchLimit(rowLimit + 1)
    Map sampled = client.scan(null,searchMapFactory(['key:key':opts.'candidate-prefix']),ID_COLUMNS,0L,0L,false,false)
    if (sampled == null || sampled.size() > rowLimit + 1)
      throw new IllegalStateException('Candidate scan returned invalid or over-limit result')
    String field = opts.kind == 'dia' ? 'r:diaObjectId' : 'r:ssObjectId'
    String pattern = opts.kind == 'dia' ? /([0-9]{3}_[0-9]+_).+/ : /([0-9]{2}_[A-Za-z0-9]+_).+/
    Map<String,String> candidates = [:]
    Map skipped = [malformed:0,duplicate:0,invalid:0,capped:0,short:0]
    sampled.entrySet().take(rowLimit).each { entry ->
      def matcher = (entry.key instanceof String && entry.key.startsWith(opts.'candidate-prefix')) ?
        (entry.key =~ pattern) : null
      String id = entry.value instanceof Map ? entry.value[field]?.toString() : null
      if (matcher == null || !matcher.matches() || !(id ==~ /[0-9]+/) ||
          (opts.kind == 'dia' && matcher.group(1).split('_')[1] != id)) {
        skipped.malformed++
      } else {
        String prefix = matcher.group(1)
        if (!candidates.containsKey(prefix) && candidates.size() < 100) candidates[prefix] = id
      }
    }
    List<Map> valid = []
    int cap = positive(opts.'max-points','max-points') + 1
    candidates.each { prefix, id ->
      client.setLimit(cap)
      client.setSearchLimit(cap)
      // Scanner errors must propagate: only validation failures are skippable.
      Map rows = client.scan(null,searchMapFactory(['key:key':prefix]),COLUMNS,0L,0L,false,false)
      try {
        if (rows != null && rows.size() > positive(opts.'max-points','max-points')) {
          skipped.capped++
        } else {
          Map record = objectFromRows(rows,opts,prefix)
          if (record.object_id != id) throw new IllegalStateException('Candidate identity changed')
          double span = Double.parseDouble(record.sources.last().mjd) - Double.parseDouble(record.sources.first().mjd)
          if (!Double.isFinite(span) || span <= 0d) throw new IllegalStateException('Object has no finite time span')
          if (record.sources.size() < positive(opts.'min-points','min-points')) skipped.short++
          else valid << record
        }
      } catch (IllegalArgumentException | IllegalStateException ex) {
        skipped.invalid++
      }
    }
    valid.sort { a,b -> (b.sources.size() <=> a.sources.size()) ?: (a.object_id <=> b.object_id) ?: (a.object_key <=> b.object_key) }
    Set ids = [] as Set
    List<Map> distinct = []
    valid.each { record ->
      if (ids.add(record.object_id)) distinct << record
      else skipped.duplicate++
    }
    System.err.println("Discovery: sampled=${sampled.size()} row(s), prefixes=${candidates.size()}, valid=${distinct.size()}, skipped=${skipped}, candidateCap=${candidates.size() == 100}, rowCap=${sampled.size() > rowLimit}")
    int wanted = positive(opts.'max-objects','max-objects')
    if (distinct.size() < wanted) throw new IllegalStateException("Discovery found ${distinct.size()} complete distinct objects; requested ${wanted}; no output published")
    stats.putAll([sampledRows:sampled.size(),candidateRows:rowLimit,
      candidatePrefixes:candidates.size(),rowCap:sampled.size() > rowLimit])
    distinct.take(wanted)
  }

  Map exportObjects(def client, Map opts, Writer output) {
    boolean automatic = discovery(opts)
    if (automatic) validateDiscovery(opts)
    List<String> requested = automatic ? [] : prefixes(opts)
    StringWriter staged = new StringWriter()
    try {
      client.connect(opts.table.toString(),opts.schema.toString())
      if (client.schema() == null) throw new IllegalArgumentException('Requested schema unavailable')
      int cap = positive(opts.'max-points','max-points') + 1
      Set ids = [] as Set
      int objects = 0, points = 0
      Map discoveryStats = [:]
      if (automatic) discover(client,opts,discoveryStats).each { record ->
        staged.write(JsonOutput.toJson(record) + '\n')
        objects++
        points += record.sources.size()
      }
      else requested.each { prefix ->
        client.setLimit(cap)
        client.setSearchLimit(cap)
        Map rows = client.scan(null,searchMapFactory(['key:key':prefix]),COLUMNS,0L,0L,false,false)
        Map record = objectFromRows(rows,opts,prefix)
        if (!ids.add(record.object_id)) throw new IllegalStateException('Duplicate canonical object')
        staged.write(JsonOutput.toJson(record) + '\n')
        objects++
        points += record.sources.size()
      }
      output.write(staged.toString())
      output.flush()
      return [objects:objects,points:points] + discoveryStats
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
     candidatePrefix:'candidate-prefix',candidateRows:'candidate-rows',minPoints:'min-points',
     maxObjects:'max-objects',maxPoints:'max-points',output:'output'].each { name,flag ->
      if (binding.hasVariable(name) && binding.getVariable(name) != null)
        args.addAll(["--${flag}",binding.getVariable(name).toString()])
    }
    args as String[]
  }

  static void runExport(def client, Map opts, ExportLightcurves exporter) {
    Map stats
    writeAtomic(new File(opts.output.toString())) { Writer out -> stats = exporter.exportObjects(client,opts,out) }
    String sample = discovery(opts) ? " sampled_rows=${stats.sampledRows} candidate_rows=${stats.candidateRows} candidate_prefixes=${stats.candidatePrefixes} row_cap=${stats.rowCap}" : ''
    println "Exported kind=${opts.kind} lc_objects=${stats.objects} total_points=${stats.points} output=${opts.output} mode=${discovery(opts) ? 'discovery' : 'explicit'}${sample}"
  }

  static void main(String[] argv) {
    Map opts = parseArgs(argv)
    Class base = Class.forName('com.Lomikel.HBaser.HBaseClient')
    def client = strictClientClass(base).getConstructor(String,int.class).newInstance(opts.host,opts.port as int)
    runExport(client,opts,new ExportLightcurves())
  }
}

if (!binding.hasVariable('LSST_LC_TEST')) ExportLightcurves.main(binding.hasVariable('cli') ? ExportLightcurves.argvFromBinding(binding) :
  (binding.hasVariable('args') ? (String[])binding.getVariable('args') : new String[0]))
ExportLightcurves

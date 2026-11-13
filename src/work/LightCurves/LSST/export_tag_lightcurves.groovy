// Read-only export of tagged LSST DIA object light curves for the light-curve
// tag evaluation (evaluate_tags.py beside this script).
//
// For every class (rubin.tag_* table) in the JSON config it lists all row keys
// (<midpointMjdTai>_<diaObjectId>) and samples OBJECTS deterministically
// (sha256(seed:objectId) order, at most sampling.max_objects_per_class per class).
// For each sampled object it reads the whole object prefix
// <diaObjectId mod 1000>_<diaObjectId>_ of rubin.diaSource_static (at most
// max_points+1 rows; a curve longer than max_points, including tag-row
// sources, is excluded and counted, never truncated).
// Tagged alerts of the object without a static row are read from the tag row
// itself (sampling.fallback_to_tag_row; tag rows are byte copies of the alert
// row) with the same identity checks and deduplicated by diaSourceId.
//
// Outputs (written to a fresh directory, published atomically):
//   curves.jsonl  one record per object in the train_lightcurves.py contract
//                 {kind,object_id,object_key,table,schema,complete,sources}; no labels
//   labels.jsonl  {object_id,classes,tags{class:{alerts,first_mjd,last_mjd}},
//                  first_tag_mjd,sampled_for,sources,tag_row_sources,invalid_sources}
//   summary.json  per-class statistics, exclusions, provenance
// An object's classes are ALL configured tags carried by any of its alerts.
// Rows with missing/non-finite flux, non-positive error or unknown band are
// dropped and counted per object. Objects with fewer than min_points valid
// observations or no time span are excluded and counted. The export fails when
// more than sampling.max_missing_fraction of a class's tagged alerts (of its
// sampled objects) can be found neither in the static table nor in a tag row.
// Tables are read one after another (not one consistent HBase snapshot).
//
// Lomikel GCLI (from ant/ after `source setup.sh`):
//   java -jar ../lib/Lomikel-HBase-${version}.exe.jar -b -q \
//     -s ../src/work/LightCurves/LSST/export_tag_lightcurves.groovy \
//     -o "config='../src/work/LightCurves/LSST/tag_classes.json',output='../run/lsst-lc-tag-eval/export'"
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.ConnectionFactory
import org.apache.hadoop.hbase.client.Get
import org.apache.hadoop.hbase.client.Scan
import org.apache.hadoop.hbase.filter.FilterList
import org.apache.hadoop.hbase.filter.FirstKeyOnlyFilter
import org.apache.hadoop.hbase.filter.KeyOnlyFilter
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class ExportTagLightcurves {
  static final String FORMAT = 'lomikel-lsst-lc-tag-eval-config-v1'
  static final List<String> COLUMNS = ['diaObjectId','diaSourceId','midpointMjdTai','band','psfFlux','psfFluxErr']
  static final List<String> BANDS = ['u','g','r','i','z','y']
  static final String TAG_KEY = /([0-9]+(?:\.[0-9]+)?)_([0-9]+)/

  static Map loadConfig(String path) {
    def cfg = new JsonSlurper().parse(new File(path))
    if (!(cfg instanceof Map) || cfg.format != FORMAT)
      throw new IllegalArgumentException('Unsupported config format')
    List classes = cfg.classes
    if (!(classes instanceof List) || classes.isEmpty() || classes.toSet().size() != classes.size() ||
        classes.any { !(it instanceof String) || !(it ==~ /rubin\.tag_[A-Za-z0-9_]+/) })
      throw new IllegalArgumentException('classes must be unique rubin.tag_* table names')
    Map s = cfg.sampling
    ['max_objects_per_class','max_scan_keys_per_class','max_points','min_points','threads'].each {
      if (!(s?.get(it) instanceof Integer) || s[it] < 1) throw new IllegalArgumentException("sampling.${it} must be a positive integer")
    }
    if (!(s.seed instanceof Integer)) throw new IllegalArgumentException('sampling.seed must be an integer')
    if (s.max_points > 512 || s.min_points < 3 || s.min_points > s.max_points)
      throw new IllegalArgumentException('sampling: 3 <= min_points <= max_points <= 512 (train_lightcurves.py contract)')
    if (s.threads > 64) throw new IllegalArgumentException('sampling.threads must be <= 64')
    if (s.fallback_to_tag_row != null && !(s.fallback_to_tag_row instanceof Boolean))
      throw new IllegalArgumentException('sampling.fallback_to_tag_row must be true or false')
    def mmf = s.max_missing_fraction == null ? 0.05 : s.max_missing_fraction
    if (!(mmf instanceof Number) || mmf < 0 || mmf >= 1)
      throw new IllegalArgumentException('sampling.max_missing_fraction must be in [0, 1)')
    if (!(cfg.hbase?.host instanceof String) || !(cfg.hbase?.port instanceof Integer) ||
        cfg.hbase.alert_table != 'rubin.diaSource_static')
      throw new IllegalArgumentException('hbase.host, hbase.port and alert_table rubin.diaSource_static required')
    cfg
  }

  static String sha(String text) {
    MessageDigest.getInstance('SHA-256').digest(text.getBytes('UTF-8')).encodeHex().toString()
  }

  /** Static object prefix <oid mod 1000>_<oid>_ (DIA only). */
  static String objectPrefix(String objectId) {
    String.format('%03d', new BigInteger(objectId).mod(1000G).intValue()) + '_' + objectId + '_'
  }

  /** Static alert key for a tag key <mjd>_<oid>. */
  static String staticKey(String tagKey) {
    def m = tagKey =~ TAG_KEY
    if (!m.matches()) throw new IllegalArgumentException("Bad tag key ${tagKey}")
    objectPrefix(m.group(2)) + m.group(1)
  }

  /** Deterministic object sample: objects in sha256(seed:oid) order, at most perClass. */
  static List<String> sampleObjects(Collection<String> objects, int seed, int perClass) {
    objects.sort(false) { sha("${seed}:${it}") }.take(perClass)
  }

  static Object decode(String type, byte[] value) {
    if (value == null) return null
    switch (type) {
      case 'float':   return value.length == 4 ? Float.intBitsToFloat(new BigInteger(1, value).intValue()) as double : null
      case 'double':  return value.length == 8 ? Double.longBitsToDouble(new BigInteger(1, value).longValue()) : null
      case 'long':    return value.length == 8 ? new BigInteger(value).longValue() : null
      case 'integer': return value.length == 4 ? new BigInteger(value).intValue() : null
      case 'string':  return new String(value, 'UTF-8')
      default: return null
    }
  }

  /** Decoded row -> source map, or null when a measurement is unusable. Identity must match. */
  static Map source(String key, Map row, String objectId) {
    String oid = row.diaObjectId?.toString()
    String sid = row.diaSourceId?.toString()
    if (oid != objectId || !(sid ==~ /[0-9]+/) || !(row.midpointMjdTai instanceof Double) ||
        !Double.isFinite(row.midpointMjdTai as double))
      throw new IllegalStateException("Identity of ${key} disagrees with object ${objectId}")
    def flux = row.psfFlux, err = row.psfFluxErr
    if (!(row.band in BANDS) || !(flux instanceof Number) || !(err instanceof Number) ||
        !Double.isFinite(flux as double) || !Double.isFinite(err as double) || (err as double) <= 0d) return null
    [row_key: key, source_id: sid, mjd: row.midpointMjdTai.toString(), band: row.band,
     flux: (flux as double).toString(), flux_err: (err as double).toString()]
  }

  /**
   * Assemble one object curve.
   * staticRows: static key -> decoded row (the prefix scan); tagged: the object's tag keys
   * -> tables holding them; tagRows: tag key -> decoded row read from a tag table (fallback).
   * Returns [record (or null), status, counts].
   */
  static Map buildCurve(String objectId, Map<String, Map> staticRows, Map<String, List<String>> tagged,
                        Map<String, Map> tagRows, Map opts) {
    String prefix = objectPrefix(objectId)
    Map<String, Map> sources = [:]   // source id -> source
    int invalid = 0, fromTag = 0
    List<String> missing = []
    staticRows.each { key, row ->
      if (!key.startsWith(prefix) || key == prefix) throw new IllegalStateException("Row ${key} outside ${prefix}")
      Map s = source(key, row, objectId)
      if (s == null) { invalid++; return }
      if (sources.containsKey(s.source_id)) throw new IllegalStateException("Duplicate source ${s.source_id} in ${prefix}")
      sources[s.source_id] = s
    }
    tagged.keySet().sort().each { tk ->
      String sk = staticKey(tk)
      if (staticRows.containsKey(sk)) return
      Map row = tagRows[tk]
      if (row == null) { missing << tk; return }
      Map s = source(sk, row, objectId)
      if (s == null) { invalid++; return }
      double tagMjd = (tk =~ TAG_KEY)[0][1] as double
      if (Math.abs((s.mjd as double) - tagMjd) > 1e-6) throw new IllegalStateException("Tag row ${tk} MJD mismatch")
      if (!sources.containsKey(s.source_id)) { sources[s.source_id] = s; fromTag++ }
    }
    List<Map> list = sources.values().sort { a, b -> ((a.mjd as double) <=> (b.mjd as double)) ?: (a.source_id <=> b.source_id) }
    Map counts = [sources: list.size(), invalid_sources: invalid, tag_row_sources: fromTag, missing_tagged: missing]
    String status = 'ok'
    if (opts.max_points && list.size() > opts.max_points) status = 'capped'   // tag-row sources count too
    else if (list.size() < opts.min_points) status = 'short'
    else if (!((list.last().mjd as double) > (list.first().mjd as double))) status = 'no_span'
    Map record = status != 'ok' ? null : [kind: 'dia', object_id: objectId, object_key: prefix,
      table: opts.table, schema: opts.schema, complete: true, sources: list]
    [record: record, status: status, counts: counts]
  }

  // ---- HBase access (native client; IOExceptions propagate) -------------
  def conn
  ExportTagLightcurves(def connection) { conn = connection }

  def table(String name) { conn.getTable(TableName.valueOf(name)) }

  boolean exists(String name) {
    def admin = conn.getAdmin()
    try { admin.tableExists(TableName.valueOf(name)) } finally { admin.close() }
  }

  /** column -> type from every schema row; conflicting types fail. */
  Map<String, String> schemaTypes(String name, List<String> schemaNames) {
    Map<String, String> types = [:]
    def t = table(name)
    try {
      Scan scan = new Scan()
      scan.setRowPrefixFilter('schema'.getBytes('UTF-8'))
      def scanner = t.getScanner(scan)
      try {
        for (def r : scanner) {
          schemaNames << new String(r.getRow(), 'UTF-8')
          r.rawCells().each { cell ->
            String col = new String(cell.getFamilyArray(), cell.getFamilyOffset(), cell.getFamilyLength(), 'UTF-8') + ':' +
                         new String(cell.getQualifierArray(), cell.getQualifierOffset(), cell.getQualifierLength(), 'UTF-8')
            String type = new String(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength(), 'UTF-8')
            if (types.containsKey(col) && types[col] != type)
              throw new IllegalStateException("Conflicting schema types for ${col} in ${name}")
            types[col] = type
          }
        }
      } finally { scanner.close() }
    } finally { t.close() }
    if (!schemaNames) throw new IllegalStateException("No schema rows in ${name}")
    types
  }

  /** All data row keys of a tag table, grouped by object; fails above the cap. */
  Map scanKeys(String name, int cap) {
    Map<String, List<String>> byObject = [:]
    int n = 0
    double minMjd = Double.MAX_VALUE, maxMjd = 0d
    def t = table(name)
    try {
      Scan scan = new Scan()
      scan.setFilter(new FilterList(new FirstKeyOnlyFilter(), new KeyOnlyFilter()))
      scan.setCaching(5000)
      def scanner = t.getScanner(scan)
      try {
        for (def r : scanner) {
          String key = new String(r.getRow(), 'UTF-8')
          if (key.startsWith('schema')) continue
          def m = key =~ TAG_KEY
          if (!m.matches()) throw new IllegalStateException("Unexpected tag key ${key} in ${name}")
          if (++n > cap) throw new IllegalStateException("${name} exceeds max_scan_keys_per_class=${cap}")
          byObject.computeIfAbsent(m.group(2)) { [] } << key
          double mjd = m.group(1) as double
          minMjd = Math.min(minMjd, mjd); maxMjd = Math.max(maxMjd, mjd)
        }
      } finally { scanner.close() }
    } finally { t.close() }
    [byObject: byObject, alerts: n, minMjd: n ? minMjd : null, maxMjd: n ? maxMjd : null]
  }

  Map decodeRow(def r, Map<String, String> types) {
    byte[] family = 'r'.getBytes('UTF-8')
    COLUMNS.collectEntries { c -> [(c): decode(types["r:${c}"], r.getValue(family, c.getBytes('UTF-8')))] }
  }

  /** Object prefix scan, at most cap rows (cap = max_points+1 detects truncation). */
  Map<String, Map> scanObject(def t, String prefix, int cap, Map<String, String> types) {
    Map<String, Map> rows = [:]
    Scan scan = new Scan()
    scan.setRowPrefixFilter(prefix.getBytes('UTF-8'))
    COLUMNS.each { c -> scan.addColumn('r'.getBytes('UTF-8'), c.getBytes('UTF-8')) }
    scan.setCaching(cap)
    scan.setLimit(cap)
    def scanner = t.getScanner(scan)
    try {
      for (def r : scanner) {
        rows[new String(r.getRow(), 'UTF-8')] = decodeRow(r, types)
        if (rows.size() >= cap) break
      }
    } finally { scanner.close() }
    rows
  }

  /** Exact Gets of tag rows (fallback); tag key -> decoded row. */
  Map<String, Map> fetchTagRows(String name, List<String> keys, Map<String, String> types) {
    Map<String, Map> result = [:]
    if (!keys) return result
    def t = table(name)
    try {
      keys.collate(500).each { chunk ->
        List gets = chunk.collect { k ->
          Get g = new Get(k.getBytes('UTF-8'))
          COLUMNS.each { c -> g.addColumn('r'.getBytes('UTF-8'), c.getBytes('UTF-8')) }
          g
        }
        def rows = t.get((List<Get>)gets)
        if (rows.length != chunk.size()) throw new IllegalStateException('HBase batch size mismatch')
        chunk.eachWithIndex { k, i ->
          def r = rows[i]
          if (r == null || r.isEmpty()) return
          if (new String(r.getRow(), 'UTF-8') != k) throw new IllegalStateException("Mismatched row for ${k}")
          result[k] = decodeRow(r, types)
        }
      }
    } finally { t.close() }
    result
  }

  // ---- export ------------------------------------------------------------
  Map export(Map cfg, File outDir) {
    Map s = cfg.sampling
    String alertTable = cfg.hbase.alert_table
    int maxPoints = s.max_points
    boolean fallback = s.fallback_to_tag_row == null ? true : s.fallback_to_tag_row
    Map perClass = [:]
    Map<String, Map<String, List<String>>> keysByClass = [:]   // class -> oid -> tag keys
    Map<String, List<String>> sampledFor = [:]                 // oid -> classes that sampled it
    cfg.classes.each { String cls ->
      if (!exists(cls)) { perClass[cls] = [status: 'missing table']; return }
      Map scanned = scanKeys(cls, s.max_scan_keys_per_class)
      keysByClass[cls] = scanned.byObject
      List<String> chosen = sampleObjects(scanned.byObject.keySet(), s.seed, s.max_objects_per_class)
      chosen.each { sampledFor.computeIfAbsent(it) { [] } << cls }
      perClass[cls] = [status: scanned.alerts ? 'ok' : 'empty table', table_alerts: scanned.alerts,
        table_objects: scanned.byObject.size(), min_mjd: scanned.minMjd, max_mjd: scanned.maxMjd,
        sampled_objects: chosen.size()]
      System.err.println("${cls}: ${perClass[cls].status} alerts=${scanned.alerts} objects=${scanned.byObject.size()} sampled=${chosen.size()}")
    }
    List<String> schemaNames = []
    Map<String, String> types = schemaTypes(alertTable, schemaNames)
    if (COLUMNS.any { !types["r:${it}"] }) throw new IllegalStateException('Alert table lacks light-curve columns')
    Map opts = [table: alertTable, schema: schemaNames.sort().join(','), min_points: s.min_points, max_points: maxPoints]
    List<String> objects = sampledFor.keySet().sort()
    // Tagged keys of every sampled object across ALL configured classes.
    Map<String, Map<String, List<String>>> taggedOf = [:]
    objects.each { oid ->
      Map<String, List<String>> tk = [:]
      keysByClass.each { cls, byObj -> byObj[oid]?.each { k -> tk.computeIfAbsent(k) { [] } << cls } }
      taggedOf[oid] = tk
    }
    // Object prefix scans in parallel; results collected by object.
    def pool = Executors.newFixedThreadPool(s.threads as int)
    Map<String, Map<String, Map>> scans = new java.util.concurrent.ConcurrentHashMap<>()
    try {
      List<Callable> tasks = objects.collate(Math.max(1, (int)Math.ceil(objects.size() / (s.threads * 4.0)))).collect { chunk ->
        ({ ->
          def t = table(alertTable)
          try { chunk.each { oid -> scans[oid] = scanObject(t, objectPrefix(oid), maxPoints + 1, types) } }
          finally { t.close() }
          null
        } as Callable)
      }
      pool.invokeAll(tasks).each { it.get() }   // propagate scan failures
    } finally { pool.shutdownNow() }
    // Fallback: tagged alerts without a static row, read from a tag table holding them.
    Map<String, Map> tagRows = [:]
    if (fallback) {
      Map<String, List<String>> byTable = [:]
      objects.each { oid ->
        if (scans[oid].size() > maxPoints) return
        taggedOf[oid].each { tk, classes -> if (!scans[oid].containsKey(staticKey(tk))) byTable.computeIfAbsent(classes[0]) { [] } << tk }
      }
      byTable.each { cls, keys -> tagRows.putAll(fetchTagRows(cls, keys, types)) }
    }
    Map<String, Integer> excluded = [capped: 0, short: 0, no_span: 0]
    Map<String, Map<String, Integer>> byClass = cfg.classes.collectEntries { [(it): [exported: 0, capped: 0, short: 0, no_span: 0,
      tagged_alerts: 0, missing_tagged: 0, tag_row_sources: 0]] }
    int written = 0, points = 0, invalidTotal = 0, fromTagTotal = 0
    List<String> missingExamples = []
    Map<String, Integer> multi = [:].withDefault { 0 }
    File curves = new File(outDir, 'curves.jsonl'), labels = new File(outDir, 'labels.jsonl')
    curves.withWriter('UTF-8') { cw -> labels.withWriter('UTF-8') { lw ->
      objects.each { oid ->
        List<String> member = cfg.classes.findAll { keysByClass[it]?.containsKey(oid) }
        List<String> sampledBy = sampledFor[oid]
        Map<String, Map> rows = scans[oid]
        String status
        Map built = null
        if (rows.size() > maxPoints) status = 'capped'
        else {
          built = buildCurve(oid, rows, taggedOf[oid], tagRows, opts)
          status = built.status
        }
        sampledBy.each { cls ->
          Map bc = byClass[cls]
          bc[status == 'ok' ? 'exported' : status]++
          if (built) bc.tag_row_sources += built.counts.tag_row_sources
        }
        // Completeness of tagged alerts: every class tagging an assembled object,
        // whether or not that class sampled it (objects capped at the scan are not assembled).
        if (built) member.each { cls ->
          Map bc = byClass[cls]
          bc.tagged_alerts += keysByClass[cls][oid].size()
          bc.missing_tagged += built.counts.missing_tagged.count { tk -> taggedOf[oid][tk].contains(cls) }
        }
        if (built) built.counts.missing_tagged.each { if (missingExamples.size() < 20) missingExamples << it }
        if (status != 'ok') { excluded[status]++; return }
        Map tags = member.collectEntries { cls ->
          List<Double> mjds = keysByClass[cls][oid].collect { (it =~ TAG_KEY)[0][1] as double }
          [(cls): [alerts: mjds.size(), first_mjd: mjds.min(), last_mjd: mjds.max()]]
        }
        cw.write(JsonOutput.toJson(built.record) + '\n')
        lw.write(JsonOutput.toJson([object_id: oid, classes: member, tags: tags,
          first_tag_mjd: tags.values()*.first_mjd.min(), sampled_for: sampledBy,
          sources: built.counts.sources, tag_row_sources: built.counts.tag_row_sources,
          invalid_sources: built.counts.invalid_sources, missing_tagged: built.counts.missing_tagged.size()]) + '\n')
        multi[member.size().toString()]++
        written++
        points += built.counts.sources
        invalidTotal += built.counts.invalid_sources
        fromTagTotal += built.counts.tag_row_sources
      }
    } }
    if (!written) throw new IllegalStateException('No tagged light curves exported')
    double maxMissing = (s.max_missing_fraction == null ? 0.05 : s.max_missing_fraction) as double
    List<String> lossy = []
    byClass.each { cls, bc ->
      if (perClass[cls] instanceof Map && perClass[cls].status == 'ok') perClass[cls] += bc
      if (bc.tagged_alerts && bc.missing_tagged / (double)bc.tagged_alerts > maxMissing)
        lossy << "${cls} ${bc.missing_tagged}/${bc.tagged_alerts}".toString()
    }
    if (lossy) throw new IllegalStateException("Tagged alerts missing from static and tag rows above max_missing_fraction=${maxMissing}: ${lossy.join(', ')}")
    Map summary = [format: 'lomikel-lsst-tag-lightcurves-v1', alert_table: alertTable,
      alert_schemas: schemaNames.sort(), columns: COLUMNS.collect { "r:${it}" }, classes: perClass,
      sampled_objects: objects.size(), exported_objects: written, exported_sources: points,
      excluded_objects: excluded, invalid_sources: invalidTotal, tag_row_sources: fromTagTotal,
      missing_tagged_examples: missingExamples, fallback_to_tag_row: fallback,
      objects_by_label_count: multi, sampling: s, config_sha256: sha(JsonOutput.toJson(cfg)),
      created: java.time.OffsetDateTime.now().withNano(0).toString(),
      note: 'Labels are Fink rubin.tag_* filter memberships of individual alerts, propagated to the object (any alert tagged), not independent physical truth.']
    new File(outDir, 'summary.json').text = JsonOutput.prettyPrint(JsonOutput.toJson(summary)) + '\n'
    summary
  }

  static void run(String configPath, String output) {
    Map cfg = loadConfig(configPath)
    File target = new File(output)
    if (target.exists()) throw new IllegalArgumentException("Output ${output} already exists; choose a fresh directory")
    target.absoluteFile.parentFile.mkdirs()
    File stage = Files.createTempDirectory(target.absoluteFile.parentFile.toPath(), '.lsst-lc-tag-export-').toFile()
    def conf = HBaseConfiguration.create()
    conf.set('hbase.zookeeper.quorum', cfg.hbase.host)
    conf.set('hbase.zookeeper.property.clientPort', cfg.hbase.port.toString())
    def conn = ConnectionFactory.createConnection(conf)
    try {
      Map summary = new ExportTagLightcurves(conn).export(cfg, stage)
      Files.move(stage.toPath(), target.toPath())
      summary.classes.each { cls, st -> println "${cls}: ${st.status} table_objects=${st.table_objects ?: 0} sampled=${st.sampled_objects ?: 0} exported=${st.exported ?: 0} capped=${st.capped ?: 0} short=${st.short ?: 0}" }
      println "Exported ${summary.exported_objects} light curves (${summary.exported_sources} sources, ${summary.tag_row_sources} from tag rows; excluded ${summary.excluded_objects}) to ${output}"
    } finally {
      conn.close()
      if (stage.exists()) stage.deleteDir()
    }
  }
}

if (!binding.hasVariable('LSST_LC_TAG_EXPORT_TEST')) {
  String configPath = binding.hasVariable('config') ? binding.getVariable('config') : null
  String output = binding.hasVariable('output') ? binding.getVariable('output') : null
  if (!configPath || !output) throw new IllegalArgumentException("Use -o \"config='tag_classes.json',output='DIR'\"")
  ExportTagLightcurves.run(configPath, output)
}
ExportTagLightcurves

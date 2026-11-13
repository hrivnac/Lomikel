// Read-only export of labeled LSST alerts for the PCA+K-means tag evaluation.
//
// For every class (rubin.tag_* table) in the JSON config it lists all row keys
// (<midpointMjdTai>_<diaObjectId>), samples objects deterministically
// (sha256(seed:objectId) order, at most max_alerts_per_object alerts per object
// and max_alerts_per_class per class), then reads the sampled alerts' measured
// columns from the canonical alert table rubin.diaSource_static
// (<diaObjectId mod 1000>_<diaObjectId>_<midpointMjdTai>; tag rows are byte copies).
// Each exported alert carries ALL configured classes whose table holds its key,
// so multi-labels are exact, not only the class it was sampled for.
//
// Outputs (written to a fresh directory, published atomically):
//   alerts.jsonl  {kind,row_key,table,cells}: allowlisted measurements + r:band only
//   labels.jsonl  {row_key,tag_key,object_id,source_id,mjd,band,classes,sampled_for}
//   summary.json  per-class table statistics, sampling and provenance
// Labels and identities never enter alerts.jsonl.
// The per-object cap applies within each class; an object sampled by several
// classes can contribute up to max_alerts_per_object alerts per class.
// Sampled tag rows without a canonical alert row are read from the tag row
// itself (sampling.fallback_to_tag_row, default true; tag rows are byte copies)
// with the same identity checks; the rest are counted per class and the export
// fails when a class loses more than sampling.max_missing_fraction.
// Tables are scanned one after another (not one consistent HBase snapshot).
//
// Lomikel GCLI (from ant/ after `source setup.sh`):
//   java -jar ../lib/Lomikel-HBase-${version}.exe.jar -b -q \
//     -s ../src/work/Clusters/LSST/export_tag_alerts.groovy \
//     -o "config='../src/work/Clusters/LSST/tag_classes.json',output='../run/lsst-tag-eval/export'"
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

class ExportTagAlerts {
  // Superset of measured alert-level columns that may be exported; the
  // evaluator picks its features from these. Keep aligned with
  // train_clusters.MEASUREMENTS and classify_alert.groovy. Never add IDs,
  // coordinates, times, visit/detector/pixel positions, f: classifier or
  // crossmatch columns.
  static final List<String> MEASUREMENTS = ['psfFlux','psfFluxErr','psfChi2','psfLnL','psfNdata',
    'apFlux','apFluxErr','scienceFlux','scienceFluxErr','templateFlux','templateFluxErr',
    'snr','extendedness','reliability','ixx','ixy','iyy','ixxPSF','ixyPSF','iyyPSF','bboxSize',
    'dipoleMeanFlux','dipoleMeanFluxErr','dipoleFluxDiff','dipoleFluxDiffErr','dipoleLength',
    'dipoleAngle','dipoleChi2','dipoleNdata','trailFlux','trailFluxErr','trailLength',
    'trailLengthErr','trailAngle','trailAngleErr','trailChi2','trailNdata']
  static final List<String> IDENTITY = ['diaObjectId','diaSourceId','midpointMjdTai','band']
  static final String TAG_KEY = /([0-9]+(?:\.[0-9]+)?)_([0-9]+)/

  static Map loadConfig(String path) {
    def cfg = new JsonSlurper().parse(new File(path))
    if (!(cfg instanceof Map) || cfg.format != 'lomikel-lsst-tag-eval-config-v1')
      throw new IllegalArgumentException('Unsupported config format')
    List classes = cfg.classes
    if (!(classes instanceof List) || classes.isEmpty() || classes.toSet().size() != classes.size() ||
        classes.any { !(it instanceof String) || !(it ==~ /rubin\.tag_[A-Za-z0-9_]+/) })
      throw new IllegalArgumentException('classes must be unique rubin.tag_* table names')
    Map s = cfg.sampling
    ['max_alerts_per_class','max_alerts_per_object','max_scan_keys_per_class','batch_size'].each {
      if (!(s?.get(it) instanceof Integer) || s[it] < 1) throw new IllegalArgumentException("sampling.${it} must be a positive integer")
    }
    if (s.batch_size > 5000) throw new IllegalArgumentException('sampling.batch_size must be <= 5000')
    if (!(s.seed instanceof Integer)) throw new IllegalArgumentException('sampling.seed must be an integer')
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

  /** Static alert-table key for a tag key <mjd>_<oid>. */
  static String staticKey(String objectId, String mjd) {
    String.format('%03d', new BigInteger(objectId).mod(1000G).intValue()) + '_' + objectId + '_' + mjd
  }

  /** Deterministic object-first sample: objects in hash order, <= perObject alerts each. */
  static List<String> sample(Map<String, List<String>> keysByObject, int seed, int perClass, int perObject) {
    List<String> chosen = []
    List<String> objects = keysByObject.keySet().sort { sha("${seed}:${it}") }
    for (String oid : objects) {
      if (chosen.size() >= perClass) break
      List<String> keys = keysByObject[oid].sort { sha("${seed}:${it}") }
      int take = Math.min(perObject, perClass - chosen.size())
      chosen.addAll(keys.take(take))
    }
    chosen
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

  // ---- HBase access (native client; IOExceptions propagate) -------------
  def conn
  ExportTagAlerts(def connection) { conn = connection }

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
    Set<String> keys = new HashSet<>()
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
          if (!keys.add(key)) throw new IllegalStateException("Duplicate key ${key}")
          if (keys.size() > cap) throw new IllegalStateException("${name} exceeds max_scan_keys_per_class=${cap}")
          byObject.computeIfAbsent(m.group(2)) { [] } << key
          double mjd = m.group(1) as double
          minMjd = Math.min(minMjd, mjd); maxMjd = Math.max(maxMjd, mjd)
        }
      } finally { scanner.close() }
    } finally { t.close() }
    [byObject: byObject, keys: keys, minMjd: keys ? minMjd : null, maxMjd: keys ? maxMjd : null]
  }

  /** Exact batched Gets of static rows; returns staticKey -> raw cells (qualifier -> bytes). */
  Map<String, Map<String, byte[]>> fetch(String name, List<String> staticKeys, List<String> columns, int batch) {
    Map<String, Map<String, byte[]>> result = [:]
    byte[] family = 'r'.getBytes('UTF-8')
    def t = table(name)
    try {
      staticKeys.collate(batch).each { chunk ->
        List gets = chunk.collect { k ->
          Get g = new Get(k.getBytes('UTF-8'))
          columns.each { c -> g.addColumn(family, c.getBytes('UTF-8')) }
          g
        }
        def rows = t.get((List<Get>)gets)
        if (rows.length != chunk.size()) throw new IllegalStateException('HBase batch size mismatch')
        chunk.eachWithIndex { k, i ->
          def r = rows[i]
          if (r == null || r.isEmpty()) return
          if (new String(r.getRow(), 'UTF-8') != k) throw new IllegalStateException("Mismatched row for ${k}")
          Map<String, byte[]> cells = [:]
          columns.each { c -> byte[] v = r.getValue(family, c.getBytes('UTF-8')); if (v != null) cells[c] = v }
          result[k] = cells
        }
      }
    } finally { t.close() }
    result
  }

  // ---- export ------------------------------------------------------------
  Map export(Map cfg, File outDir) {
    Map s = cfg.sampling
    int seed = s.seed
    String alertTable = cfg.hbase.alert_table
    Map perClass = [:]
    Map<String, Set<String>> classKeys = [:]
    Map<String, List<String>> sampled = [:]
    cfg.classes.each { String cls ->
      if (!exists(cls)) { perClass[cls] = [status: 'missing table']; classKeys[cls] = [] as Set; return }
      Map scanned = scanKeys(cls, s.max_scan_keys_per_class)
      classKeys[cls] = scanned.keys
      List<String> chosen = sample(scanned.byObject, seed, s.max_alerts_per_class, s.max_alerts_per_object)
      sampled[cls] = chosen
      perClass[cls] = [status: scanned.keys ? 'ok' : 'empty table', table_alerts: scanned.keys.size(),
        table_objects: scanned.byObject.size(), min_mjd: scanned.minMjd, max_mjd: scanned.maxMjd,
        sampled_alerts: chosen.size(),
        sampled_objects: chosen.collect { (it =~ TAG_KEY)[0][2] }.toSet().size()]
    }
    // Union of sampled keys; first class (config order) that sampled it is recorded.
    // Loss and fallback counts are per class sample (a key sampled for that class).
    Map<String, Set<String>> sampledSets = sampled.collectEntries { cls, keys -> [(cls): keys as Set] }
    Map<String, String> sampledFor = [:]
    cfg.classes.each { cls -> sampled[cls]?.each { k -> sampledFor.putIfAbsent(k, cls) } }
    List<String> schemaNames = []
    Map<String, String> types = schemaTypes(alertTable, schemaNames)
    List<String> measured = MEASUREMENTS.findAll { types["r:${it}"] in ['float','double','integer','long'] }
    if (!types['r:band'] || IDENTITY.any { !types["r:${it}"] })
      throw new IllegalStateException('Alert table lacks identity or band columns')
    List<String> tagKeys = sampledFor.keySet().sort()
    Map<String, String> toStatic = tagKeys.collectEntries { k -> def m = (k =~ TAG_KEY)[0]; [(k): staticKey(m[2], m[1])] }
    Map<String, Map<String, byte[]>> raw = fetch(alertTable, tagKeys.collect { toStatic[it] }, measured + IDENTITY, s.batch_size)
    // Some tagged alerts have no canonical static row (seen for whole nights).
    // Tag rows are byte copies of the alert row, so optionally read the same
    // columns from the tag table itself; identity is verified identically below.
    Map<String, String> source = [:]
    boolean fallback = s.fallback_to_tag_row == null ? true : s.fallback_to_tag_row
    if (fallback) {
      Map<String, List<String>> byTable = [:]
      tagKeys.findAll { raw[toStatic[it]] == null }.each { k ->
        byTable.computeIfAbsent(cfg.classes.find { classKeys[it].contains(k) }) { [] } << k
      }
      byTable.each { cls, keys ->
        Map<String, Map<String, byte[]>> got = fetch(cls, keys, measured + IDENTITY, s.batch_size)
        keys.each { k -> if (got[k] != null) { raw[toStatic[k]] = got[k]; source[k] = cls } }
      }
    }
    int missing = 0, written = 0
    Map<String, Integer> tagRowByClass = [:].withDefault { 0 }
    Map<String, Integer> missingByClass = [:].withDefault { 0 }
    List<String> missingSample = []
    Map<String, Integer> multi = [:].withDefault { 0 }
    File alerts = new File(outDir, 'alerts.jsonl'), labels = new File(outDir, 'labels.jsonl')
    alerts.withWriter('UTF-8') { aw -> labels.withWriter('UTF-8') { lw ->
      tagKeys.each { k ->
        String sk = toStatic[k]
        Map<String, byte[]> cells = raw[sk]
        if (cells == null) {
          missing++
          cfg.classes.findAll { sampledSets[it]?.contains(k) }.each { missingByClass[it]++ }
          if (missingSample.size() < 20) missingSample << k
          return
        }
        def m = (k =~ TAG_KEY)[0]
        String oid = decode('long', cells['diaObjectId'])?.toString()
        String sid = decode('long', cells['diaSourceId'])?.toString()
        Double mjd = decode('double', cells['midpointMjdTai'])
        String band = decode('string', cells['band'])
        if (oid != m[2] || sid == null || mjd == null || Math.abs(mjd - (m[1] as double)) > 1e-6 || !(band ==~ /[ugrizy]/))
          throw new IllegalStateException("Identity of ${sk} disagrees with tag key ${k}")
        Map clean = [:]
        measured.each { c ->
          def v = decode(types["r:${c}"], cells[c])
          if (v != null && Double.isFinite(v as double)) clean["r:${c}"] = v.toString()
        }
        clean['r:band'] = band
        List<String> member = cfg.classes.findAll { classKeys[it].contains(k) }
        if (source[k]) cfg.classes.findAll { sampledSets[it]?.contains(k) }.each { tagRowByClass[it]++ }
        multi[member.size().toString()]++
        aw.write(JsonOutput.toJson([kind: 'dia', row_key: sk, table: source[k] ?: alertTable, cells: clean]) + '\n')
        lw.write(JsonOutput.toJson([row_key: sk, tag_key: k, object_id: oid, source_id: sid,
          mjd: m[1], band: band, classes: member, sampled_for: sampledFor[k],
          cells_from: source[k] ?: alertTable]) + '\n')
        written++
      }
    } }
    if (!written) throw new IllegalStateException('No labeled alerts exported')
    // Completeness: a sampled tag row without its canonical alert row is lost;
    // fail when any class loses more than max_missing_fraction of its sample.
    double maxMissing = (s.max_missing_fraction == null ? 0.05 : s.max_missing_fraction) as double
    List<String> lossy = []
    cfg.classes.each { cls ->
      int n = sampled[cls]?.size() ?: 0
      int lost = missingByClass[cls] ?: 0
      if (perClass[cls] instanceof Map && perClass[cls].status == 'ok') {
        perClass[cls].missing_static_rows = lost
        perClass[cls].missing_fraction = n ? lost / (double)n : 0d
        perClass[cls].exported_alerts = n - lost
        perClass[cls].cells_from_tag_row = tagRowByClass[cls] ?: 0
      }
      if (n && lost / (double)n > maxMissing) lossy << "${cls} ${lost}/${n}".toString()
    }
    if (lossy) throw new IllegalStateException("Missing canonical alert rows above max_missing_fraction=${maxMissing}: ${lossy.join(', ')}")
    Map summary = [format: 'lomikel-lsst-tag-alerts-v1', alert_table: alertTable,
      alert_schemas: schemaNames.sort(), schema_types_consistent: true,
      measured_columns: measured.collect { "r:${it}" }, classes: perClass,
      exported_alerts: written, missing_static_rows: missing, missing_static_examples: missingSample,
      fallback_to_tag_row: fallback, cells_from_tag_row: source.size(),
      exported_objects: new File(outDir, 'labels.jsonl').readLines().collect { new JsonSlurper().parseText(it).object_id }.toSet().size(),
      alerts_by_label_count: multi, sampling: s, config_sha256: sha(JsonOutput.toJson(cfg)),
      created: java.time.OffsetDateTime.now().withNano(0).toString(),
      note: 'Labels are Fink rubin.tag_* filter memberships of individual alerts, not independent physical truth.']
    new File(outDir, 'summary.json').text = JsonOutput.prettyPrint(JsonOutput.toJson(summary)) + '\n'
    summary
  }

  static void run(String configPath, String output) {
    Map cfg = loadConfig(configPath)
    File target = new File(output)
    if (target.exists()) throw new IllegalArgumentException("Output ${output} already exists; choose a fresh directory")
    target.absoluteFile.parentFile.mkdirs()
    File stage = Files.createTempDirectory(target.absoluteFile.parentFile.toPath(), '.lsst-tag-export-').toFile()
    def conf = HBaseConfiguration.create()
    conf.set('hbase.zookeeper.quorum', cfg.hbase.host)
    conf.set('hbase.zookeeper.property.clientPort', cfg.hbase.port.toString())
    def conn = ConnectionFactory.createConnection(conf)
    try {
      Map summary = new ExportTagAlerts(conn).export(cfg, stage)
      Files.move(stage.toPath(), target.toPath())
      summary.classes.each { cls, st -> println "${cls}: ${st.status} table_alerts=${st.table_alerts ?: 0} table_objects=${st.table_objects ?: 0} sampled_alerts=${st.sampled_alerts ?: 0}" }
      println "Exported ${summary.exported_alerts} alerts of ${summary.exported_objects} objects (missing static rows: ${summary.missing_static_rows}) to ${output}"
    } finally {
      conn.close()
      if (stage.exists()) stage.deleteDir()
    }
  }
}

if (!binding.hasVariable('LSST_TAG_EXPORT_TEST')) {
  String configPath = binding.hasVariable('config') ? binding.getVariable('config') : null
  String output = binding.hasVariable('output') ? binding.getVariable('output') : null
  if (!configPath || !output) throw new IllegalArgumentException("Use -o \"config='tag_classes.json',output='DIR'\"")
  ExportTagAlerts.run(configPath, output)
}
ExportTagAlerts

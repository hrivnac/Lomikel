import groovy.json.JsonOutput

// Run from the repository root with the Lomikel HBase jars on the classpath, e.g.
//   java -cp "$(ls lib/*.jar | tr '\n' ':')" groovy.ui.GroovyMain \
//        src/test/lsst_lc_tag_eval/test_export_groovy.groovy
// Offline: config validation, key mapping, sampling and curve assembly only.
def root = new File('.').canonicalFile
def shell = new GroovyShell(new Binding(LSST_LC_TAG_EXPORT_TEST: true))
def Export = shell.evaluate(new File(root, 'src/work/LightCurves/LSST/export_tag_lightcurves.groovy'))
def fails = { Class type, Closure action ->
  try { action(); assert false: "expected ${type.simpleName}" }
  catch (Throwable e) { assert type.isInstance(e): "expected ${type.simpleName}, got ${e}" }
}

// Keys: tag <mjd>_<oid> -> object prefix <oid mod 1000>_<oid>_ and static key prefix+mjd.
assert Export.objectPrefix('170028486625329005') == '005_170028486625329005_'
assert Export.staticKey('61090.1_170028486625329005') == '005_170028486625329005_61090.1'
fails(IllegalArgumentException) { Export.staticKey('005_170028486625329005_61090.1') }

// Object sampling: deterministic, capped, seed dependent.
List objs = (1..50).collect { "${170000 + it}".toString() }
List a = Export.sampleObjects(objs, 7, 10)
assert a == Export.sampleObjects(objs.reverse(), 7, 10) && a.size() == 10 && a.toSet().size() == 10
assert Export.sampleObjects(objs, 8, 10) != a
assert Export.sampleObjects(objs, 7, 1000).size() == 50

// Curve assembly.
String oid = '170000000000000123'
String p = Export.objectPrefix(oid)
def row = { String sid, double mjd, def flux = 10f as double, def err = 1d, String band = 'g', String o = oid ->
  [diaObjectId: o as long, diaSourceId: sid as long, midpointMjdTai: mjd, band: band, psfFlux: flux, psfFluxErr: err] }
Map opts = [table: 'rubin.diaSource_static', schema: 's', min_points: 3]
Map rows = [(p + '61000.1'): row('1', 61000.1d), (p + '61000.3'): row('3', 61000.3d),
            (p + '61000.2'): row('2', 61000.2d, Double.NaN)]
// Tagged alerts: one has a static row, one only a tag row, one nowhere.
Map tagged = [('61000.1_' + oid): ['rubin.tag_a'], ('61000.4_' + oid): ['rubin.tag_a'], ('61000.5_' + oid): ['rubin.tag_b']]
Map tagRows = [('61000.4_' + oid): row('4', 61000.4d)]
Map built = Export.buildCurve(oid, rows, tagged, tagRows, opts)
assert built.status == 'ok'
assert built.record.sources*.source_id == ['1', '3', '4']               // sorted, invalid flux dropped
assert built.record.sources.last().row_key == p + '61000.4'             // tag-row source under its static key
assert built.counts == [sources: 3, invalid_sources: 1, tag_row_sources: 1, missing_tagged: ['61000.5_' + oid]]
assert built.record.object_key == p && built.record.complete == true && built.record.kind == 'dia'
assert built.record.sources.every { it.keySet() == ['row_key', 'source_id', 'mjd', 'band', 'flux', 'flux_err'] as Set }
// A tag row duplicating a static source is not counted twice.
Map dup = Export.buildCurve(oid, rows + [(p + '61000.4'): row('4', 61000.4d)], tagged, tagRows, opts)
assert dup.counts.tag_row_sources == 0 && dup.record.sources.size() == 3
// Too few valid observations -> short (no record); no time span -> no_span.
assert Export.buildCurve(oid, [(p + '61000.1'): row('1', 61000.1d)], [:], [:], opts).status == 'short'
assert Export.buildCurve(oid, [(p + 'a'): row('1', 61000.1d), (p + 'b'): row('2', 61000.1d), (p + 'c'): row('3', 61000.1d)], [:], [:], opts).status == 'no_span'
// Tag-row sources count towards max_points: a curve above the cap is excluded ('capped'), never exported.
Map capOpts = opts + [max_points: 3]
Map full3 = [(p + '61000.1'): row('1', 61000.1d), (p + '61000.2'): row('2', 61000.2d), (p + '61000.3'): row('3', 61000.3d)]
Map capped = Export.buildCurve(oid, full3, [('61000.4_' + oid): ['rubin.tag_a']], [('61000.4_' + oid): row('4', 61000.4d)], capOpts)
assert capped.status == 'capped' && capped.record == null && capped.counts.sources == 4
assert Export.buildCurve(oid, full3, [:], [:], capOpts).status == 'ok'
// Identity problems fail the export.
fails(IllegalStateException) { Export.buildCurve(oid, [(p + 'x'): row('1', 61000.1d, 1d, 1d, 'g', '170000000000000124')], [:], [:], opts) }
fails(IllegalStateException) { Export.buildCurve(oid, ['999_x_61000.1': row('1', 61000.1d)], [:], [:], opts) }
fails(IllegalStateException) { Export.buildCurve(oid, [(p + 'a'): row('1', 61000.1d), (p + 'b'): row('1', 61000.2d)], [:], [:], opts) }
fails(IllegalStateException) { Export.buildCurve(oid, rows, [('61000.4_' + oid): ['t']], [('61000.4_' + oid): row('4', 61000.9d)], opts) }
// Unknown band / non-positive error are unusable measurements, not identity errors.
assert Export.source('k', row('9', 61000.1d, 1d, 0d), oid) == null
assert Export.source('k', row('9', 61000.1d, 1d, 1d, 'q'), oid) == null

// Config validation.
def write = { Map cfg -> File f = File.createTempFile('lctagcfg', '.json'); f.deleteOnExit(); f.text = JsonOutput.toJson(cfg); f.path }
Map good = [format: 'lomikel-lsst-lc-tag-eval-config-v1', classes: ['rubin.tag_a', 'rubin.tag_b'],
  hbase: [host: 'h', port: 2183, alert_table: 'rubin.diaSource_static'],
  sampling: [seed: 1, max_objects_per_class: 10, max_scan_keys_per_class: 100, max_points: 512, min_points: 3, threads: 4]]
assert Export.loadConfig(write(good)).classes == ['rubin.tag_a', 'rubin.tag_b']
fails(IllegalArgumentException) { Export.loadConfig(write(good + [format: 'lomikel-lsst-tag-eval-config-v1'])) }
fails(IllegalArgumentException) { Export.loadConfig(write(good + [classes: ['rubin.tag_a', 'rubin.tag_a']])) }
fails(IllegalArgumentException) { Export.loadConfig(write(good + [classes: ["rubin.tag_a'; drop"]])) }
fails(IllegalArgumentException) { Export.loadConfig(write(good + [sampling: good.sampling + [max_points: 513]])) }
fails(IllegalArgumentException) { Export.loadConfig(write(good + [sampling: good.sampling + [min_points: 2]])) }
fails(IllegalArgumentException) { Export.loadConfig(write(good + [sampling: good.sampling + [threads: 0]])) }
fails(IllegalArgumentException) { Export.loadConfig(write(good + [sampling: good.sampling + [max_missing_fraction: 1]])) }
fails(IllegalArgumentException) { Export.loadConfig(write(good + [sampling: good.sampling + [fallback_to_tag_row: 'yes']])) }
fails(IllegalArgumentException) { Export.loadConfig(write(good + [hbase: good.hbase + [alert_table: 'rubin.diaSource_sso']])) }
assert Export.loadConfig(new File(root, 'src/work/LightCurves/LSST/tag_classes.json').path).classes.size() == 7
println 'export_tag_lightcurves tests passed'

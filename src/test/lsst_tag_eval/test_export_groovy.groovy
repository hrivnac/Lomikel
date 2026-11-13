import groovy.json.JsonOutput

// Run from the repository root with the Lomikel HBase jar on the classpath, e.g.
//   java -cp "lib/Lomikel-HBase-${version}.jar:$(ls lib/*.jar | tr '\n' ':')" groovy.ui.GroovyMain \
//        src/test/lsst_tag_eval/test_export_groovy.groovy
// Offline: exercises config validation, key mapping, decoding and sampling only.
def root = new File('.').canonicalFile
def shell = new GroovyShell(new Binding(LSST_TAG_EXPORT_TEST: true))
def Export = shell.evaluate(new File(root, 'src/work/Clusters/LSST/export_tag_alerts.groovy'))
def fails = { Closure action ->
  try { action(); assert false: 'expected IllegalArgumentException' }
  catch (IllegalArgumentException expected) { }
}

// Tag key <mjd>_<oid> maps to the static alert key <oid mod 1000>_<oid>_<mjd>.
assert Export.staticKey('170587106587967772', '61343.013849857016') == '772_170587106587967772_61343.013849857016'
assert Export.staticKey('170028486625329005', '61090.1') == '005_170028486625329005_61090.1'

// Big-endian HBase encodings.
assert Export.decode('float', [0x45, 0x6b, 0x86, 0x0b] as byte[]) == Float.intBitsToFloat(0x456b860b) as double
assert Export.decode('long', [0, 0, 0, 0, 0, 0, 1, 0] as byte[]) == 256L
assert Export.decode('integer', [0xff, 0xff, 0xff, 0xff] as byte[]) == -1
assert Export.decode('double', new byte[4]) == null
assert Export.decode('string', 'r'.getBytes('UTF-8')) == 'r'

// Sampling: deterministic, object-first, capped per object and per class.
Map keys = (1..20).collectEntries { o -> [(o.toString()): (1..5).collect { "6100${it}.5_${o}".toString() }] }
List a = Export.sample(keys, 7, 12, 3)
assert a == Export.sample(keys, 7, 12, 3)
assert a.size() == 12
assert a.groupBy { it.split('_')[1] }.values().every { it.size() <= 3 }
assert a.collect { it.split('_')[1] }.toSet().size() == 4
assert Export.sample(keys, 8, 12, 3) != a
assert Export.sample(keys, 7, 1000, 2).size() == 40

// Config validation.
def write = { Map cfg -> File f = File.createTempFile('tagcfg', '.json'); f.deleteOnExit(); f.text = JsonOutput.toJson(cfg); f.path }
Map good = [format: 'lomikel-lsst-tag-eval-config-v1', classes: ['rubin.tag_a', 'rubin.tag_b'],
  hbase: [host: 'h', port: 2183, alert_table: 'rubin.diaSource_static'],
  sampling: [seed: 1, max_alerts_per_class: 10, max_alerts_per_object: 2, max_scan_keys_per_class: 100, batch_size: 10]]
assert Export.loadConfig(write(good)).classes == ['rubin.tag_a', 'rubin.tag_b']
fails { Export.loadConfig(write(good + [format: 'x'])) }
fails { Export.loadConfig(write(good + [classes: ['rubin.tag_a', 'rubin.tag_a']])) }
fails { Export.loadConfig(write(good + [classes: ['rubin.diaSource_static']])) }
fails { Export.loadConfig(write(good + [classes: ["rubin.tag_a'; drop"]])) }
fails { Export.loadConfig(write(good + [sampling: good.sampling + [batch_size: 0]])) }
fails { Export.loadConfig(write(good + [sampling: good.sampling + [batch_size: 100000]])) }
fails { Export.loadConfig(write(good + [hbase: good.hbase + [alert_table: 'rubin.diaSource_sso']])) }
fails { Export.loadConfig(write(good + [sampling: good.sampling + [max_missing_fraction: 1]])) }
fails { Export.loadConfig(write(good + [sampling: good.sampling + [max_missing_fraction: -0.1]])) }
assert Export.loadConfig(write(good + [sampling: good.sampling + [max_missing_fraction: 0.1]]))
fails { Export.loadConfig(write(good + [sampling: good.sampling + [fallback_to_tag_row: 'yes']])) }
assert Export.loadConfig(new File(root, 'src/work/Clusters/LSST/tag_classes.json').path).classes.size() >= 2

// Exported measurement allowlist never contains identifiers, positions or times.
assert !Export.MEASUREMENTS.any { it =~ /(?i)id$|^ra|^dec|mjd|visit|detector|^x$|^y$|time/ }
println 'export_tag_alerts tests passed'

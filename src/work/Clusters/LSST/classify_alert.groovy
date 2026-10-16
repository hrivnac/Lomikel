// Read-only single-alert inference for lomikel-lsst-pca-kmeans-v1 models.
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

class ClassifyAlert {
  static double finiteNumber(def value, String name) {
    if (!(value instanceof Number) || !Double.isFinite(value.doubleValue()))
      throw new IllegalArgumentException("${name} must be a finite number")
    value.doubleValue()
  }

  static void validateModel(Map model) {
    if (model.format != 'lomikel-lsst-pca-kmeans-v1' || !(model.kind in ['dia','ss']))
      throw new IllegalArgumentException('Unsupported model format or alert kind')
    List features = model.features
    int n = features?.size() ?: 0
    if (!n || features.any { !(it instanceof String) || !(it ==~ /r:(?:psfFlux|psfFluxErr|apFlux|apFluxErr|scienceFlux|scienceFluxErr|templateFlux|templateFluxErr|snr|extendedness|ixx|ixy|iyy|dipole(?:Flux|FluxErr|Length|Angle|Separation)|trail(?:Flux|FluxErr|Length|Width|Angle|Snr))/) } || features.toSet().size() != n)
      throw new IllegalArgumentException('Model features must be unique allowlisted r: measurement columns')
    ['impute','mean','scale'].each { field ->
      if (!(model[field] instanceof List) || model[field].size() != n)
        throw new IllegalArgumentException("Invalid ${field} dimensions")
      model[field].each { finiteNumber(it, field) }
    }
    if (model.scale.any { it.doubleValue() <= 0 }) throw new IllegalArgumentException('Scales must be positive')
    int d = model.components instanceof List ? model.components.size() : 0
    if (!d || !(model.explained_variance_ratio instanceof List) || model.explained_variance_ratio.size() != d)
      throw new IllegalArgumentException('Invalid PCA dimensions')
    model.components.each { component ->
      if (!(component instanceof List) || component.size() != n) throw new IllegalArgumentException('Invalid PCA component dimensions')
      component.each { finiteNumber(it, 'component') }
    }
    model.explained_variance_ratio.each {
      double value = finiteNumber(it, 'explained variance ratio')
      if (value < 0 || value > 1) throw new IllegalArgumentException('Invalid explained variance ratio')
    }
    int k = model.centers instanceof List ? model.centers.size() : 0
    if (!k || !(model.radii instanceof List) || model.radii.size() != k)
      throw new IllegalArgumentException('Invalid cluster dimensions')
    model.centers.each { center ->
      if (!(center instanceof List) || center.size() != d) throw new IllegalArgumentException('Invalid cluster center dimensions')
      center.each { finiteNumber(it, 'center') }
    }
    model.radii.each { if (finiteNumber(it, 'radius') < 0) throw new IllegalArgumentException('Negative radius') }
  }

  Map classify(Map model, Map row) {
    validateModel(model)
    if (row.kind != model.kind || !(row.row_key instanceof String) || !row.row_key || !(row.cells instanceof Map))
      throw new IllegalArgumentException('Row kind, key or cells invalid')
    Map origin = model.provenance instanceof Map ? model.provenance : [:]
    if ((origin.table && row.table != origin.table) || (origin.schema && row.schema != origin.schema) ||
        (model.table && row.table != model.table) || (model.schema && row.schema != model.schema))
      throw new IllegalArgumentException('Row table/schema provenance mismatch')
    if (!model.features.any { row.cells[it] != null && row.cells[it].toString().trim() })
      throw new IllegalArgumentException('At least one measured model feature is required')
    List x = model.features.withIndex().collect { feature, i ->
      def value = row.cells[feature]
      double raw
      if (value == null || value.toString().trim() == '') raw = model.impute[i].doubleValue()
      else {
        try { raw = Double.parseDouble(value.toString()) }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Invalid row value for ${feature}") }
        if (!Double.isFinite(raw)) throw new IllegalArgumentException("Nonfinite row value for ${feature}")
      }
      double standardized = (raw - model.mean[i].doubleValue()) / model.scale[i].doubleValue()
      if (!Double.isFinite(standardized)) throw new IllegalArgumentException('Nonfinite standardized value')
      standardized
    }
    List projection = model.components.collect { component ->
      double value = 0d
      component.eachWithIndex { coefficient, i -> value += coefficient.doubleValue() * x[i] }
      if (!Double.isFinite(value)) throw new IllegalArgumentException('Nonfinite projection')
      value
    }
    List distances = model.centers.collect { center ->
      double norm = 0d
      center.eachWithIndex { value, i -> norm = Math.hypot(norm, projection[i] - value.doubleValue()) }
      if (!Double.isFinite(norm)) throw new IllegalArgumentException('Nonfinite distance')
      norm
    }
    int index = distances.indices.min { distances[it] }
    [kind:model.kind, row_key:row.row_key, class_id:index, distance:distances[index],
     outlier:distances[index] > model.radii[index].doubleValue()]
  }

  Map classifyFromClient(Map model, def client, Map opts) {
    validateModel(model)
    if (opts.kind != model.kind || !opts.table || !opts.schema || !opts.'row-key')
      throw new IllegalArgumentException('HBase kind, table, schema and exact row key required')
    Map origin = model.provenance instanceof Map ? model.provenance : [:]
    if ((model.table && model.table != opts.table) || (model.schema && model.schema != opts.schema) ||
        (origin.table && origin.table != opts.table) || (origin.schema && origin.schema != opts.schema))
      throw new IllegalArgumentException('Model table/schema metadata mismatch')
    try {
      client.connect(opts.table, opts.schema)
      def schema = client.schema()
      if (schema == null || model.features.any { !schema.isNumber(it) })
        throw new IllegalArgumentException('Model features are absent or nonnumeric in schema')
      client.setLimit(1)
      client.setSearchLimit(1)
      Map records = client.scan(opts.'row-key', [:], model.features.join(','), 0L, 0L, false, false)
      if (!records.containsKey(opts.'row-key')) throw new IllegalArgumentException('Row not found')
      return classify(model, [kind:opts.kind, row_key:opts.'row-key', table:opts.table,
        schema:opts.schema, cells:records[opts.'row-key']])
    } finally { client.close() }
  }

  static Map parseArgs(String[] argv) {
    Map opts = [:]
    Set allowed = ['model','host','port','table','schema','kind','row-key','row-json'] as Set
    for (int i = 0; i < argv.length; i += 2) {
      if (i + 1 >= argv.length || !argv[i].startsWith('--') || !(argv[i].substring(2) in allowed) || opts.containsKey(argv[i].substring(2)))
        throw new IllegalArgumentException('Invalid argument; use --name value')
      opts[argv[i].substring(2)] = argv[i+1]
    }
    if (!opts.model || (opts.'row-json' != null) == (opts.'row-key' != null))
      throw new IllegalArgumentException('Require model and exactly one row-json or row-key')
    if (opts.'row-key' && (!opts.host || !(opts.port ==~ /[1-9][0-9]*/) || Integer.parseInt(opts.port) > 65535 || !opts.table || !opts.schema || !(opts.kind in ['dia','ss'])))
      throw new IllegalArgumentException('HBase row requires host port table schema kind')
    opts
  }

  static String[] argvFromBinding(Binding source) {
    List<String> argv = []
    ['model','host','port','table','schema','kind','rowKey','rowJson'].each { name ->
      if (source.hasVariable(name) && source.getVariable(name) != null)
        argv.addAll(["--${name == 'rowKey' ? 'row-key' : name == 'rowJson' ? 'row-json' : name}", source.getVariable(name).toString()])
    }
    return argv as String[]
  }

  static void main(String[] argv) {
    Map opts = parseArgs(argv)
    Map model = (Map)new JsonSlurper().parse(new File(opts.model.toString()))
    validateModel(model)
    Map row
    if (opts.'row-json') row = (Map)new JsonSlurper().parse(new File(opts.'row-json'.toString()))
    else {
      if (opts.kind != model.kind) throw new IllegalArgumentException('Requested kind does not match model')
      def client = Class.forName('com.Lomikel.HBaser.HBaseClient')
        .getConstructor(String, int.class).newInstance(opts.host, Integer.parseInt(opts.port))
      row = new ClassifyAlert().classifyFromClient(model, client, opts)
      println JsonOutput.toJson(row)
      return
    }
    println JsonOutput.toJson(new ClassifyAlert().classify(model, row))
  }
}

if (!binding.hasVariable('LSST_ALERT_TEST')) ClassifyAlert.main(binding.hasVariable('cli') ? ClassifyAlert.argvFromBinding(binding) :
  (binding.hasVariable('args') ? (String[])binding.getVariable('args') : new String[0]))
ClassifyAlert

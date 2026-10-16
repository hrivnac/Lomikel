// Read-only object-level NN embedding and K-means inference; no graph writes.
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class ClassifyLightcurve {
  static final List BANDS = ['u','g','r','i','z','y']
  static final String COLUMNS = 'r:diaObjectId,r:ssObjectId,r:diaSourceId,r:midpointMjdTai,r:band,r:psfFlux,r:psfFluxErr'
  Closure searchMapFactory = { Map m -> Class.forName('com.Lomikel.DB.SearchMap').getConstructor(Map).newInstance(m) }

  static double number(def value, String label) {
    if (!(value instanceof Number) || !Double.isFinite(value.doubleValue()))
      throw new IllegalArgumentException("${label} must be finite numeric")
    value.doubleValue()
  }
  static int positive(def value, String label) {
    if (!(value?.toString() ==~ /[1-9][0-9]*/) || new BigInteger(value.toString()) >= Integer.MAX_VALUE)
      throw new IllegalArgumentException("${label} must be a bounded positive integer")
    Integer.parseInt(value.toString())
  }
  static double cell(def value, String label) {
    try {
      if (value == null || !value.toString().trim()) throw new NumberFormatException()
      double n = Double.parseDouble(value.toString())
      if (!Double.isFinite(n)) throw new NumberFormatException()
      return n
    } catch (NumberFormatException ex) { throw new IllegalArgumentException("Invalid ${label}") }
  }

  static void digestValue(def value, MessageDigest digest) {
    if (value == null) digest.update((byte)'n')
    else if (value instanceof Boolean) digest.update((byte)(value ? 't' : 'f'))
    else if (value instanceof BigInteger || value instanceof Long || value instanceof Integer ||
             value instanceof Short || value instanceof Byte) {
      digest.update((byte)'i')
      digest.update(value.toString().getBytes(StandardCharsets.US_ASCII))
      digest.update((byte)':')
    } else if (value instanceof Number) {
      double n = value.doubleValue()
      if (!Double.isFinite(n)) throw new IllegalArgumentException('Non-finite model number')
      digest.update((byte)'d')
      digest.update(ByteBuffer.allocate(8).putDouble(n).array())
    } else if (value instanceof String) {
      byte[] data = value.getBytes(StandardCharsets.UTF_8)
      digest.update((byte)'s')
      digest.update(Integer.toString(data.length).getBytes(StandardCharsets.US_ASCII))
      digest.update((byte)':')
      digest.update(data)
    } else if (value instanceof List) {
      digest.update((byte)'l')
      digest.update(Integer.toString(value.size()).getBytes(StandardCharsets.US_ASCII))
      digest.update((byte)':')
      value.each { digestValue(it,digest) }
    } else if (value instanceof Map) {
      digest.update((byte)'m')
      digest.update(Integer.toString(value.size()).getBytes(StandardCharsets.US_ASCII))
      digest.update((byte)':')
      if (value.keySet().any { !(it instanceof String) })
        throw new IllegalArgumentException('Non-string model key')
      value.keySet().sort().each { key -> digestValue(key,digest); digestValue(value[key],digest) }
    } else throw new IllegalArgumentException('Unsupported model value')
  }

  static String modelDigest(Map model) {
    MessageDigest digest = MessageDigest.getInstance('SHA-256')
    digestValue(model.findAll { key,value -> key != 'model_id' },digest)
    digest.digest().encodeHex().toString()
  }

  static void validateModel(Map model) {
    if (model.format != 'lomikel-lsst-lc-nn-kmeans-v1' || !(model.kind in ['dia','ss']) ||
        model.bands != BANDS || !(model.bins instanceof Number) ||
        model.bins.intValue() != model.bins.doubleValue() || model.bins.intValue() < 1 || model.bins.intValue() > 10000 ||
        !(model.model_id instanceof String) || !model.model_id || !(model.provenance instanceof Map) ||
        !(model.provenance.table instanceof String) || !model.provenance.table ||
        !(model.provenance.schema instanceof String) || !model.provenance.schema)
      throw new IllegalArgumentException('Invalid model metadata')
    int n = BANDS.size() * model.bins.intValue() * 3 + 2
    ['feature_mean','feature_scale','encoder_weights'].each { field ->
      if (!(model[field] instanceof List) || model[field].size() != n)
        throw new IllegalArgumentException("Invalid ${field} dimension")
    }
    model.feature_mean.each { number(it,'feature_mean') }
    model.feature_scale.each { if (number(it,'feature_scale') <= 0) throw new IllegalArgumentException('Nonpositive feature scale') }
    int d = model.encoder_bias instanceof List ? model.encoder_bias.size() : 0
    if (!d || !(model.centers instanceof List) || !model.centers ||
        !(model.radii instanceof List) || model.radii.size() != model.centers.size())
      throw new IllegalArgumentException('Invalid embedding/cluster dimensions')
    model.encoder_bias.each { number(it,'encoder_bias') }
    model.encoder_weights.each { weight ->
      if (!(weight instanceof List) || weight.size() != d) throw new IllegalArgumentException('Invalid encoder weight shape')
      weight.each { number(it,'encoder weight') }
    }
    model.centers.each { center ->
      if (!(center instanceof List) || center.size() != d) throw new IllegalArgumentException('Invalid center shape')
      center.each { number(it,'center') }
    }
    model.radii.each { if (number(it,'radius') < 0) throw new IllegalArgumentException('Negative radius') }
    if (!(model.model_id ==~ /[0-9a-f]{64}/) || model.model_id != modelDigest(model))
      throw new IllegalArgumentException('Model digest mismatch')
  }

  static List features(Map row, int bins) {
    if (!(row.object_id instanceof String) || !(row.object_id ==~ /[0-9]+/) ||
        !(row.object_key instanceof String) || !(row.sources instanceof List) ||
        row.complete != true || row.sources.size() < 3 || row.sources.size() > 512)
      throw new IllegalArgumentException('Incomplete or invalid object')
    String prefix = row.object_key
    String pattern = row.kind == 'dia' ? /[0-9]{3}_[0-9]+_/ : /[0-9]{2}_[A-Za-z0-9]+_/
    if (!(prefix ==~ pattern) || (row.kind == 'dia' && prefix.split('_')[1] != row.object_id))
      throw new IllegalArgumentException('Invalid object prefix')
    Set ids = [] as Set, keys = [] as Set
    List sources = row.sources.collect { source ->
      if (!(source instanceof Map) || !(source.source_id instanceof String) ||
          !(source.source_id ==~ /[0-9]+/) || !(source.row_key instanceof String) ||
          !source.row_key.startsWith(prefix) || source.row_key == prefix || !ids.add(source.source_id) || !keys.add(source.row_key) ||
          !(source.band in BANDS)) throw new IllegalArgumentException('Invalid/duplicate source')
      double mjd = cell(source.mjd,'mjd'), flux = cell(source.flux,'flux'), err = cell(source.flux_err,'flux_err')
      if (err <= 0) throw new IllegalArgumentException('Nonpositive flux error')
      [mjd:mjd,flux:flux,err:err,band:source.band,source_id:source.source_id]
    }.sort { a,b -> (a.mjd <=> b.mjd) ?: (a.source_id <=> b.source_id) }
    double duration = sources.last().mjd - sources.first().mjd
    if (!Double.isFinite(duration) || duration <= 0) throw new IllegalArgumentException('Positive duration required')
    List absolute = sources.collect { Math.abs(it.flux) }.sort()
    int middle = absolute.size().intdiv(2)
    double median = absolute.size() % 2 ? absolute[middle] : (absolute[middle-1]/2d + absolute[middle]/2d)
    double scale = Math.max(1d,median)
    int size = BANDS.size() * bins
    List fluxSum = (0..<size).collect { 0d }, errSum = (0..<size).collect { 0d }
    List count = (0..<size).collect { 0 }
    sources.each { source ->
      double t = (source.mjd - sources.first().mjd) / duration
      int bin = Math.min(bins - 1, (int)Math.floor(t * bins))
      int index = BANDS.indexOf(source.band) * bins + bin
      double normalized = source.flux / scale
      double magnitude = Math.abs(normalized)
      double asinh = magnitude > 1e150d ? Math.log(magnitude) + Math.log(2d) :
        Math.log1p(magnitude + magnitude * magnitude / (1d + Math.hypot(1d,magnitude)))
      fluxSum[index] += Math.copySign(asinh,normalized)
      errSum[index] += Math.log1p(source.err / scale)
      count[index]++
    }
    List values = fluxSum.indices.collect { i -> count[i] ? fluxSum[i] / count[i] : 0d } +
      errSum.indices.collect { i -> count[i] ? errSum[i] / count[i] : 0d } +
      count.collect { it ? 1d : 0d } + [Math.log1p(duration)/5d,Math.log1p(scale)/10d]
    if (values.any { !Double.isFinite(it as double) }) throw new IllegalArgumentException('Nonfinite features')
    [values, BANDS.findAll { band -> sources.any { it.band == band } }, sources.size()]
  }

  Map classify(Map model, Map row) {
    validateModel(model)
    if (row.kind != model.kind || row.table != model.provenance.table || row.schema != model.provenance.schema)
      throw new IllegalArgumentException('Kind or provenance mismatch')
    List extracted = features(row,model.bins.intValue())
    List vector = extracted[0]
    List standardized = vector.indices.collect { i ->
      double x = (vector[i] - model.feature_mean[i].doubleValue()) / model.feature_scale[i].doubleValue()
      if (!Double.isFinite(x)) throw new IllegalArgumentException('Nonfinite standardized feature')
      x
    }
    List embedding = model.encoder_bias.indices.collect { i ->
      double activation = model.encoder_bias[i].doubleValue()
      standardized.eachWithIndex { value,j -> activation += value * model.encoder_weights[j][i].doubleValue() }
      if (!Double.isFinite(activation)) throw new IllegalArgumentException('Nonfinite embedding')
      Math.tanh(activation)
    }
    List distances = model.centers.collect { center ->
      double distance = 0d
      center.eachWithIndex { value,i -> distance = Math.hypot(distance,embedding[i]-value.doubleValue()) }
      if (!Double.isFinite(distance)) throw new IllegalArgumentException('Nonfinite distance')
      distance
    }
    int nearest = distances.indices.min { distances[it] }
    [kind:model.kind,object_id:row.object_id,class_id:nearest,distance:distances[nearest],
     outlier:distances[nearest] > model.radii[nearest].doubleValue(),model_id:model.model_id,
     bands_present:extracted[1],source_count:extracted[2]]
  }

  Map classifyFromClient(Map model, def client, Map opts) {
    validateModel(model)
    String anchor = opts.'row-key'?.toString()
    int cut = anchor?.lastIndexOf('_') ?: -1
    String prefix = cut > 0 ? anchor.substring(0,cut+1) : ''
    if (!anchor || cut <= 0 || !anchor.substring(cut+1) || !(opts.kind in ['dia','ss']) ||
        opts.kind != model.kind || opts.table != model.provenance.table || opts.schema != model.provenance.schema)
      throw new IllegalArgumentException('Invalid anchor or model provenance')
    String pattern = opts.kind == 'dia' ? /[0-9]{3}_[0-9]+_/ : /[0-9]{2}_[A-Za-z0-9]+_/
    if (!(prefix ==~ pattern) || !(anchor.substring(cut+1) ==~ /[0-9]+(?:\.[0-9]+)?/))
      throw new IllegalArgumentException('Invalid anchor row key')
    int max = positive(opts.'max-points','max-points')
    if (max > 512) throw new IllegalArgumentException('max-points cannot exceed 512')
    try {
      client.connect(opts.table.toString(),opts.schema.toString())
      if (client.schema() == null) throw new IllegalArgumentException('Requested schema unavailable')
      client.setLimit(max+1); client.setSearchLimit(max+1)
      Map rows = client.scan(null,searchMapFactory(['key:key':prefix]),COLUMNS,0L,0L,false,false)
      if (!rows?.containsKey(anchor) || rows.size() > max) throw new IllegalStateException('Missing anchor or capped scan')
      // Apply exactly the exporter identity, duplicate and numeric rules before classification.
      String field = opts.kind == 'dia' ? 'r:diaObjectId' : 'r:ssObjectId'
      Set ids = [] as Set, keys = [] as Set
      String objectId
      List sources = rows.collect { key,cells ->
        if (!(key instanceof String) || !key.startsWith(prefix) || !(cells instanceof Map))
          throw new IllegalStateException('Foreign row')
        String id = cells[field]?.toString(), source = cells['r:diaSourceId']?.toString()
        if (!(id ==~ /[0-9]+/) || (objectId && objectId != id) ||
            (opts.kind == 'dia' && prefix.split('_')[1] != id) ||
            !(source ==~ /[0-9]+/) || !ids.add(source) || !keys.add(key))
          throw new IllegalStateException('Mixed object or duplicate source')
        objectId = id
        [row_key:key,source_id:source,mjd:cells['r:midpointMjdTai'],band:cells['r:band'],
         flux:cells['r:psfFlux'],flux_err:cells['r:psfFluxErr']]
      }
      classify(model,[kind:opts.kind,object_id:objectId,object_key:prefix,table:opts.table,
        schema:opts.schema,complete:true,sources:sources])
    } finally { client.close() }
  }

  static Map parseArgs(String[] argv) {
    Map opts = [:]
    Set allowed = ['model','row-json','row-key','host','port','table','schema','kind','max-points'] as Set
    for (int i=0; i<argv.length; i+=2) {
      if (i+1 >= argv.length || !argv[i].startsWith('--') || !(argv[i].substring(2) in allowed) ||
          opts.containsKey(argv[i].substring(2)) || !argv[i+1]) throw new IllegalArgumentException('Invalid option')
      opts[argv[i].substring(2)] = argv[i+1]
    }
    if (!opts.model || (opts.'row-json' != null) == (opts.'row-key' != null))
      throw new IllegalArgumentException('Require model and exactly one row-json or row-key')
    if (opts.'row-key') {
      if (!opts.host || !opts.table || !opts.schema || !(opts.kind in ['dia','ss']) ||
          positive(opts.port,'port') > 65535) throw new IllegalArgumentException('Incomplete HBase options')
      if (positive(opts.'max-points','max-points') > 512)
        throw new IllegalArgumentException('max-points cannot exceed 512')
    }
    opts
  }
  static String[] argvFromBinding(Binding binding) {
    List args = []
    [model:'model',rowJson:'row-json',rowKey:'row-key',host:'host',port:'port',table:'table',
     schema:'schema',kind:'kind',maxPoints:'max-points'].each { name,flag ->
      if (binding.hasVariable(name) && binding.getVariable(name) != null)
        args.addAll(["--${flag}",binding.getVariable(name).toString()])
    }
    args as String[]
  }
  static void main(String[] argv) {
    Map opts = parseArgs(argv)
    Map model = (Map)new JsonSlurper().parse(new File(opts.model.toString()))
    validateModel(model)
    def classifier = new ClassifyLightcurve()
    Map prediction
    if (opts.'row-json') prediction = classifier.classify(model,(Map)new JsonSlurper().parse(new File(opts.'row-json'.toString())))
    else {
      Class base = Class.forName('com.Lomikel.HBaser.HBaseClient')
      def client = new GroovyClassLoader(base.classLoader).parseClass("""
        class StrictLSSTLightcurveInferenceClient extends ${base.name} {
          StrictLSSTLightcurveInferenceClient(String host, int port) { super(host,port) }
          @Override protected void handleScanIOException(java.io.IOException failure) {
            throw new IllegalStateException('HBase object scan failed',failure)
          }
        }
      """).getConstructor(String,int.class).newInstance(opts.host,Integer.parseInt(opts.port))
      prediction = classifier.classifyFromClient(model,client,opts)
    }
    println JsonOutput.toJson(prediction)
  }
}

if (!binding.hasVariable('LSST_LC_TEST')) ClassifyLightcurve.main(binding.hasVariable('cli') ? ClassifyLightcurve.argvFromBinding(binding) :
  (binding.hasVariable('args') ? (String[])binding.getVariable('args') : new String[0]))
ClassifyLightcurve

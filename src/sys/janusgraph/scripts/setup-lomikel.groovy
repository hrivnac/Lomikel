def globals = [:]

globals << [hook : [
  onStartUp: { ctx -> ctx.logger.info("Executed once at startup of Gremlin Server.")},
  onShutDown: { ctx -> ctx.logger.info("Executed once at shutdown of Gremlin Server.")}
  ] as LifeCycleHook]
  
// -----------------------------------------------------------------------------
  
class LomikelServer {

  def static init() {
    println "class Lomikel Server initialised"
    }

  def static hi() {
    return "Hello World from Lomikel Server !"
    }
    
  def static geosearch(g, ra, dec, ang, jdmin, jdmax, limit) {
    def lat = dec
    def lon = ra - 180
    def dist = ang * 6371.0087714 * Math.PI / 180
    def nDir = g.V().has('direction', geoWithin(Geoshape.circle(lat, lon, dist))).count().next()
    def nJD  = g.V().has('direction', geoWithin(Geoshape.circle(lat, lon, dist))).limit(nDir).has('jd', inside(jdmin, jdmax)).count().next()
    if (limit < nJD) {
      nJD = limit
      }
    return g.V().has('direction', geoWithin(Geoshape.circle(lat, lon, dist))).limit(nDir).has('jd', inside(jdmin, jdmax)).limit(nJD)
    }
  
  def static get_or_create(g, lbl, name, value) {
    return g.V().has('lbl', lbl).
                 has(name, value).
                 fold().
                 coalesce(unfold(), addV(lbl).
                                    property('lbl', lbl).
                                    property(name, value));
    }
    
  def static get_or_create_edge(g, lbl1, name1, value1, lbl2, name2, value2, edge) {
    return g.V().has('lbl', lbl1).
                 has(name1, value1).
                 as('v').
             V().has('lbl', lbl2).
                 has(name2, value2).
             coalesce(__.inE(edge).where(outV().as('v')), addE(edge).from('v'));
      }

  // w = g.addV().property('lbl', 'datalink').property('technology', 'Phoenix').property('url', 'jdbc:phoenix:ithdp2101.cern.ch:2181' ).property('query', "select * from AEI.CANONICAL_0 where project = 'mc16_13TeV'").next()
  // w = g.addV().property('lbl', 'datalink').property('technology', 'Graph'  ).property('url', 'hbase:188.184.87.217:8182:janusgraph').property('query', "g.V().limit(1)").next()
  // w = g.addV().property('lbl', 'datalink').property('technology', 'HBase'  ).property('url', '134.158.74.54:2183:ztf:schema').property('query', "client.setLimit(10); return client.scan(null, null, null, 0, false, false)").next()
  def static getDataLink(v, q = null) {
    def url   = v.values('url').next()
    def query
    if (q != null) {
      query = q
      }
    else if (v.values('query').hasNext()) {
      query = v.values('query').next()
      }
    else {
      return 'no Query'
      }
    try {
      switch (v.values('technology').next()) {
        case 'HBase':
          def (hostname, port, table, schema) = url.split(':') // 134.158.74.54:2181:ztf:schema_0.7.0_0.3.8
          def client = new com.Lomikel.HBaser.HBaseClient(hostname, port)
          client.connect(table, schema)
          return Eval.me('client', client, query)
          break
        case 'Graph':
          def (backend, hostname, port, table) = url.split(':') // hbase:188.184.87.217:8182:janusgraph
          def graph = JanusGraphFactory.build().
                                        set('storage.backend',     backend ).
                                        set('storage.hostname',    hostname).
                                        set('storage.port',        port    ).
                                        set('storage.hbase.table', table   ).
                                        open()
          def g = graph.traversal()
          query = query
          return Eval.me('g', g, query)
          break
        case 'Phoenix':
          return groovy.sql.Sql.newInstance(url, 'org.apache.phoenix.jdbc.PhoenixDriver').
                                rows(query).toString()
          break
        default:
          return 'DataLink ' + v + ' unknown'
          }
        }
      catch (Exception e) {
      return 'DataLink ' + v + ' not found'
      }
    }

  def static standardDeviation(g, lbl, variableNames) {
    def sdMap = [:]
    variableNames.split().stream().each {v ->
      g.V().has('lbl', lbl).values(v).fold().as(v).mean(local).as('mean').select(v).unfold().math('(_-mean)^2').mean().math('sqrt(_)').map{sd -> println  v + ': ' + sd
                                                                                                                        sdMap += [(v):sd]}.next()
      }
    return sdMap
    }
    
  def static drop_by_date(graph, importDate, nCommit, tWait) {
    def g = graph.traversal()
    //importDate = 'Mon Feb 14 05:51:20 UTC 2022'
    //nCommit = 500
    def i = 0
    def tot = 0
    def nMax = g.V().has('importDate', importDate).count().next()
    println('' + nMax + ' vertexes to drop')
    def t0 = System.currentTimeMillis()
    while(true) {
      g.V().has('importDate', importDate).limit(nCommit).out().out().drop().iterate()
      g.V().has('importDate', importDate).limit(nCommit).out().drop().iterate()
      g.V().has('importDate', importDate).limit(nCommit).drop().iterate()
      graph.traversal().tx().commit()
      Thread.sleep(tWait)
      tot = nCommit * ++i
      def dt = (System.currentTimeMillis() - t0) / 1000
      def per = 100 * tot / nMax
      def freq = tot / dt
      def rest = (nMax - tot) / freq / 60 /60
      println(tot + ' = ' + per + '% at ' + freq + 'Hz, ' + rest + 'h to go')
      }
    }
      
  def static importStatus(g) {
    def txt = ''
    txt += 'Imported:\n'
    g.V().has('lbl', 'Import').has('nAlerts', neq(0)).order().by('importSource').valueMap('importSource', 'importDate', 'nAlerts').each{txt += '\t' + it + '\n'}
    txt += 'Importing:\n'
    g.V().has('lbl', 'Import').hasNot('complete').order().by('importSource').valueMap('importSource', 'importDate').each{txt += '\t' + it + '\n'}
    return txt
    }
    
  def static candidates(g, objectId) {
    return g.V().has('objectId', objectId).out().out().has('lbl', 'candidate')
    }  
    
  def static registerAlertOfInterest(g, alertType, objectId, jd, url) {   
    return g.V().
             has('AlertsOfInterest', 'lbl', 'AlertsOfInterest').
             has('alertType', alertType).
             fold().
             coalesce(unfold(), 
                      addV('AlertsOfInterest').
                      property('lbl', 'AlertsOfInterest').
                      property('alertType', alertType).
                      property('technology', 'HBase').
                      property('url', url)).
             addE('contains').
             to(__.addV('alert').
                   property('lbl', 'alert').
                   property('objectId', objectId).
                   property('jd', jd)).
             next()
    }
      
  def static graph = JanusGraphFactory.build().set("storage.backend", "hbase").set("storage.hostname", "@STORAGE.HOSTNAME@").set("storage.port", "@STORAGE.PORT@").set("storage.hbase.table", "@STORAGE.JANUS.TABLE@").open()
  def static g = graph.traversal()

  }
  
// -----------------------------------------------------------------------------
  
LomikelServer.init()

globals << [graph : LomikelServer.graph]
globals << [g : LomikelServer.g]


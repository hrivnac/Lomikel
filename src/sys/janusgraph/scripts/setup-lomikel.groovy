def globals = [:]

globals << [hook : [
  onStartUp: { ctx -> ctx.logger.info("Executed once at startup of Gremlin Server.")},
  onShutDown: { ctx -> ctx.logger.info("Executed once at shutdown of Gremlin Server.")}
  ] as LifeCycleHook]
  
class LomikelServer {

  def static init() {
    println "class Lomikel Server initialised"
    }

  def static hi() {
    return "Hello World from Lomikel Server !"
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
        return 'unknown DataLink ' + v
        }
    }
      
  def static graph = JanusGraphFactory.build().set("storage.backend", "hbase").set("storage.hostname", "@STORAGE.HOSTNAME@").set("storage.port", "@STORAGE.PORT@").set("storage.hbase.table", "@STORAGE.JANUS.TABLE@").open()
  def static g = graph.traversal()

  }
  
LomikelServer.init()

globals << [graph : LomikelServer.graph]
globals << [g : LomikelServer.g]


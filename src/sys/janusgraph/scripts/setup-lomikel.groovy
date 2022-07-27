import groovy.sql.Sql
  
// -----------------------------------------------------------------------------

println "a"

class Lomikel_CERN {

  def static init() {
    graph = JanusGraphFactory.build().set("storage.backend", "hbase").set("storage.hostname", "@STORAGE.HOSTNAME@").set("storage.port", "@STORAGE.PORT@").set("storage.hbase.table", "@STORAGE.JANUS.TABLE@").open()
    g = graph.traversal()
    println "class Lomikel CERN initialised"
    }

  def static hi() {
    return "Hello World from Lomikel CERN server !"
    }
    
  def static get_or_create(lbl, name, value) {
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
  def static getDataLink(v) {
    switch (v.values('technology').next()) {
      case 'HBase':
        return 'HBase'
        break
      case 'Graph':
        def (backend, hostname, port, table) = v.values('url').next().split(':') // hbase:188.184.87.217:8182:janusgraph
        def graph1 = JanusGraphFactory.build().
                                       set("storage.backend",     backend).
                                       set("storage.hostname",    hostname).
                                       set("storage.port",        port).
                                       set("storage.hbase.table", table).
                                       open()
        def g1 = graph1.traversal()
        return Eval.me('g', g1, v.values('query').next())
        break
      case 'Phoenix':
        return Sql.newInstance(v.values('url').next(), 'org.apache.phoenix.jdbc.PhoenixDriver').
                   rows(v.values('query').next())
        break
      default:
        return 'unknown DataLink ' + v
        }
      }
      
  def static graph
  def static g
              
  }
  
// -----------------------------------------------------------------------------
    
println "b"

Lomikel_CERN.init()

println "c"

def globals = [:]

globals << [hook : [
  onStartUp: { ctx -> ctx.logger.info("Executed once at startup of Gremlin Server.")},
  onShutDown: { ctx -> ctx.logger.info("Executed once at shutdown of Gremlin Server.")}
  ] as LifeCycleHook]
  
println "d"  
  
globals << [graph : Lomikel_CERN.graph]
globals << [g : Lomikel_CERN.g]

println "e"

w = g.addV().property('lbl', 'datalink').property('technology', 'Phoenix').property('url', 'jdbc:phoenix:ithdp2101.cern.ch:2181' ).property('query', "select * from AEI.CANONICAL_0 where project = 'mc16_13TeV'").next()

println "f"

Lomikel_CERN.getDataLink(w)

println "g"


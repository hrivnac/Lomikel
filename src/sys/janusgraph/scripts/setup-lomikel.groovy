import groovy.sql.Sql

class Lomikel {

  def static hi() {
    return "Hello World from Lomikel server !"
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
  
  def static getDataLink(v) {
    switch (v.values('technology').next()) {
      case 'HBase':
        return 'HBase'
        break
      case 'Graph':
        (backend, hostname, port, table) = v.values('url').next().split(':') // hbase:188.184.87.217:8182:janusgraph
        graph = JanusGraphFactory.build().
                                  set("storage.backend",     backend).
                                  set("storage.hostname",    hostname).
                                  set("storage.port",        port).
                                  set("storage.hbase.table", table).
                                  open()
        g = graph.traversal()
        return Eval.me('g', g, daba[3])
        break
      case 'Phoenix':
        return Sql.newInstance(v.values('url').next(), 'org.apache.phoenix.jdbc.PhoenixDriver').
                   rows(v.values('query').next())
        break
      default:
        return 'unknown DataLink ' + v
        }
      }
      
    }
    
def globals = [:]

globals << [hook : [
  onStartUp: { ctx -> ctx.logger.info("Executed once at startup of Gremlin Server.")},
  onShutDown: { ctx -> ctx.logger.info("Executed once at shutdown of Gremlin Server.")}
  ] as LifeCycleHook]
  
globals << [graph : JanusGraphFactory.build().set("storage.backend", "hbase").set("storage.hostname", "@STORAGE.HOSTNAME@").set("storage.port", "@STORAGE.PORT@").set("storage.hbase.table", "@STORAGE.JANUS.TABLE@").open()]
globals << [g : graph.traversal()]

Lomikel.init()


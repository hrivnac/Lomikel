def globals = [:]

globals << [hook : [
  onStartUp: { ctx -> ctx.logger.info("Executed once at startup of Gremlin Server.")},
  onShutDown: { ctx -> ctx.logger.info("Executed once at shutdown of Gremlin Server.")}
  ] as LifeCycleHook]
  
// -----------------------------------------------------------------------------
  
class LomikelServer {

  def static init() {
    println "Lomikel/FinkBrowser Server initialised";
    }

  def static hi() {
    return "Hello World from Lomikel Server !";
    }
   
  def static graph = JanusGraphFactory.build().set("storage.backend", "hbase").set("storage.hostname", "@STORAGE.HOSTNAME@").set("storage.port", "2183").set("storage.hbase.table", "janusgraph").open();
  def static g     = graph.traversal();
  def static gr    = new com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG(g);

  }
  
// -----------------------------------------------------------------------------
  
LomikelServer.init()

globals << [graph : LomikelServer.graph]
globals << [g     : LomikelServer.g]
globals << [gr    : LomikelServer.gr]


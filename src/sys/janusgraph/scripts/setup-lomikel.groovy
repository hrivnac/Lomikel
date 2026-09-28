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
    
  def static graph = JanusGraphFactory.open("conf/gremlin-server/Local.properties")
  def static g     = graph.traversal();
  def static gr    = new com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG(g);

  }
  
// -----------------------------------------------------------------------------
  
LomikelServer.init()

globals << [graph : LomikelServer.graph]
globals << [g     : LomikelServer.g]
globals << [gr    : LomikelServer.gr]


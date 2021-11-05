def globals = [:]

globals << [hook : [
  onStartUp: { ctx -> ctx.logger.info("Executed once at startup of Gremlin Server.")},
  onShutDown: { ctx -> ctx.logger.info("Executed once at shutdown of Gremlin Server.")}
  ] as LifeCycleHook]
  
globals << [graph : JanusGraphFactory.build().set("storage.backend", "hbase").set("storage.hostname", "@STORAGE.HOSTNAME@").set("storage.port", "@STORAGE.PORT@").set("storage.hbase.table", "@STORAGE.JANUS.TABLE@").open()]
globals << [g : graph.traversal()]

def hi_lomikel() {
  return "Hello World from Lomikel server !"
  }
  
def get_or_create(lbl, name, value) {
  return g.V().has('lbl', lbl).
               has(name, value).
               fold().
               coalesce(unfold(),
                        addV(lbl).
                        property('lbl', lbl).
                        property(name, value));
  }
             

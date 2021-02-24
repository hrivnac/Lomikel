clientH = new com.Lomikel.HBaser.HBaseClient("@STORAGE.HOSTNAME@", @STORAGE.HBASE.PORT@)
clientH.connect("@STORAGE.HBASE.TABLE@", "@STORAGE.HBASE.SCHEMA@")
com.Lomikel.Januser.Hertex.setHBaseClient(clientH)
clientP = new com.Lomikel.Phoenixer.PhoenixProxyClient("@BACKEND.PHOENIX.PROXY.HOSTNAME@", @BACKEND.PHOENIX.PROXY.PORT@)
clientP.connect("@DATASETS.TABLE@", "@DATASETS.SCHEMA@")
com.Lomikel.Januser.Sertex.setPhoenixClient(clientP);

def globals = [:]

globals << [hook : [
  onStartUp: { ctx -> ctx.logger.info("Executed once at startup of Gremlin Server.")},
  onShutDown: { ctx -> ctx.logger.info("Executed once at shutdown of Gremlin Server.")}
  ] as LifeCycleHook]
  
globals << [graph : JanusGraphFactory.build().set("storage.backend", "hbase").set("storage.hostname", "@STORAGE.HOSTNAME@").set("storage.hbase.table", "@STORAGE.JANUS.TABLE@").open()]
globals << [g : graph.traversal()]


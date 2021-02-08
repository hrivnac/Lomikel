client = new com.Lomikel.HBaser.HBaseClient("localhost", 2181)
client.connect("test_portal_tiny.3", "schema_0.7.0_0.3.6")
com.Lomikel.Januser.Hertex.setHBaseClient(client)
com.Lomikel.Januser.Hertex.setRowkeyName("rowkey")

def globals = [:]

globals << [hook : [
  onStartUp: { ctx -> ctx.logger.info("Executed once at startup of Gremlin Server.")},
  onShutDown: { ctx -> ctx.logger.info("Executed once at shutdown of Gremlin Server.")}
  ] as LifeCycleHook]
  
globals << [graph : JanusGraphFactory.build().set("storage.backend", "hbase").set("storage.hostname", "@STORAGE.HOSTNAME@").set("storage.hbase.table", "@STORAGE.HBASE.TABLE@").open()]
globals << [g : graph.traversal()]


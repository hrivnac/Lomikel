db = "@BACKEND.DB@"
client = null
if (db.equals("hbase")) {
  client = new com.Lomikel.HBaser.HBaseClient("@STORAGE.HOSTNAME@", @STORAGE.HBASE.PORT@)
  client.connect("@STORAGE.HBASE.TABLE@", "@STORAGE.HBASE.SCHEMA@")
  com.Lomikel.Januser.Hertex.setHBaseClient(client)
  com.Lomikel.Januser.Hertex.setRowkeyName("@ROWKEY.NAME@")
  }
if (db.equals("phoenix")) {
  client = new com.Lomikel.Phoenixer.PhoenixProxyClient("@BACKEND.PHOENIX.PROXY.HOSTNAME@", @BACKEND.PHOENIX.PROXY.PORT@)
  client.connect("@DATASETS.TABLE@", "@DATASETS.SCHEMA@")
  com.Lomikel.Januser.Sertex.setPhoenixClient(client);
  com.Lomikel.Januser.Sertex.setRowkeyNames(client.schema().rowkeyNames())
  }

def globals = [:]

globals << [hook : [
  onStartUp: { ctx -> ctx.logger.info("Executed once at startup of Gremlin Server.")},
  onShutDown: { ctx -> ctx.logger.info("Executed once at shutdown of Gremlin Server.")}
  ] as LifeCycleHook]
  
globals << [graph : JanusGraphFactory.build().set("storage.backend", "hbase").set("storage.hostname", "@STORAGE.HOSTNAME@").set("storage.hbase.table", "@STORAGE.JANUS.TABLE@").open()]
globals << [g : graph.traversal()]


# read alert list from parquet file in HDFS
# register them in graph of 'AlertsOfInterest'
# enhance them with columns from HBase
# export resulting graph into graphml file

from com.Lomikel.Parquet import ParquetReader
from com.Lomikel.Januser import StringGremlinClient

hostH      = "hbase-1.lal.in2p3.fr"
portH      = "2183"
hostG      = "134.158.74.85"
portG      = 24445
table      = "ztf"
schema     = "schema_3.1_5.4.0"
url        = hostH + ":" + portH + ":" + table + ":" + schema
columns    = "i:ra,i:dec,i:magpsf,i:jd,i:sigmapsf,i:fid,i:jdstarthist,i:magpsf,i:sigmapsf,i:fid,i:magnr,i:sigmagnr,i:isdiffpos,i:neargaia,i:sgscore1,i:classtar,d:anomaly_score,d:rf_kn_vs_nonkn,d:rf_snia_vs_nonia,d:snn_sn_vs_all,d:snn_snia_vs_nonia"
    
collection = "graph_index_0000001";
file       = "/tmp/graph_index_0000001.graphml"

reader  = ParquetReader("hdfs://134.158.75.222:8020")
clientG = StringGremlinClient(hostG, portG)

reader.process("/user/julien.peloton/graph_index_0000001.parquet", "parquet")

for rowkey in reader.props()["index"]:
  rk = rowkey.split("_");
  cmd = "LomikelServer.registerAlertOfInterest('" + collection + "', '" + rk[0] + "', " + rk[1] + ", '" + url + "')"
  clientG.interpret(cmd);


clientG.interpret("LomikelServer.enhanceAlertsOfInterest('" + collection + "', '" + columns + "')");
clientG.interpret("LomikelServer.exportAlertsOfInterest('" + collection + "', '" + file + "')")

# graphml file can be created locally using JanusGraph gremlin console:
# Lomikel.exportAlertsOfInterest(g, 'graph_index_0000001', '/tmp/x.graphml')

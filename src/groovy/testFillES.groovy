import com.Lomikel.ElasticSearcher.ESClient;
import com.Lomikel.HBaser.AsynchHBaseClient;
import com.Lomikel.Utils.Timer;

timer = new Timer("entries", 100, -1);

esclient = new ESClient("http://134.158.74.85:20200");

client = new AsynchHBaseClient("hbase-1.lal.in2p3.fr", 2183);
client.connect("ztf", "schema_3.1_5.6.2");
client.startScan(null,
                 null,
                 "i:ra,i:dec,i:jd",
                 0,
                 0,
                 false,
                 false);

timer.start();
while (client.scanning() || client.size() > 0) {
  if (client.size() > 0) {
    println(client.size() + ":");
    timer.report();
    client.poll().each {k, v -> esclient.putGeoPoint("radec",
                                                     "location",
                                                     k,
                                                     Double.valueOf(v.get("i:ra")),
                                                     Double.valueOf(v.get("i:dec")));
                                esclient.putValue("jd_double",
                                                  "jd_double",
                                                  k,
                                                  Double.valueOf(v.get("i:jd")));
                                esclient.putValue("jd_string",
                                                  "jd_string",
                                                  k,
                                                  v.get("i:jd"));
                                esclient.putValue("jd_nano",
                                                  "jd_nano",
                                                  k,
                                                  (int)(1000000000 * Double.valueOf(v.get("i:jd"))));
                         }
    }
  }
  
client.stop();

import com.Lomikel.ElasticSearcher.ESClient;
import com.Lomikel.HBaser.AsynchHBaseClient;
import com.Lomikel.Utils.Timer;

timer = new Timer("entries", 1000, 5);

esclient = new ESClient("http://157.136.253.253:20200");

client = new AsynchHBaseClient("vdhbase1.lal.in2p3.fr", 2183);
client.connect("ztf", "schema_3.1_5.6.2");
//client.setLimit(20000);
now = System.currentTimeMillis();
client.startScan(null,
                 null,
                 "i:ra,i:dec,i:jd",
                 now - 90000000, // 1 day
                 now,
                 false,
                 false);

timer.start();
while (client.scanning() || client.size() > 0) {
  if (client.size() > 0) {
    //println(client.size() + ":");
    client.poll().each {k, v -> esclient.putGeoPoint("radec",
                                                     "location",
                                                     k,
                                                     Double.valueOf(v.get("i:ra")),
                                                     Double.valueOf(v.get("i:dec")));
                                esclient.putValue("jd_double",
                                                  "jd_double",
                                                  k,
                                                  Double.valueOf(v.get("i:jd")));
                                esclient.putValue("jd_text",
                                                  "jd_text",
                                                  k,
                                                  v.get("i:jd"));
                                esclient.putValue("jd_long",
                                                  "jd_long",
                                                  k,
                                                  (long)(1000000000 * Double.valueOf(v.get("i:jd"))));
                         }
    if (timer.report()) {
      esclient.commit();
      }
    }
  }

esclient.commit();

client.stop();



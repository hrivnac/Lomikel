import com.Lomikel.ElasticSearcher.ESClient;
import com.Lomikel.HBaser.AsynchHBaseClient;
import com.Lomikel.Utils.Timer;
import com.Lomikel.Utils.NotifierURL;
import com.Lomikel.Utils.Info;

// Log
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;

Configurator.initialize(null, "../src/java/log4j2.xml");

int delay = 1;

public String sizes() {
  String sizes = "";
  for (String idxName : new String[]{"radec", "mjd"}) {
    sizes += idxName + "[" + esclient.size(idxName) + "], ";
    }
  return sizes;
  }


esclient = new ESClient("http://157.136.253.253:20200");
Logger log = LogManager.getLogger(this.class);
Timer timer = new Timer("entries", 1000, 1);

String osizes = sizes();

AsynchHBaseClient client = new AsynchHBaseClient("vdhbase1.lal.in2p3.fr", 2183);
client.setMaxQueueSize(1000);
client.connect("ztf", "schema_3.1_5.6.2");
now = System.currentTimeMillis();
client.startScan(null,
                 null,
                 "i:ra,i:dec,i:jd",
                 now - 90000000 * delay,
                 now,
                 false,
                 false);

timer.start();

while (client.scanning() || client.size() > 0) {
  if (client.size() > 0) {
    client.poll().each {k, v -> esclient.putGeoPoint("radec",
                                                     "location",
                                                     k.split("_")[0],
                                                     Double.valueOf(v.get("i:ra")),
                                                     Double.valueOf(v.get("i:dec")));
                                esclient.updateDoubleArrayWithRetry("mjd", 
                                                                    "mjd",
                                                                    k.split("_")[0],
                                                                    Double.valueOf(v.get("i:jd")),
                                                                    10);
                         }                                                     
    if (timer.report()) {
      esclient.commitWithRetry(10);
      }
    }
  }

esclient.commitWithRetry(10);

String psizes = sizes();
log.info("Original sizes: " + osizes);
log.info("Final    sizes: " + psizes);

NotifierURL.notify("fillES", "Lomikel", Info.release() ,"Original sizes: " + osizes + "\nFinal    sizes: " + psizes);


client.stop();




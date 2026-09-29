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
log = LogManager.getLogger(this.class);

delay = 1;

startupWaitMillis = 30000;

timer = new Timer("entries", 1000, 1);

now = System.currentTimeMillis();

public String sizes() {
  String sizes = "";
  for (String idxName : new String[]{"radec", "mjd"}) {
    sizes += idxName + "[" + esclient.size(idxName) + "], ";
    }
  return sizes;
  }


esclient = new ESClient("http://157.136.253.253:24499");
osizes = sizes();

client = new AsynchHBaseClient("vdhbase1.lal.in2p3.fr", 2183);
client.setMaxQueueSize(100);
client.connect("ztf", "schema_4.0_6.1.1");

log.info("Importing alerts within last " + delay + " days");

timer.start();

client.startScan(null,
                 null,
                 "i:ra,i:dec,i:jd",
                 now - 90000000 * delay,
                 now,
                 true,
                 false);

// The scan can start asynchronously, but a completed empty scan must not wait forever.
try {
  long startupDeadline = System.currentTimeMillis() + startupWaitMillis;
  while (client.scanPending() && !client.scanning() && client.size() == 0 &&
         System.currentTimeMillis() < startupDeadline) {
    Thread.sleep(100);
    }
  if (client.scanPending() && !client.scanning() && client.size() == 0) {
    throw new IllegalStateException('ZTF HBase scan did not start before deadline');
    }
  timer.start();
  while (client.scanPending() || client.size() > 0) {
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
    else {
      Thread.sleep(100);
      }
    }
  if (client.scanFailure() != null) {
    throw new IllegalStateException('LSST HBase scan failed', client.scanFailure());
    }
  }
finally {
  client.stop();
  client.close();
  }

esclient.commitWithRetry(10);

String psizes = sizes();
log.info("Original sizes: " + osizes);
log.info("Final    sizes: " + psizes);

// BUG: why doesn't work in thread ?
NotifierURL.notifyExecution("fillES-ZTF", "Lomikel", Info.release(), "Original sizes: " + osizes + "\nFinal    sizes: " + psizes);




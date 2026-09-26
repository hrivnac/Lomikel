import com.Lomikel.HBaser.AsynchHBaseClient;
import com.Lomikel.Januser.JanusClient;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG;
import com.astrolabsoftware.FinkBrowser.Januser.Classifier;
import com.Lomikel.Utils.Timer;
import com.Lomikel.Utils.NotifierURL;
import com.Lomikel.Utils.Info;

// Log
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;

Configurator.initialize(null, "../src/java/log4j2.xml")
log = LogManager.getLogger(this.class)

delay = 1;

startupWaitMillis = 30000;

timer = new Timer("entries", 100, 5);
 
now = System.currentTimeMillis();

client = new AsynchHBaseClient("vdhbase1.lal.in2p3.fr", 2183);
client.setMaxQueueSize(100);
client.connect("ztf", "schema_4.0_6.1.1");
//client.setLimit(20000);

jc = new JanusClient("/opt/janusgraph-1/conf/gremlin-server/IJCLabRW.properties")
gr = new FinkGremlinRecipiesG(jc)

classifiers = new Classifier[]{Classifier.instance('FINK',        'ZTF', ''          )//,
                               //Classifier.instance('XMATCH',      'ZTF', ''          ),
                               //Classifier.instance('FEATURES',    'ZTF', '2024/13-60'),
                               //Classifier.instance('FEATURES',    'ZTF', '2025/13-50'),
                               //Classifier.instance('LIGHTCURVES', 'ZTF', 'Latent'    )
                               }
formula = "cdsxmatch != 'Unknown' && roid != 3 && ndethist >= 3";
hbaseUrl = 'vdhbase1.lal.in2p3.fr:2183:ztf:schema_4.0_6.1.1'
gr.fhclient(hbaseUrl);
client.setEvaluation(formula);
  
log.info("Importing alerts within last " + delay + " days");

timer.start()

client.startScan(null,
                 null,
                 "i:objectId",
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
      client.poll().each {k, v -> for (Classifier classifier : classifiers) {
                                    try {
                                      gr.classifySource(classifier, v.get("i:objectId"));
                                      }
                                    catch (Exception e) {
                                      log.error("Cannot classify " + v.get("i:objectId") + " with " + classifier, e);
                                      throw e;
                                      }
                                    }
                           }
      timer.report();
      }
    else {
      Thread.sleep(100);
      }
    }
  if (client.scanFailure() != null) {
    throw new IllegalStateException('ZTF HBase scan failed', client.scanFailure());
    }
  }
finally {
  client.stop();
  client.close();
  }

gr.generateCorrelations(classifiers);

// BUG: why doesn't work in thread ?
NotifierURL.notifyExecution("importTags-ZTF", "Lomikel", Info.release(), timer.info("" + delay));

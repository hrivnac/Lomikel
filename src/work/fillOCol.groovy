import com.Lomikel.HBaser.AsynchHBaseClient;
import com.Lomikel.Januser.JanusClient;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG;
import com.astrolabsoftware.FinkBrowser.Januser.Classifier;
import com.Lomikel.Utils.Timer;

// Log
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;

Configurator.initialize(null, "../src/java/log4j2.xml")
log = LogManager.getLogger(this.class)

timer = new Timer("entries", 100, 5);

client = new AsynchHBaseClient("vdhbase1.lal.in2p3.fr", 2183);
client.setMaxQueueSize(100);
client.connect("ztf", "schema_3.1_5.6.2");
//client.setLimit(20000);

jc = new JanusClient("/opt/janusgraph-1/conf/gremlin-server/IJCLab.properties")
gr = new FinkGremlinRecipiesG(jc)

classifiers = new Classifier[]{Classifier.instance('FINK'),
                               Classifier.instance('XMATCH'),
                               Classifier.instance('FEATURES=2024/13-60'),
                               Classifier.instance('FEATURES=2025/13-50'),
                               Classifier.instance('LIGHTCURVES=Latent')
                               }
formula = "cdsxmatch != 'Unknown' && roid != 3 && ndethist >= 3";
hbaseUrl = 'vdhbase1.lal.in2p3.fr:2183:ztf:schema_4.0_6.1.1'
now = System.currentTimeMillis();
client.setEvaluation(formula);
client.startScan(null,
                 null,
                 "i:objectId",
                 now - 90000000, // 1 day
                 now,
                 false,
                 false);

timer.start();
while (client.scanning() || client.size() > 0) {
  if (client.size() > 0) {
    //println(client.size() + ":");
    client.poll().each {k, v -> for (Classifier classifier : classifiers) {
                                  try {
                                    gr.classifySource(classifier, v.get("i:objectId"));
                                    }
                                  catch (Exception e) {
                                    log.error("Cannot classify " + v.get("i:objectId") + " with " + classifier, e);
                                    }
                                  }
                         }
    timer.report();
    }
  }

gr.generateCorrelations(classifiers);

client.stop();



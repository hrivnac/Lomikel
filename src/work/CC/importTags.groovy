import com.Lomikel.HBaser.AsynchHBaseClient;
import com.Lomikel.Januser.JanusClient;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG;
import com.astrolabsoftware.FinkBrowser.Januser.Classifier;
import com.Lomikel.Utils.Timer;

// Log
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;

Configurator.initialize(null, '../src/java/log4j2.xml');
log = LogManager.getLogger(this.class);

timer = new Timer("entries", 100, 5);

clss = new String[]{'rubin.tag_early_snia_candidate',
                    'rubin.tag_extragalactic_lt20mag_candidate',
                    'rubin.tag_extragalactic_new_candidate',
                    'rubin.tag_good_quality',
                    'rubin.tag_hostless_candidate',
                    'rubin.tag_in_tns',
                    'rubin.tag_sn_near_galaxy_candidate'};

jc = new JanusClient("/opt/janusgraph-1/conf/gremlin-server/CC.properties");
gr = new FinkGremlinRecipiesG(jc);

client = new AsynchHBaseClient("cchbase1.in2p3.fr", 2183);
client.setMaxQueueSize(1000);

now = System.currentTimeMillis();

timer.start();

clss.each {
  cls -> log.info('Importing ' + cls);
         client.connect(cls, null); 
         client.startScan(null,
                          null,
                          null,
                          0,
                          now,
                          false,
                          false);
  while (client.scanning() || client.size() > 0) {
    if (client.size() > 0) {
      client.poll().each {k, v -> (oid, mjd) = k.tokenize('_');
                                   gr.g().addV('NewTag')
                                         .property('lbl',      'NewTag')
                                         .property('objectId', oid)
                                         .property('cls',      cls)
                                         .property('mjd',      mjd)
                                         .iterate();
                           }
      if (timer.report(cls + ": ")) {
        gr.commit();
        }
      }
    }
  }

client.stop();
client.close();

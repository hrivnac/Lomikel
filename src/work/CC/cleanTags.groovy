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

jc = new JanusClient("/opt/janusgraph-1/conf/gremlin-server/CC.properties");
gr = new FinkGremlinRecipiesG(jc);
    
log.info("Cleaning already processed NewTags");
gr.dropV("NewTag", 1000, "processed", "true");

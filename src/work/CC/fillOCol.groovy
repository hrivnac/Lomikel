import com.Lomikel.Parquet.ParquetReader;
import com.Lomikel.Januser.JanusClient;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG;
import com.astrolabsoftware.FinkBrowser.Januser.Classifier;
import com.Lomikel.Utils.Timer;

// Parquet
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroup;

// Hadoop
import org.apache.hadoop.fs.Path;

// Java
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

// Log
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;

Configurator.initialize(null, "../src/java/log4j2.xml");

int delay = 2;

public class PR extends ParquetReader {

  Logger log = LogManager.getLogger(this.class);

  JanusClient jc = new JanusClient("/opt/janusgraph-1/conf/gremlin-server/CC.properties")
  FinkGremlinRecipiesG gr = new FinkGremlinRecipiesG(jc)

  Classifier[] classifiers = new Classifier[]{Classifier.instance('FINK', 'LSST', '')}

  Timer timer = new Timer("entries", 100, 5);
  
  public PR(String url) {
    super(url);
    timer.start() 
    }

  @Override
  protected void addToSet(String name,
                          String value) {
    if (name.equals("diaObject.diaObjectId") ||
        name.equals("ssSource.ssObjectId"  )) {
      Set<String> set;
      if (props().containsKey(name)) {
        set = props().get(name);
        }
      else {
        set = new HashSet<>();
        }
      set.add(value);
      props().put(name, set);
      }
    }
    
  @Override
  public void endGroup() {
    if (props().containsKey("diaObject.diaObjectId") || props().containsKey("ssSource.ssObjectId")) {
      String key;
      if (props().containsKey("diaObject.diaObjectId")) {
        key = props().get("diaObject.diaObjectId").iterator().next();
        }
      else if (props().containsKey("ssSource.ssObjectId")) {
        key = props().get("ssSource.ssObjectId").next();
        }
      else {
        log.warn("no objectid");
        }
      for (Classifier classifier : classifiers) {
        try {
          gr.classifySource(classifier, key);
          }
        catch (Exception e) {
          log.error("Cannot classify " + key + " with " + classifier, e);
          }
        }
      props().clear();
      }  
    timer.report();
    }
    
  }
  
ParquetReader reader = new PR("hdfs://ccmaster1:8020");
yesterday = LocalDate.now().minusDays(delay).format(DateTimeFormatter.ofPattern("'year='yyyy'/month='MM'/day='dd"));
reader.processDir("/user/fink/archive/science/" + yesterday, "parquet");
gr.generateCorrelations(classifiers);

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
Logger log = LogManager.getLogger(this.class)

delay = 25;

public class PR extends ParquetReader {

  //Logger log = LogManager.getLogger(this.class);

  public PR(String url) {
    super(url);
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
    log.info(props());
    }
    
  }

timer = new Timer("entries", 100, 5); 
  
reader = new PR("hdfs://ccmaster1:8020");
yesterday = LocalDate.now().minusDays(delay).format(DateTimeFormatter.ofPattern("'year='yyyy'/month='MM'/day='dd"));
reader.processDir("/user/fink/archive/science/" + yesterday, "parquet");
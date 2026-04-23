import com.Lomikel.ElasticSearcher.ESClient;
import com.Lomikel.Parquet.ParquetReader;
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

int[] delays = new int[]{2};

public class PR extends ParquetReader {

  ESClient esclient = new ESClient("http://157.136.253.253:20200");
  Logger log = LogManager.getLogger(this.class);
  Timer timer = new Timer("entries", 1000, 1);

  public PR(String url) {
    super(url);
    timer.start();
    }

  @Override
  protected void addToSet(String name,
                          String value) {
    if (name.equals("diaSource.ra"            ) ||
        name.equals("diaSource.dec"           ) ||
        name.equals("diaSource.midpointMjdTai") ||
        name.equals("diaObject.diaObjectId"   ) ||
        name.equals("ssSource.ssObjectId"     )) {
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
      double ra  = new Double(props().get("diaSource.ra"            ).iterator().next());
      double dec = new Double(props().get("diaSource.dec"           ).iterator().next());
      double mjd = new Double(props().get("diaSource.midpointMjdTai").iterator().next());
      String key;
      if (props().containsKey("diaObject.diaObjectId")) {
        key = props().get("diaObject.diaObjectId").iterator().next();
        esclient.putGeoPoint("dia_radec", "location", key, ra, dec);
        esclient.putValue("dia_mjd", "mjd", key, mjd);
        }
      else if (props().containsKey("ssSource.ssObjectId")) {
        key = props().get("ssSource.ssObjectId").iterator().next();
        esclient.putGeoPoint("ss_radec", "location", key, ra, dec);
        esclient.putValue("ss_mjd", "mjd", key, mjd );
        }
      else {
        log.warn("no objectid");
        }
      props().clear();
      if (timer.report()) {
        esclient.commitWithRetry(10);
        }
      }
    } 
    
  public void cleanup() 
    esclient.commitWithRetry(10);
    }
    
  }
  
//esclient.createIndex("ss_radec", "location", "geo_point");
//esclient.createIndex("dia_radec", "location", "geo_point");
//esclient.createIndex("ss_mjd", "mjd", "double");
//esclient.createIndex("dia_mjd", "mjd", "double");

ParquetReader reader = new PR("hdfs://ccmaster1:8020");
for (int delay : delays) {
  aday = LocalDate.now()
                  .minusDays(delay)
                  .format(DateTimeFormatter
                  .ofPattern("'year='yyyy'/month='MM'/day='dd"));
  reader.processDir("/user/fink/archive/science/" + aday, "parquet");
  reader.cleanup();
  }
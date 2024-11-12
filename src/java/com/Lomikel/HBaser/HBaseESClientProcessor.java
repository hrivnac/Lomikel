package com.Lomikel.HBaser;

import com.Lomikel.Utils.Timer;
import com.Lomikel.Utils.LomikelException;
import com.Lomikel.ElasticSearcher.ESClient;

// HealPix
import static cds.healpix.VerticesAndPathComputer.LON_INDEX;
import static cds.healpix.VerticesAndPathComputer.LAT_INDEX;

// Java
import java.util.Map;  

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>HBaseESProcessor</code> implements {@link HBaseProcessor} for {@link ESClient}. 
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class HBaseESClientProcessor implements HBaseProcessor{
    
  /** Create and attach {@link ESCLient} and index name.
    * @param esclient The {@link ESClient} to be attached. */
  public HBaseESClientProcessor(ESClient esclient) {
    _esclient = esclient;
    _timer = new Timer("entries added", 10, 20);
    _timer.start();
    }
       
  /** Depending on <tt>_esclient</tt>, upsert results into <em>ElasticSearch</em>
    * and clean the {@link Map}. */  
  @Override
  public void processResults(Map<String, Map<String, String>> results) {
    if (_esclient != null) {
      results2ES(results);
      results.clear();
      }
    }
    
  /** Register results into {@link ESClient}.
    * @param results The {@link Map} of results. */
  // TBD: make generic
  protected void results2ES(Map<String, Map<String, String>> results) {
    for (Map.Entry<String, Map<String, String>> entry : results.entrySet()) {
      try {
        _esclient.putGeoPoint("radec",
                              "location",
                              entry.getKey(),
                              Double.valueOf(entry.getValue().get("i:ra")),
                              Double.valueOf(entry.getValue().get("i:dec")));
        _esclient.putValue("jd",
                           "date",
                           entry.getKey(),
                           entry.getValue().get("i:jd"));
        }
      catch (LomikelException e) {
        log.error("Cannot insert result " + entry, e);
        }
      _timer.report();
      }
    }
    
  private ESClient _esclient;
  
  private Timer _timer;
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(HBaseESClientProcessor.class);

  }

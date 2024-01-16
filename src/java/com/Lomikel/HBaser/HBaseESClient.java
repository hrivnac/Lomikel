package com.Lomikel.HBaser;

import com.Lomikel.Utils.LomikelException;
import com.Lomikel.ElasticSearcher.ESClient;

// HealPix
import static cds.healpix.VerticesAndPathComputer.LON_INDEX;
import static cds.healpix.VerticesAndPathComputer.LAT_INDEX;

// Java
import java.util.Map;  

// Log4J
import org.apache.log4j.Logger;

/** <code>HBaseESClient</code> connects to HBase and updates {@link ESClient}. 
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class HBaseESClient extends HBaseClient {
   
  // Lifecycle -----------------------------------------------------------------
  
  /** Create and connect to HBase.
    * @param zookeepers The comma-separated list of zookeper ids.
    * @param clientPort The client port. 
    * @throws LomikelException If anything goes wrong. */
  public HBaseESClient(String zookeepers,
                       String clientPort) throws LomikelException {
    super(zookeepers, clientPort);
    }
        
  /** Create and connect to HBase.
    * @param zookeepers The comma-separated list of zookeper ids.
    * @param clientPort The client port. 
    * @throws LomikelException If anything goes wrong. */
  public HBaseESClient(String zookeepers,
                       int    clientPort) throws LomikelException {
    super(zookeepers, clientPort);
    }
    
  /** Create and connect to HBase.
    * @param url The HBase url.
    * @throws LomikelException If anything goes wrong. */
  public HBaseESClient(String url) throws LomikelException {
    super(url);
    }
    
  /** Connect {@link ESClient}. The <tt>scan</tt> will
    * will it instead of delivering results.
    * @param url The {@link ESClient} url.
    * @param idxName The name of the index to be filled. */
  public void connectElasticSearch(String url,
                                   String idxName) {
    _esclient = new ESClient(url);
    _idxName = idxName;
    }
    
  /** Depending on <tt>_esclient</tt>, upsert results into <em>ElasticSearch</em>
    * and clean the {@link Map}. */  
  @Override
  protected void processResults(Map<String, Map<String, String>> results) {
    if (_esclient != null) {
      results2ES(results);
      results.clear();
      }
    }
    
  /** Register results into {@link ESClient}.
    * @param results The {@link Map} of results. */
  protected void results2ES(Map<String, Map<String, String>> results) {
    for (Map.Entry<String, Map<String, String>> entry : results.entrySet()) {
      try {
        _esclient.putGeoPoint(_idxName,
                              entry.getKey(),
                              Double.valueOf(entry.getValue().get("i:ra")),
                              Double.valueOf(entry.getValue().get("i:dec")));
        }
      catch (LomikelException e) {
        log.error("Cannot insert result " + entry, e);
        }
      }
    }

  private String _idxName;
    
  private ESClient _esclient;
  
  /** Logging . */
  private static Logger log = Logger.getLogger(HBaseESClient.class);

  }

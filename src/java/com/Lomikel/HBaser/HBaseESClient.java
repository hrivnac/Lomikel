package com.Lomikel.HBaser;

import com.Lomikel.Utils.LomikelException;
import com.Lomikel.ElasticSearcher.ESClient;

// HealPix
import static cds.healpix.VerticesAndPathComputer.LON_INDEX;
import static cds.healpix.VerticesAndPathComputer.LAT_INDEX;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

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
    * @param url The {@link ESClient} url. */
  public void connectElasticSearch(String url) {
    setProcessor(new HBaseESClientProcessor(new ESClient(url)));
    }
    
  /** Logging . */
  private static Logger log = LogManager.getLogger(HBaseESClient.class);

  }

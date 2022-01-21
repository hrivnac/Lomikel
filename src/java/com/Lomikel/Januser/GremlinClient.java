package com.Lomikel.Januser;

import com.Lomikel.Utils.Init;

// Tinker Pop
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.unfold;
import org.apache.tinkerpop.gremlin.structure.Graph;
import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;
import org.apache.tinkerpop.gremlin.driver.Cluster;
import org.apache.tinkerpop.gremlin.driver.MessageSerializer;

// Log4J
import org.apache.log4j.Logger;

/** <code>GremlinClient</code> provides connection to Gremlin Graph.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
// TBD: reuse (the same) serializer
public abstract class GremlinClient {
   
  /** Create with connection parameters.
    * @param hostname The Gremlin hostname.
    * @param table    The Gremlin port. */
  public GremlinClient(String  hostname,
                       int     port) {
    Init.init();
    log.info("Opening " + hostname + ":" + port);
    open(hostname, port);
    connect();
    }
   
  /** Open.  
    * @param hostname The Gremlin hostname.
    * @param table    The Gremlin port. */
  public abstract void open(String hostname,
                            int    port);
        
  /** Connect client. */
  public abstract void connect();
       
  /** Close client. */
  public abstract void close();
 
  /** Create {@link Cluster}.  
    * @param hostname The Gremlin hostname.
    * @param table    The Gremlin port.
    * @param serializer The used {@link MessageSerializer}. */
  public void createCluster(String            hostname,
                            int               port,
                            MessageSerializer serializer) {
    _cluster = Cluster.build()
                      .addContactPoint(hostname)
                      .port(port)
                      .serializer(serializer)
                      .create();
    }
  
  /** Give the {@link Cluster}.
    * @return The attached {@link Cluster}. */
  public Cluster cluster() {
    return _cluster;
    }
    
  private Cluster _cluster;  

  /** Logging . */
  private static Logger log = Logger.getLogger(GremlinClient.class);

  }

package com.Lomikel.Januser;

import com.Lomikel.Utils.Init;

// Tinker Pop
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.unfold;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.util.empty.EmptyGraph;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;
import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection;

// Java

// Log4J
import org.apache.log4j.Logger;

/** <code>GremlinClient</code> provides connection to Gramlin Graph.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class GremlinClient {
   
  /** Create with connection parameters.
    * @param hostname The Gremlin hostname.
    * @param table    The Gremlin port. */
  public GremlinClient(String hostname,
                       int    port) {
    Init.init();
    log.info("Opening " + hostname + ":" + port);
    _graph = EmptyGraph.instance();
    try {
      _g = _graph.traversal().withRemote(DriverRemoteConnection.using(hostname, port));
      }
    catch (Exception e) {
      log.error("Cannot open connection", e);
      }
    log.info("Connected");
    }
        
  /** Close graph. */
  public void close() {
    log.info("Closed");
    }
    
  /** Give {@link GraphTraversalSource}.
    * @return {@link GraphTraversalSource}. */
  public GraphTraversalSource g() {
    return _g;
    }
    
  private Graph _graph;
  
  private GraphTraversalSource _g;

  /** Logging . */
  private static Logger log = Logger.getLogger(GremlinClient.class);

  }

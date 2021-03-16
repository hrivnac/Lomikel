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

// Log4J
import org.apache.log4j.Logger;

/** <code>DirectGremlinClient</code> provides direct connection to Gremlin Graph.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class DirectGremlinClient extends GremlinClient {
   
  /** Create with connection parameters, using <em>Gryo</em> serializer.
    * @param hostname The Gremlin hostname.
    * @param table    The Gremlin port. */
  public DirectGremlinClient(String  hostname,
                             int     port) {
    super(hostname, port, true);
    }
   
  @Override
  public void connect() {
    _graph = EmptyGraph.instance();
    try {
      _g = _graph.traversal().withRemote(DriverRemoteConnection.using(cluster()));
      }
    catch (Exception e) {
      log.error("Cannot connect", e);
      }
    log.info("Connected");
    }
       
  /** Close graph. */
  public void close() {
    try {
      _graph.close();
      }
    catch (Exception e) {
      log.warn("Cannot Close graph");
      log.debug("Cannot Close graph", e);
      }
    cluster().close();
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
  private static Logger log = Logger.getLogger(DirectGremlinClient.class);

  }
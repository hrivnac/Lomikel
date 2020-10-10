package com.Lomikel.Januser;

import com.Lomikel.Utils.Init;
import com.Lomikel.Utils.Info;

// Tinker Pop
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.unfold;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.util.empty.EmptyGraph;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;
import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection;

// Java
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;

// Log4J
import org.apache.log4j.Logger;

/** <code>GremlinClient</code> provides connection to Gramlin Graph.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
// TBD: refactor with JanusClient 
public class GremlinClient {
   
  /** Create with connection parameters.
    * @param hostname The Gremlin hostname.
    * @param table    The Gremlin port. */
  public GremlinClient(String hostname,
                       int    port) {
    _hostname = hostname;
    _port     = port;
     }
   
  /** Open graph. */
  // BUG: too many opening
  public GraphTraversalSource open() {
    log.info("Opening connection to " + _hostname + ":" + _port);
    _graph = EmptyGraph.instance();
    try {
      _g = _graph.traversal().withRemote(DriverRemoteConnection.using(_hostname, _port));
      }
    catch (Exception e) {
      log.error("Cannot open connection", e);
      }
    log.info("Connected");
    return _g;
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

  private String _hostname;
  
  private int   _port;
    
  private Graph _graph;
  
  private GraphTraversalSource _g;

  /** Logging . */
  private static Logger log = Logger.getLogger(GremlinClient.class);

  }

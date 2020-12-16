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
import org.apache.tinkerpop.gremlin.structure.io.GraphWriter;
import org.apache.tinkerpop.gremlin.structure.io.IoCore;
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONWriter;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.jsr223.ConcurrentBindings;
import org.apache.tinkerpop.gremlin.groovy.engine.GremlinExecutor;

// Java
import java.util.concurrent.CompletableFuture;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

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
    
  /** Convert {@link GraphTraversal} to its <em>GraphSON</em> representation.
    * @param gt The existing  {@link GraphTraversal}.
    * @return   The JSON representation.
    * @throws   IOException If anything fails. */
  public String toJSON(GraphTraversal gt) throws IOException {
    GraphSONWriter gw = GraphSONWriter.build().create();
    OutputStream os = new ByteArrayOutputStream();
    gw.writeVertices(os, gt, Direction.BOTH);
    return "[" + os.toString() + "]";
    }

  /** Interpret Gremlin String.
    * @param request The Gremlin regurest string (without <tt>g.</tt>.
    * @return        The result {@link GraphTraversal}.
    * @throws Exception If anything goes wrong. */
  // TBD: handle exceptions
  // TBD: shouldn't contain magic number for timeout
  public GraphTraversal interpret(String request) throws Exception {
    log.info("Evaluating " + request);
    ConcurrentBindings cb = new ConcurrentBindings();
    cb.putIfAbsent("g", _g);
    GremlinExecutor ge = GremlinExecutor.build().evaluationTimeout(15000L).globalBindings(cb).create();
    CompletableFuture<Object> evalResult = ge.eval("g." + request);
    return (GraphTraversal)evalResult.get();
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

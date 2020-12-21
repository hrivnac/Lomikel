package com.Lomikel.Januser;

import com.Lomikel.Utils.Init;

// Tinker Pop
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.unfold;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.Edge;
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
import org.apache.tinkerpop.gremlin.driver.Cluster;
import org.apache.tinkerpop.gremlin.driver.ser.Serializers;
import org.apache.tinkerpop.gremlin.driver.MessageSerializer;
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONMapper;
import org.apache.tinkerpop.gremlin.driver.ser.GraphSONMessageSerializerV3d0;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerIoRegistryV3d0;
import org.janusgraph.graphdb.tinkerpop.JanusGraphIoRegistry;
import org.apache.tinkerpop.gremlin.driver.ser.GryoMessageSerializerV3d0;
import org.apache.tinkerpop.gremlin.structure.io.gryo.GryoMapper;

// Java
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.script.SimpleBindings;

// Log4J
import org.apache.log4j.Logger;

/** <code>GremlinClient</code> provides connection to Gremlin Graph.
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
      Map<String, Object>  builderConf = new HashMap<>();
      builderConf.put("serializeResultToString", Boolean.TRUE);
      
      //GraphSONMapper.Builder builder = GraphSONMapper.build()
      GryoMapper.Builder builder = GryoMapper.build()
                                             .addRegistry(TinkerIoRegistryV3d0.instance())
                                             .addRegistry(JanusGraphIoRegistry.instance());
      
                                                     
      //MessageSerializer serializer = new GraphSONMessageSerializerV3d0(builder);
      MessageSerializer serializer = new GryoMessageSerializerV3d0(builder);
      
      serializer.configure(builderConf, null);
      Cluster cluster = Cluster.build(hostname)
                               .port(port)
                               .serializer(serializer)
                               .maxContentLength(655360)
                               .create();
      _g = _graph.traversal().withRemote(DriverRemoteConnection.using(cluster));
      //_g = _graph.traversal().withRemote(DriverRemoteConnection.using(hostname, port));
      log.info("Connected");
      }
    catch (Exception e) {
      log.error("Cannot open connection", e);
      }
    }
           
  /** Close graph. */
  public void close() {
    log.info("Closed");
    }
    
  /** Convert {@link Graph} to its <em>GraphSON</em> representation.
    * @param graph The existing {@link Graph}.
    * @return      The JSON representation.
    * @throws      IOException If anything fails. */
  public String toJSON(Graph graph) throws IOException {
    OutputStream os = new ByteArrayOutputStream();
    _graph.io(IoCore.graphson()).writer()
                                .wrapAdjacencyList(true)
                                .create()
                                .writeGraph(os, graph);
    return os.toString();
    }
    
  /** Convert {@link Vertex} to its <em>GraphSON</em> representation.
    * @param vertex The existing {@link Vertex}.
    * @return       The JSON representation.
    * @throws       IOException If anything fails. */
  public String toJSON(Vertex vertex) throws IOException {
    OutputStream os = new ByteArrayOutputStream();
    _graph.io(IoCore.graphson()).writer()
                                .wrapAdjacencyList(true)
                                .create()
                                .writeVertex(os, vertex, Direction.BOTH);
    return os.toString();
    }
    
  /** Convert {@link List} of {@link Vertex}es to its <em>GraphSON</em> representation.
    * @param vertices The existing {@link List} of {@link Vertex}es.
    * @return         The JSON representation.
    * @throws         IOException If anything fails. */
  public String toJSON(List<Vertex> vertices) throws IOException {
    OutputStream os = new ByteArrayOutputStream();
    _graph.io(IoCore.graphson()).writer()
                                .wrapAdjacencyList(true)
                                .create()
                                .writeVertices(os, vertices.iterator(), Direction.BOTH);
    return os.toString();
    }
    
  /** Interpret Gremlin String.
    * @param request The Gremlin regurest string (without <tt>g.</tt>.
    * @return        The result {@link Graph}.
    * @throws Exception If anything goes wrong. */
  // TBD: handle exceptions
  // TBD: shouldn't contain magic number for timeout
  public Graph interpret(String request) throws Exception {
    log.info("Evaluating " + request);
    ConcurrentBindings cb = new ConcurrentBindings();
    cb.putIfAbsent("g", _g);
    GremlinExecutor ge = GremlinExecutor.build()
                                        .evaluationTimeout(15000L)
                                        .globalBindings(cb)
                                        .create();
    CompletableFuture<Object> evalResult = ge.eval("g." + request + ".bothE().subgraph(\"a\").cap(\"a\").next()", "gremlin-groovy", new SimpleBindings());
    return (Graph)evalResult.get();
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

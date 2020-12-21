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
import org.apache.tinkerpop.gremlin.driver.Client;
import org.apache.tinkerpop.gremlin.driver.ResultSet;
import org.apache.tinkerpop.gremlin.driver.Result;
import org.apache.tinkerpop.shaded.jackson.databind.ObjectMapper;

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
// TBD: reuse (the same) serializer
public class GremlinClient {
   
  /** Create with connection parameters.
    * @param hostname The Gremlin hostname.
    * @param table    The Gremlin port. */
  public GremlinClient(String hostname,
                       int    port) {
    Init.init();
    log.info("Opening " + hostname + ":" + port);
    try {
      Map<String, Object>  builderConf = new HashMap<>();
      builderConf.put("serializeResultToString", Boolean.FALSE);      
      GryoMapper.Builder builder = GryoMapper.build()
                                             .addRegistry(JanusGraphIoRegistry.getInstance());                                                     
      MessageSerializer serializer = new GryoMessageSerializerV3d0(builder);      
      _cluster = Cluster.build()
                        .addContactPoint(hostname)
                        .port(port)
                        .serializer(serializer)
                        .create();
      _client = _cluster.connect().init();
      log.info("Connected");
      }
    catch (Exception e) {
      log.error("Cannot open connection", e);
      }
    }
           
  /** Close graph. */
  public void close() {
    _client.close();
    _cluster.close();
    log.info("Closed");
    }
 
  /** Interpret Gremlin String.
    * @param request The Gremlin regurest string.
    * @return        The {@link List} of {@link Result}s.
    * @throws Exception If anything goes wrong. */
  // TBD: handle exceptions
  public List<Result>	 interpret(String request) throws Exception {
    log.info("Evaluating " + request);
    ResultSet results = _client.submit(request);
    return results.all().get();
    }
    
  /** Interpret Gremlin request as JSON string .
    * @param request The Gremlin reguest string.
    * @return        The {@link Result}s as JSON string .
    * @throws Exception If anything goes wrong. */
  public String interpret2JSON(String request) throws Exception {
    List<Result> results = interpret(request);
    ObjectMapper mapper = GraphSONMapper.build()
                                        .addRegistry(JanusGraphIoRegistry.getInstance())
                                        .create()
                                        .createMapper();
    String json = "[";
    for (Result result : results) {
      json += mapper.writeValueAsString(result.getObject());
      }
    json += "]";
    return json;
    }
    
  private Cluster _cluster;  
    
  private Client _client;

  /** Logging . */
  private static Logger log = Logger.getLogger(GremlinClient.class);

  }

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
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.unfold;
import org.apache.tinkerpop.gremlin.structure.Graph;
import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;
import org.apache.tinkerpop.gremlin.driver.Cluster;
import org.apache.tinkerpop.gremlin.driver.MessageSerializer;
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONMapper;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerIoRegistryV3d0;
import org.janusgraph.graphdb.tinkerpop.JanusGraphIoRegistry;
import org.apache.tinkerpop.gremlin.driver.ser.GryoMessageSerializerV3d0;
import org.apache.tinkerpop.gremlin.structure.io.gryo.GryoMapper;
import org.apache.tinkerpop.gremlin.driver.Client;
import org.apache.tinkerpop.gremlin.driver.ResultSet;
import org.apache.tinkerpop.gremlin.driver.Result;
import org.apache.tinkerpop.shaded.jackson.databind.ObjectMapper;
import org.apache.tinkerpop.gremlin.driver.ser.GraphBinaryMessageSerializerV1;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerIoRegistryV3d0;
import org.apache.tinkerpop.gremlin.structure.io.binary.TypeSerializerRegistry;

// Java
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

// Log4J
import org.apache.log4j.Logger;

/** <code>DirectGremlinClient</code> provides direct connection to Gremlin Graph.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class DirectGremlinClient extends    GremlinClient
                                 implements ModifyingGremlinClient {
   
  /** Create with connection parameters, using <em>Gryo</em> serializer.
    * @param hostname The Gremlin hostname.
    * @param table    The Gremlin port. */
  public DirectGremlinClient(String  hostname,
                             int     port) {
    super(hostname, port);
    }
    
  /** Open with <em>GraphBinary</em> serializer.
    * @param hostname The Gremlin hostname.
    * @param table    The Gremlin port. */
 @Override
  public void open(String hostname,
                   int    port) {
    log.info("Using GraphBinary serializer");
    try {
      Map<String, Object>  conf = new HashMap<>();
      conf.put("serializeResultToString", true);
      List<String> ioRegistries =  new ArrayList<>();
      ioRegistries.add("org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerIoRegistryV3d0");
      ioRegistries.add("org.janusgraph.graphdb.tinkerpop.JanusGraphIoRegistry");
      conf.put("ioRegistries", ioRegistries);
      TypeSerializerRegistry.Builder builder = TypeSerializerRegistry.build();
      MessageSerializer serializer = new GraphBinaryMessageSerializerV1(builder);
      serializer.configure(conf, null);
      createCluster(hostname, port, serializer);
      log.info("Opened");
      }
    catch (Exception e) {
      log.error("Cannot open connection", e);
      }
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
       
  @Override
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
    
  @Override
  public void commit() {
    _graph.tx().commit();
    log.info("Commited");
    }
    
  @Override
  public GraphTraversalSource g() {
    return _g;
    }
    
  private Graph _graph;
  
  private GraphTraversalSource _g;

  /** Logging . */
  private static Logger log = Logger.getLogger(DirectGremlinClient.class);

  }
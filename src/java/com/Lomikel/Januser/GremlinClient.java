package com.Lomikel.Januser;

import com.Lomikel.Utils.Init;

// Tinker Pop
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
    * @param table    The Gremlin port.
    * @param gryo     Whether open <em>Gryo</em> serializer or <em>GraphBinary</em> serializer. */
  public GremlinClient(String  hostname,
                       int     port,
                       boolean gryo) {
    Init.init();
    log.info("Opening " + hostname + ":" + port);
    if (gryo) {
      openGryo(hostname, port);
      }
    else {
      openBinary(hostname, port);
      }
    connect();
    }
   
  /** Open with <em>GraphBinary</em> serializer.
    * @param hostname The Gremlin hostname.
    * @param table    The Gremlin port. */
  public void openBinary(String hostname,
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
      _cluster = Cluster.build()
                        .addContactPoint(hostname)
                        .port(port)
                        .serializer(serializer)
                        .create();
      log.info("Opened");
      }
    catch (Exception e) {
      log.error("Cannot open connection", e);
      }
    }
   
  /** Open with <em>Gryo</em> serializer.
    * @param hostname The Gremlin hostname.
    * @param table    The Gremlin port. */
  public void openGryo(String hostname,
                       int    port) {
    log.info("Using Gryo serializer");
    try {
      GryoMapper.Builder builder = GryoMapper.build()
                                             .addRegistry(JanusGraphIoRegistry.getInstance());                                                     
      MessageSerializer serializer = new GryoMessageSerializerV3d0(builder);      
      _cluster = Cluster.build()
                        .addContactPoint(hostname)
                        .port(port)
                        .serializer(serializer)
                        .create();
      log.info("Opened");
      }
    catch (Exception e) {
      log.error("Cannot open connection", e);
      }
    }
        
  /** Connect client. */
  public abstract void connect();
       
  /** Close client. */
  public abstract void close();
 
    
  /** Give the {@link Cluster}.
    * @return The attached {@link Cluster}. */
  public Cluster cluster() {
    return _cluster;
    }
    
  private Cluster _cluster;  

  /** Logging . */
  private static Logger log = Logger.getLogger(GremlinClient.class);

  }

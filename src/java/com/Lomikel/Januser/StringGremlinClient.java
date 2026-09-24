package com.Lomikel.Januser;

// Tinker Pop
import org.apache.tinkerpop.gremlin.util.MessageSerializer;
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONMapper;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerIoRegistryV3;
import org.janusgraph.graphdb.tinkerpop.JanusGraphIoRegistry;
import org.apache.tinkerpop.gremlin.driver.Client;
import org.apache.tinkerpop.gremlin.driver.ResultSet;
import org.apache.tinkerpop.gremlin.driver.Result;
import org.apache.tinkerpop.shaded.jackson.databind.ObjectMapper;
import org.apache.tinkerpop.gremlin.util.ser.GraphSONMessageSerializerV3;

// Java
import java.util.List;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** Submits complete Gremlin scripts to a remote Gremlin server.
  *
  * <p>This API is deliberately separate from typed traversal recipes: the
  * server evaluates each string and the client converts driver results for
  * callers that explicitly need script submission.</p>
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class StringGremlinClient extends GremlinClient {
   
  /** Create with connection parameters, using the GraphSON serializer.
    * @param hostname The Gremlin hostname.
    * @param port     The Gremlin port. */
  public StringGremlinClient(String  hostname,
                             int     port) {
    super(hostname, port, true);
    initialize(hostname, port);
    }
   
  /** Open with <em>GraphSON</em> serializer.
    * @param hostname The Gremlin hostname.
    * @param port     The Gremlin port. */
  @Override
  public void open(String hostname,
                   int    port) {
    log.info("Using GraphSON serializer");
    try {
      GraphSONMapper.Builder builder = GraphSONMapper.build()
                                                     .addRegistry(TinkerIoRegistryV3.instance())                                                     
                                                     .addRegistry(JanusGraphIoRegistry.instance());
      _mapper = builder.create().createMapper();
      MessageSerializer serializer = new GraphSONMessageSerializerV3(builder);  
      createCluster(hostname, port, serializer);
      log.info("Opened");
      }
    catch (Exception e) {
      throw new IllegalStateException("Cannot open Gremlin connection", e);
      }
    }
    
  @Override
  public void connect() {
    try {
      _client = cluster().connect().init();
      log.info("Connected");
      }
    catch (Exception e) {
      throw new IllegalStateException("Cannot connect Gremlin client", e);
      }
    }
       
  @Override
  public void close() {
    RuntimeException failure = null;
    try {
      if (_client != null) {
        _client.close();
        }
      }
    catch (Exception e) {
      failure = collectCleanupFailure(failure, "Gremlin client", e);
      }
    try {
      if (cluster() != null) {
        cluster().close();
        }
      }
    catch (Exception e) {
      failure = collectCleanupFailure(failure, "Gremlin cluster", e);
      }
    log.info("Closed");
    if (failure != null) {
      throw failure;
      }
    }
 
  /** Interpret Gremlin String.
    * @param request The Gremlin regurest string.
    * @return        The {@link List} of {@link Result}s.
    * @throws Exception If anything goes wrong. */
  // TBD: handle exceptions
  public List<Result>	 interpret(String request) throws Exception {
    log.debug("Evaluating " + request);
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
                                        .addRegistry(JanusGraphIoRegistry.instance())
                                        .create()
                                        .createMapper();
    StringBuffer jsonB = new StringBuffer("[");
    boolean first = true;
    for (Result result : results) {
      if (first) {
        first = false;
        }
      else {
        jsonB.append(",");
        }
      jsonB.append(mapper.writeValueAsString(result.getObject()));
      }
    jsonB.append("]");
    return jsonB.toString();
    }
    
  private Client _client;
       
  private ObjectMapper _mapper;  

  /** Logging . */
  private static Logger log = LogManager.getLogger(StringGremlinClient.class);

  }

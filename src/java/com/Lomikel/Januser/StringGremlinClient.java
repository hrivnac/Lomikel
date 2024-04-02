package com.Lomikel.Januser;

// Tinker Pop
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.unfold;
import org.apache.tinkerpop.gremlin.structure.Graph;
import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;
import org.apache.tinkerpop.gremlin.util.MessageSerializer;
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONMapper;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerIoRegistryV3;
import org.janusgraph.graphdb.tinkerpop.JanusGraphIoRegistry;
import org.apache.tinkerpop.gremlin.driver.Client;
import org.apache.tinkerpop.gremlin.driver.ResultSet;
import org.apache.tinkerpop.gremlin.driver.Result;
import org.apache.tinkerpop.shaded.jackson.databind.ObjectMapper;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerIoRegistryV3;
import org.apache.tinkerpop.gremlin.util.ser.GraphSONMessageSerializerV3;

// Java
import java.util.List;

// Log4J
import org.apache.log4j.Logger;

/** <code>StringGremlinClient</code> provides connection to Gremlin Graph passing Gremlin commands as Strings.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class StringGremlinClient extends GremlinClient {
   
  /** Create with connection parameters, using <em>GraphBinary</em> serializer.
    * @param hostname The Gremlin hostname.
    * @param table    The Gremlin port. */
  public StringGremlinClient(String  hostname,
                             int     port) {
    super(hostname, port);
    }
   
  /** Open with <em>GraphSON</em> serializer.
    * @param hostname The Gremlin hostname.
    * @param table    The Gremlin port. */
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
      log.error("Cannot open connection", e);
      }
    }
    
  @Override
  public void connect() {
    _client = cluster().connect().init();
    log.info("Connected");
    }
       
  @Override
  public void close() {
    _client.close();
    cluster().close();
    log.info("Closed");
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
  private static Logger log = Logger.getLogger(StringGremlinClient.class);

  }

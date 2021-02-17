package com.Lomikel.Januser;

import com.Lomikel.Utils.LomikelException;

import com.Lomikel.Phoenixer.PhoenixClient;

// Tinker Pop
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Graph;

// Java
import java.util.Map;

// Log4J
import org.apache.log4j.Logger;

/** <code>Sertex</code> is a {@link Vertex} with additional properties
  * filled from Phoenix.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Sertex extends Wertex {
   
  /** Dress existing {@link Vertex} with values from Phoenix.
    * @param vertex The original {@link Vertex}. */
  public Sertex(Vertex vertex) {
    super(vertex);
    if (_client == null) {
      log.warn("PhoenixClient is not set, not dressing Vertex as Sertex");
      }
    if (rowkeys() != null && rowkeys().length == rowkeyNames().length) {
      String n = null;
      //Map<String, Map<String, String>> results = _client.scan(rowkey(), n, "*", 0, 0, false, true);
      //Map<String, String> fields = results.get(rowkey());
      //setFields(fields); 
      }
    }
    
  /** Set the {@link PhoenixClient} to search for additional values.
    * It should be set before any creation.
    * @param client The {@link PhoenixClient} to search for additional values. */
  public static void setPhoenixClient(PhoenixClient client) {
    _client = client;
    }
    
  private static PhoenixClient _client;
    
  /** Logging . */
  private static Logger log = Logger.getLogger(Sertex.class);

  }

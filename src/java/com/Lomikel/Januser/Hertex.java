package com.Lomikel.Januser;

import com.Lomikel.Utils.LomikelException;

import com.Lomikel.HBaser.HBaseClient;

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

/** <code>Hertex</code> is a {@link Vertex} with additional properties
  * filled from HBase.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Hertex extends Wertex {
   
  /** Dress existing {@link Vertex} with values from HBase.
    * @param vertex The original {@link Vertex}.
    * @throws LomikelException If anything goes wrong. */
  public Hertex(Vertex vertex) throws LomikelException {
    super(vertex);
    if (_client == null) {
      throw new LomikelException("HBaseClient is not set");
      }
    String n = null;
    Map<String, Map<String, String>> results = _client.scan(rowkey(), n, "*", 0, 0, false, true);
    Map<String, String> fields = results.get(rowkey());
    setFields(fields); 
    }
    
  /** Set the {@link HBaseClient} to search for additional values.
    * It should be set before any creation.
    * @param client The {@link HBaseClient} to search for additional values. */
  public static void setHBaseClient(HBaseClient client) {
    _client = client;
    }
    
  private static HBaseClient _client;
    
  /** Logging . */
  private static Logger log = Logger.getLogger(Hertex.class);

  }

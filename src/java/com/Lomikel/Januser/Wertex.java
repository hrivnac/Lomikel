package com.Lomikel.Januser;

import com.Lomikel.Utils.LomikelException;

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
import java.util.TreeMap;
import java.util.Iterator;
import java.util.Arrays;

// Log4J
import org.apache.log4j.Logger;

/** <code>Wertex</code> is a {@link Vertex} with additional properties
  * filled from the aux database.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Wertex implements Vertex {
   
  /** Dress existing {@link Vertex} with values from the database.
    * @param vertex The original {@link Vertex}. */
  public Wertex(Vertex vertex) {
    _vertex  = vertex;
    _rowkeys = null;
    if (_rowkeyNames == null || _rowkeyNames.length == 0) {
      log.warn("rowkey names not set, not dressing Vertex as Wertex");
      return;
      }
    // TBD: should be done without Exception 
    try {
      _rowkeys = new String[_rowkeyNames.length];
      for (int i = 0; i < _rowkeyNames.length; i++) {
        _rowkeys[i] = vertex.<String>property(_rowkeyNames[i]).value();
        }
      }
    catch (IllegalStateException e) {
      log.warn(Arrays.toString(_rowkeyNames) + " not set, not dressing Vertex as Wertex");
      }
    }
    
  /** Set the names of the row keys to form the correspondence between
    * Graph and database. It should be set before any database request.
    * @param rowkeyNames The names of the common keys in Graph {@link Vertex} and 
    *                    database. */
  public static void setRowkeyNames(String[] rowkeyNames) {
    _rowkeyNames = rowkeyNames;
    }
    
  /** Set the names of the first (and only) row key to form the correspondence between
    * Graph and database. It should be set before any database request.
    * @param rowkeyName The name of the first common keys in Graph {@link Vertex} and 
    *                   database. */
  public static void setRowkeyName(String rowkeyName) {
    _rowkeyNames = new String[]{rowkeyName};
    }
    
  /** Give the names of the row keys to form the correspondence between
    * Graph and database.
    * @return The names of the common keys in Graph {@link Vertex} and 
    *         database. */
  public static String[] rowkeyNames() {
    return _rowkeyNames;
    }
    
  /** Give the name of the first row key to form the correspondence between
    * Graph and database.
    * @return The names of the first common key in Graph {@link Vertex} and 
    *         database. */
  public static String rowkeyName() {
    if (_rowkeyNames.length == 0) {
      log.error("RowkeyNames not set");
      return null;
      }
    else if (_rowkeyNames.length > 1) {
      log.warn("Rowkeynames length " + _rowkeyNames.length + " > 1, the furst one returned");
      }
    return _rowkeyNames[0];
    }
    
  @Override
  public Edge addEdge(String label, Vertex inVertex, Object... keyValues) {
    return _vertex.addEdge(label, inVertex, keyValues);
    }
    
  @Override  
  public Iterator<Edge> edges(Direction direction, String... edgeLabels) {
    return _vertex.edges(direction, edgeLabels);
    }
    
  @Override
  public Iterator<Vertex>	 vertices(Direction direction, String... edgeLabels) {
    return _vertex.vertices(direction, edgeLabels);
    }
    
  @Override
  public <V> Iterator<VertexProperty<V>>	properties(String... propertyKeys) {
    return _vertex.properties(propertyKeys);
    }
    
  @Override
  public Graph	graph() {
    return _vertex.graph();
    }
    
  @Override
  public Object	id() {
    return _vertex.id();
    }
    
  @Override
  public String	label() {
    return _vertex.label();
    }

  @Override
  public <V> VertexProperty<V>	property(VertexProperty.Cardinality cardinality, String key, V value, Object... keyValues) {
    return _vertex.property(cardinality, key, value, keyValues);
    }

  @Override
  public void	remove() {
    _vertex.remove();
    }
    
  /** Give the link rowkeys.
    * @return The link rowkeys. */
  public String[] rowkeys() {
    return _rowkeys;
    }
    
  /** Give the the first link rowkey.
    * @return The first link rowkey. */
  public String rowkey() {
    if (_rowkeys.length == 0) {
      log.error("Rowkey not set");
      return null;
      }
    else if (_rowkeys.length > 1) {
      log.warn("Rowkeys length " + _rowkeys.length + " > 1, the furst one returned");
      }
    return _rowkeys[0];
    }
    
  /** Tell if there is a valid and single rowkey.
    * @return Whether there is a valid and single rowkey.
    *         Non-existent or longer rowkey will give <tt>false</tt>. */
  public boolean hasSingleRowkey() {
    return (_rowkeyNames       != null && _rowkeys       != null &&
            _rowkeyNames.length == 1   && _rowkeys.length == 1);
    }
        
  /** Set all field values read from the database.
    * @param fields The key-value pairs from the database. */
  protected void setFields(Map<String, String> fields) {
    for (Map.Entry<String, String> entry : fields.entrySet()) {
      property(entry.getKey(), entry.getValue());
      }
    }
   
  private Vertex _vertex;  
  
  private String[] _rowkeys;
  
  private static String[] _rowkeyNames;

  /** Logging . */
  private static Logger log = Logger.getLogger(Wertex.class);

  }

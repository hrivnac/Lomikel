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
import java.util.Iterator;

// Log4J
import org.apache.log4j.Logger;

/** <code>Sertex</code> is a {@link Vertex} with additional properties
  * filled from the aux database.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Sertex implements Vertex {
   
  /** Dress existing {@link Vertex} with values from the database.
    * @param vertex The original {@link Vertex}.
    * @throws LomikelException If anything goes wrong. */
  public Sertex(Vertex vertex) throws LomikelException {
    _vertex = vertex;
    if (_rowkeyName == null) {
      throw new LomikelException("rowkey name is not set");
      }
    _rowkey = vertex.<String>property(_rowkeyName).value();
    }
    
  /** Set the name of the row key to form the correspondence between
    * Graph and database. It should be set before any database request.
    * @param rowkeyName The name of the common key in Graph {@link Vertex} and 
    *                   database. */
  public static void setRowkeyName(String rowkeyName) {
    _rowkeyName = rowkeyName;
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
    
  /** Give the link rowkey.
    * @return The link rowkey. */
  public String rowkey() {
    return _rowkey;
    }
    
  /** Give the field value from the database.
    * @param key The database field name.
    * @return    The correspoinding value from the database. */
  public String field(String key) {
    return _fields.get(key);
    }
    
  /** Give all field values from the database.
    * @return The key-value pairs from the database. */
  public Map<String, String> fields() {
    return _fields;
    }
    
  /** Set all field values read from the database.
    * @param fields The key-value pairs from the database. */
  protected void setFields(Map<String, String> fields) {
    _fields = fields;
    }
    
  private Map<String, String> _fields;
    
  private Vertex _vertex;  
  
  private String _rowkey;
  
  private static String _rowkeyName;

  /** Logging . */
  private static Logger log = Logger.getLogger(Sertex.class);

  }

package com.Lomikel.Januser;

// Tinker Pop
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.Property;

// Java
import java.util.Iterator;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>VertexField</code> is a {@link VertexProperty} with properties
  * filled from the aux database.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
// BUG: some methods are not implemented
public class VertexField implements VertexProperty<String> {

  /** Create.
    * @param key The database key.
    * @param value The database value.
    * @param vertex The associated {@link Vertex}. */
  public VertexField(String key,
                     String value,
                     Vertex vertex) {
    _key    = key;
    _value  = value;
    _vertex = vertex;
    }
  
  @Override
  public Iterator<Property<String>> properties(String... propertyKeys) {
    return null;
    }
    
  @Override
  public Vertex element() {
    return _vertex;
    }
    
  @Override
  public void remove() {
    }
    
  @Override
  public boolean isPresent() {
    return true;
    }
    
  @Override
  public String value() {
    return _value;
    }
    
  @Override
  public String key() {
    return _key;
    }
    
  @Override
  public <U> Property<U>	property(String key, U value) {
    return null;
    }
    
  @Override
  public Object id() {
    return null;
    }
    
  @Override
  public String toString() {
    return "vf[" + _key + "->" + _value + "]";
    }
    
  private String _key;
  
  private String _value;
  
  private Vertex _vertex;
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(VertexField.class);

  }

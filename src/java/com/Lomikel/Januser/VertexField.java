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
    
  private String _key;
  
  private String _value;
  
  private Vertex _vertex;
  
  /** Logging . */
  private static Logger log = Logger.getLogger(VertexField.class);

  }

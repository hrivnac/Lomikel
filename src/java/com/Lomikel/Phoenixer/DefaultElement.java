package com.Lomikel.Phoenixer;

import com.Lomikel.Januser.JanusClient;
import com.Lomikel.Utils.Coding;
import com.Lomikel.Utils.LomikelException;

// Tinker Pop
import org.apache.tinkerpop.gremlin.structure.Vertex;
//import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.unfold;

// Java
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// Log4J
import org.apache.log4j.Logger;

/** <code>DefaultElement</code> implements stored object.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public abstract class DefaultElement implements Element {
  
  @Override
  public abstract Map<String, String> schema();

  @Override
  public abstract String[] keyColumns();
  
  @Override
  public abstract String phoenixTable();
  
  @Override
  // TBD: conflicts ?
  public void merge(Element other) {
    _content.putAll(other.content());
    }

  // Content -------------------------------------------------------------------
    
  @Override
  public Map<String, Object> content() {
    return _content;
    }
  
  @Override
  // TBD: check schema
  public <E> Element set(String name,
                         E      object) {
    if (object != null && !object.toString().trim().equals("")) {
      _content.put(name, object);
      }
    return this;
    }
    
  @Override
  // TBD: check schema
  public <E> E get(String name,
                   E      object) {
    if (! _content.containsKey(name)) {
      object = null;
      return null;
      }
    object = (E)_content.get(name);
    return object;
    }
    
  @Override
  // TBD: check schema
  public String get(String name) {
    if (! _content.containsKey(name)) {
      return null;
      }
    return _content.get(name).toString();
    }
    
  // Graph ---------------------------------------------------------------------
  
  @Override
  public Vertex vertex() throws LomikelException {
    if (key() == null) {
      throw new LomikelException("Unknown key");
      }
    String label = simpleName().toLowerCase();
    Vertex vertex = janusClient().getOrCreate(label, "phoenixkey", Coding.compactKey(key()));
    String k;
    String v;
    Object o;
    for (Map.Entry<String, String> entry : schema().entrySet()) {
      k = entry.getKey();
      v = entry.getValue();
      o = get(k);
      if (o != null) {
        vertex.property(k, o);
        }
      }
    janusClient().commit();  
    return vertex;
    }
    
  // ---------------------------------------------------------------------------
  
  @Override
  public abstract <E extends Element> E emptyClone();

  @Override
  public String[] key() {
    String[] k = new String[keyColumns().length];
    String kk;
    int i = 0;
    for (String c : keyColumns()) {
      kk = get(c);
      if (kk == null) {
        return null;
        }
      k[i++] = kk;
      }
    return k;
    }
  
  @Override
  public void setKey(String[] key) {
    int i = 0;
    for (String c : keyColumns()) {
      _content.put(c, key[i++]);
      }
    }
    
  @Override
  public <E extends Element> E onlyKey() {
    E element = emptyClone();
    for (String keyColumn : keyColumns()) {
      if (_content.containsKey(keyColumn)) {
        element.set(keyColumn, _content.get(keyColumn));
        }
      }
    return element;
    }
 
  @Override
  public String toString(Format format) {
    String result = "Element():\n";
    for (Map.Entry<String, Object> entry : content().entrySet()) {
      result += "\t" + entry.getKey() + " = " + entry.getValue() + "\n";
      }
    return result;
    }
  
  @Override
  public String toString() {
    return "Element()";
    }
    
  /** Give {@link Object} simple class name.
    * @return       The {@link Object} simple class name. */
  public String simpleName() {
    return getClass().getSimpleName();
    }
    
  /** Give the hosting {@link JanusClient}.
    * @return The hosting {@link JanusClient}. */
  public static JanusClient janusClient() {
    return _janus;
    }
    
  /** Set the hosting {@link JanusClient}.
    * @param jc The hosting {@link JanusClient}. */
  public static void setJanusClient(JanusClient janus) {
    _janus = janus;
    }
    
  private Map<String, Object> _content = new TreeMap<>();
  
  private static JanusClient _janus = new JanusClient();

  /** Logging . */
  private static Logger log = Logger.getLogger(DefaultElement.class);
       
  }

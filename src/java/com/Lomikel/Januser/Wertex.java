package com.Lomikel.Januser;

import com.Lomikel.DB.Client;

// Tinker Pop
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.Graph;
import  org.janusgraph.core.SchemaViolationException;

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
// TBD: allow partial filling at runtime
public abstract class Wertex implements Vertex {
   
  /** Dress existing {@link Vertex} with values from the database.
    * Fill in all fields from the database.
    * @param vertex The original {@link Vertex}. */
  public Wertex(Vertex vertex) {
    this(vertex, null);
    }
   
  /** Dress existing {@link Vertex} with values from the database.
    * @param vertex The original {@link Vertex}.
    * @param fields The fields to fill in from the database.
    *               All fields will be filled in if <tt>null</tt>. */
  public Wertex(Vertex   vertex,
                String[] fields) {
    _vertex  = vertex;
    _fields  = fields;
    String[] rowkeyNames = rowkeyNames();
    _rowkeys = null;
    if (rowkeyNames == null || rowkeyNames.length == 0) {
      log.warn("rowkey names not set, not dressing Vertex as Wertex");
      return;
      }
    // TBD: should be done without Exception 
    try {
      _rowkeys = new String[rowkeyNames.length];
      for (int i = 0; i < rowkeyNames.length; i++) {
        //_rowkeys[i] = vertex.<String>property(_rowkeyNames[i]).value();
        _rowkeys[i] = vertex.property(rowkeyNames[i]).value().toString(); // TBD: proper types
        }
      }
    catch (IllegalStateException e) {
      log.warn(Arrays.toString(rowkeyNames) + " not set, not dressing Vertex as Wertex");
      }
    }
    
  /** Set the names of the row keys to form the correspondence between
    * Graph and database. It should be set before any database request.
    * @param lbl         The {@link Vertex} label.
    * @param cl          The {@link Class} representing this {@link Wertex}.
    * @param rowkeyNames The names of the common keys in Graph {@link Vertex} and 
    *                    database. */
  public static void setRowkeyNames(String   lbl,
                                    Class    cl,
                                    String[] rowkeyNames) {
    log.info("Setting rowkey names for " + cl.getCanonicalName() + ": " + String.join(", ", rowkeyNames));
    _representants.put(lbl, cl.getCanonicalName()); 
    _rowkeyNames.put(cl.getCanonicalName(), rowkeyNames);
    }
    
  /** Set the names of the only row key to form the correspondence between
    * Graph and database. It should be set before any database request.
    * @param lbl         The {@link Vertex} label.
    * @param cl         The {@link Class} representing this {@link Wertex}.
    * @param rowkeyName The name of the common keys in Graph {@link Vertex} and 
    *                   database. */
  public static void setRowkeyName(String lbl,
                                   Class  cl,
                                   String rowkeyName) {
    log.info("Setting rowkey name for " + cl.getCanonicalName() + ": " + rowkeyName);
    _representants.put(lbl, cl.getCanonicalName()); 
    _rowkeyNames.put(cl.getCanonicalName(), new String[]{rowkeyName});
    }
    
  /** Give the Canonical Class Name of the {@link Class} representing a <em>label</em>.
    * @param lbl The <em>label</em> to be represented.
    * @return    The representing Canonical Class Name. */
  public static String representant(String lbl) {
    return _representants.get(lbl);
    }
    
  /** Give the names of the keys to form the correspondence between
    * Graph and database.
    * @return The names of the common keys in Graph {@link Vertex} and 
    *         database. */
  public String[] rowkeyNames() {
    return rowkeyNames(getClass().getCanonicalName());
    }
    
  /** Give the names of the keys to form the correspondence between
    * Graph and database.
    * @param representant The representing Canonical Class Name.
    * @return             The names of the common keys in Graph {@link Vertex} and 
    *                     database. */
  public static String[] rowkeyNames(String representant) {
    return _rowkeyNames.get(representant);
    }
    
  /** Give the name of the keys to form the correspondence between
    * Graph and database.
    * @return The names of the common keys in Graph {@link Vertex} and 
    *         database.
    *         <tt>null</tt> if not set.
    *         Concatenated with <tt>#</tt> if multivalue. */
  public String rowkeyName() {
    return rowkeyName(getClass().getCanonicalName());
    }
    
  /** Give the name of the keys to form the correspondence between
    * Graph and database.
    * @param representant The representing Canonical Class Name.
    * @return             The names of the common keys in Graph {@link Vertex} and 
    *                     database.
    *                     <tt>null</tt> if not set.
    *                     Concatenated with <tt>#</tt> if multivalue. */
  public static String rowkeyName(String representant) {
    if (_rowkeyNames.get(representant).length == 0) {
      log.error("RowkeyName not set");
      return null;
      }
    return String.join("#", _rowkeyNames.get(representant));
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
    
  /** Give the the link rowkey.
    * @return The link rowkey.
    *         <tt>null</tt> if not set.
    *         Concatenated with <tt>#</tt> if multivalue. */
  public String rowkey() {
    if (_rowkeys.length == 0) {
      log.error("Rowkey not set");
      return null;
      }
    return String.join("#", _rowkeys);
    }
        
  /** Set all field values read from the database.
    * @param fields The key-value pairs from the database. */
  protected void setFields(Map<String, String> fields) {
    setFields(fields, null);
    }
        
  /** Set all field values read from the database.
    * @param fields The key-value pairs from the database.
    * @param prefix The prefix to add (together with <tt>:</tt>) to the filed names. */
  protected void setFields(Map<String, String> fields,
                           String              prefix) {
    if (prefix == null || prefix.trim().equals("")) {
      prefix = "";
      }
    else {
      prefix = prefix.trim() + ":";
      }
    if (_fields != null) {
      String value;
      for (String field : _fields) {
        value = fields.get(field);
        if (value == null) {
          log.error("" + field + " missing, not filled");
          }
        else {
          property(prefix + field, value);
          }
        }
      }
    else if (fields != null && !fields.isEmpty()) {
      for (Map.Entry<String, String> entry : fields.entrySet()) {
        try {
          property(prefix + entry.getKey(), entry.getValue());
          }
        catch (SchemaViolationException e) {
          log.warn("Cannot add " + entry.getKey() + " -> " + entry.getValue(), e);
          }
        }
      }
    }
    
  /** Give the associated {@link Client}.
    * @return The associated {@link Client}. */
  public abstract Client client();
  
  /** Enhance {@link Vertex} with properties from HBase and Phoenix databases.
    * @param  @vertex The {@link Vertex} to be enhanced.
    * @return         The enhanced {@link Vertex}, if possible. */
  public static Vertex enhance(Vertex vertex) {
    vertex = Hertex.enhance(vertex);
    vertex = Sertex.enhance(vertex);
    return vertex;
    }

  private Vertex _vertex;  
  
  private String[] _rowkeys;
  
  private String[] _fields;
  
  private static Map<String, String[]> _rowkeyNames = new TreeMap<>();
  
  private static Map<String, String> _representants = new TreeMap<>();

  /** Logging . */
  private static Logger log = Logger.getLogger(Wertex.class);

  }

package com.Lomikel.Januser;

import com.Lomikel.DB.Client;
import com.Lomikel.Phoenixer.PhoenixClient;
import com.Lomikel.Phoenixer.PhoenixSchema;

// Tinker Pop
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;

// Java
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

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
    * @param vertex The original {@link Vertex}.
    * @param fields The coma-separated list of fields to fill in from the database.
    *               All fields will be filled in if <tt>null</tt>. */
  public Sertex(Vertex vertex,
                String fields) {
    this(vertex, fields == null ? null : fields.split(","));
    }
   
  /** Dress existing {@link Vertex} with values from Phoenix.
    * @param vertex The original {@link Vertex}.
    * @param fields The fields to fill in from the database.
    *               All fields will be filled in if <tt>null</tt>. */
  public Sertex(Vertex   vertex,
                String[] fields) {
    super(vertex);
    if(property("phoenix").isPresent()) {
      log.info("Fields already filled");
      return;
      }
    if (_client == null) {
      log.warn("PhoenixClient is not set");
      return;
      }
    if (fields != null && fields.length == 0) {
      log.info("No fields are set");
      return;
      }
    if (rowkeys() != null && rowkeys().length == rowkeyNames().length) { // TBD: error if not
      Map<String, String> searchMap = new HashMap<>();
      for (int i = 0; i < rowkeyNames().length; i++) {
        searchMap.put(rowkeyNames()[i], rowkeys()[i]);
        }
      Map<String, Map<String, String>> results = _client.scan(null, searchMap, "*", 0, 0, false, true);
      if (results != null && !results.isEmpty()) {
        property("phoenix", true);
        }
      Map<String, String> allFields = results.get(rowkey());
      Map<String, String> fields2fill;
      if (fields == null) {
        log.info("Filling all fields");
        fields2fill = allFields;
        }
      else {
        fields2fill = new HashMap<>();
        for (String field : fields) {
          if (!field.trim().equals("")) {
            if (!allFields.containsKey(field)) {
              log.warn("Cannot get " + field + " from " + allFields);
              }
            else {
              fields2fill.put(field, allFields.get(field));
              }
            }
          }
        }    
      setFields(fields2fill, null); 
      }
    }
    
  /** TBD */
  public List<String[]> lookUp(String[] rowkeys) {
    List<String[]> existingRowkeys = new ArrayList<>();
    if (rowkeys != null && rowkeys.length == rowkeyNames().length) { // TBD: error if not
      Map<String, String> searchMap = new HashMap<>();
      for (int i = 0; i < rowkeyNames().length; i++) {
        searchMap.put(rowkeyNames()[i], rowkeys[i]);
        }
      String filter = String.join(",", rowkeys);
      Map<String, Map<String, String>> results = _client.scan(null, searchMap, filter, 0, 0, false, true);
      if (results != null && !results.isEmpty()) {
        for (Map.Entry<String, Map<String, String>> entry : results.entrySet()) {
          log.info(entry.getValue());
          }
        }
      }
    return existingRowkeys;
    }
    
  /** Set the {@link PhoenixClient} to search for additional values.
    * It should be set before any creation.
    * @param client The {@link PhoenixClient} to search for additional values. */
  public static void setPhoenixClient(PhoenixClient client) {
    _client = client;
    }
    
  @Override
  public Client client() {
    return _client;
    }
    
  /** Set the schema to form the correspondence between
    * Graph and database. It should be set before any database request.
    * @param lbl         The {@link Vertex} label.
    * @param cl          The {@link Class} representing this {@link Wertex}.
    * @param rowkeyNames The names of the common keys in Graph {@link Vertex} and 
    *                    database. */
  public static void setSchema(String        lbl,
                               Class         cl,
                               PhoenixSchema schema) {
    log.info("Setting schema for " + cl.getCanonicalName() + ": " + schema);
    _schemas.put(lbl, schema);
    setRowkeyNames(lbl, cl, schema.rowkeyNames());
    }
    
  /** TBD */
  public static PhoenixSchema schema(String lbl) {
    return _schemas.get(lbl);
    }
  
  /** Enhance {@link Vertex} with properties from Phoenix database.
    * @param  @vertex The {@link Vertex} to be enhanced.
    * @param fields The coma-separated list of fields to fill.
    *               <tt>null</tt> will fill all fields.
    *               Empty String will fill nothing besides rowkey fields.
    * @return       The enhanced {@link Vertex}, if possible. */
  public static Vertex enhance(Vertex vertex,
                               String fields) {
    if (_client == null) {
      log.warn( "Cannot enhance, no client");
      return vertex;
      }
    if (vertex.property("lbl") == null) {
      log.warn( "Cannot enhance, no label");
      return vertex;
      }
    Class cl = _client.representation(vertex.property("lbl").value().toString());
    if (cl == null) {
      log.warn( "Cannot enhance, cannot get enhancing Class");
      return vertex;
      }
    try {
      Constructor constructor = cl.getConstructor(new Class[]{Vertex.class, String.class});
      Vertex newVertex = (Vertex)constructor.newInstance(vertex, fields);
      return newVertex;
      }
    catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
      log.warn( "Cannot enhance as " + cl);
      log.debug("Cannot enhance as " + cl, e);
      return vertex;
      }
    }
     
  /** Get {@link Vertex} backuped by <em>Phoenix</em>
    * from the <em>JanusGraph</em>, or create if it doesn't exist yet.
    * @param lbl     The {@link Vertex} label.
    * @param rowkey  The {@link Vertex} <tt>rowkeys</tt> value. Their names are taken from the schema.
    * @param g        The {@link GraphTraversalSource} to be used to execute operations.
    * @param enhance Whether enhance all values from the <em>Phoenix</em>.
    * @return        The created {@link Vertex}es. It will be created even when no corresponding
    *                entry exists in the <em>Phoenix</em>. In that case, it can be enhanced later. */
  public static List<Vertex> getOrCreate(String                 lbl,
                                         String[]               rowkeys,
                                         GraphTraversalSource   g,
                                         boolean                enhance) {
    return getOrCreate(lbl, rowkeys, g, enhance ? null : "");
    }
    
  /** Get {@link Vertex} backuped by <em>Phoenix</em>
    * from the <em>JanusGraph</em>, or create if it doesn't exist yet.
    * @param lbl     The {@link Vertex} label.
    * @param rowkey  The {@link Vertex} <tt>rowkeys</tt> value. Their names are taken from the schema.
    * @param g       The {@link GraphTraversalSource} to be used to execute operations.
    * @param fields  The coma-separated list of fields to fill.
    *                <tt>null</tt> will fill all fields.
    *                Empty String will fill nothing besides rowkey fields.
    * @return        The created {@link Vertex}es. It will be created even when no corresponding
    *                entry exists in the <em>Phoenix</em>. In that case, it can be enhanced later.
    *                      If multiple {@link Vertex}es exist, only thee first one is given. */
  // TBD: more user-riendly rowkeys
  public static List<Vertex> getOrCreate(String                 lbl,
                                         String[]               rowkeys,
                                         GraphTraversalSource   g,
                                         String                 fields) {
    List<Vertex> vertexes = new GremlinRecipies(g).getOrCreate(lbl, rowkeyNames(representant(lbl)), rowkeys);
    List<Vertex> newVertexes = new ArrayList<>();
    for (Vertex v : vertexes) {
      newVertexes.add(enhance(v, fields));
      }
    return newVertexes;
    }
   
  //@Override
  //public String toString() {
  //  String msg = "Vertex backed up with " + _client;
  //  if (_client != null) {
  //    msg += "\tenhancing representations: " + _client.representations();
  //    }
  //  msg += "\n\t" + super.toString();
  //  return msg;
  //  }
    
  private static PhoenixClient _client;
  
  private static Map<String, PhoenixSchema> _schemas = new TreeMap<>();
    
  /** Logging . */
  private static Logger log = Logger.getLogger(Sertex.class);

  }

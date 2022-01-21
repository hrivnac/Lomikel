package com.Lomikel.Januser;

import com.Lomikel.DB.Client;
import com.Lomikel.HBaser.HBaseClient;

// Tinker Pop
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;

// Java
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

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
    * @param fields The coma-separated list of fields to fill in from the database.
    *               All fields will be filled in if <tt>null</tt>. */
  public Hertex(Vertex vertex,
                String fields) {
    this(vertex, fields == null ? null : fields.split(","));
    }
  
  /** Dress existing {@link Vertex} with values from HBase.
    * @param vertex The original {@link Vertex}.
    * @param fields The fields to fill in from the database.
    *               All fields will be filled in if <tt>null</tt>. */
  // TBD: refactor with Sertex
  public Hertex(Vertex   vertex,
                String[] fields) {
    super(vertex, fields);
    if (_client == null) {
      log.warn("HBaseClient is not set, not dressing Vertex as Hertex");
      return;
      }
    if (rowkey() != null) {
      String n = null;
      Map<String, Map<String, String>> results = _client.scan(rowkey(), n, "*", 0, 0, false, true);
      if (!results.isEmpty()) {
        property("hbase", true);
        }
      Map<String, String> allFields = results.get(rowkey());
      Map<String, String> fields2fill;
      if (fields == null) {
        fields2fill = allFields;
        }
      else {
        fields2fill = new HashMap<>();
        for (String field : fields) {
          if (!allFields.containsKey(field)) {
            log.warn("Cannot get " + field + " from " + allFields);
            }
          else {
            fields2fill.put(field, allFields.get(field));
            }
          }
        }    
      setFields(fields2fill, null); 
      }
    }
    
  /** Set the {@link HBaseClient} to search for additional values.
    * It should be set before any creation.
    * @param client The {@link HBaseClient} to search for additional values. */
  public static void setHBaseClient(HBaseClient client) {
    _client = client;
    }
    
  @Override
  public Client client() {
    return _client;
    }
  
  /** Enhance {@link Vertex} with properties from HBase database.
    * @param vertex The {@link Vertex} to be enhanced.
    * @param fields The coma-separated list of fields to fill.
    *               <tt>null</tt> will fill all fields.
    *               Empty String will fill nothing besides rowkey fields.
    * @return       The enhanced {@link Vertex}, if possible. */
  public static Vertex enhance(Vertex vertex,
                               String fields) {
    if (_client == null) {
      log.warn( "Cannot enhance");
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
      log.warn( "Cannot enhance as " + cl, e);
      log.debug("Cannot enhance as " + cl);
      return vertex;
      }
    }
   
  /** Get {@link Vertex} backuped by <em>HBase</em>
    * from the <em>JanusGraph</em>, or create if it doesn't exist yet.
    * @param lbl     The {@link Vertex} label.
    * @param rowkey  The {@link Vertex} <tt>rowkey</tt> value. Its name is taken from the schema.
    * @param g       The {@link GraphTraversalSource} to be used to execute operations.
    * @param enhance Whether enhance all values from the <em>HBase</em>.
    * @return        The created {@link Vertex}. It will be created even when no corresponding
    *                entry exists in the <em>HBase</em>. In that case, it can be enhanced later. */
  public static List<Vertex> getOrCreate(String                 lbl,
                                         String                 rowkey,
                                         GraphTraversalSource   g,
                                         boolean                enhance) {
    return getOrCreate(lbl, rowkey, g, enhance ? null : "");
    }
   
  /** Get {@link Vertex} backuped by <em>HBase</em>
    * from the <em>JanusGraph</em>, or create if it doesn't exist yet.
    * @param lbl     The {@link Vertex} label.
    * @param rowkey  The {@link Vertex} <tt>rowkey</tt> value. Its name is taken from the schema.
    * @param g       The {@link GraphTraversalSource} to be used to execute operations.
    * @param fields  The coma-separated list of fields to fill.
    *                <tt>null</tt> will fill all fields.
    *                Empty String will fill nothing besides rowkey fields.
    * @return        The created {@link Vertex}. It will be created even when no corresponding
    *                entry exists in the <em>HBase</em>. In that case, it can be enhanced later. */
  public static List<Vertex> getOrCreate(String                 lbl,
                                         String                 rowkey,
                                         GraphTraversalSource   g,
                                         String                 fields) {
    List<Vertex> vertexes = new GremlinRecipies(g).getOrCreate(lbl, rowkeyName(representant(lbl)), rowkey);
    List<Vertex> newVertexes = new ArrayList<>();
    for (Vertex v : vertexes) {
      newVertexes.add(enhance(v, fields));
      }
    return vertexes;
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
     
  private static HBaseClient _client;
    
  /** Logging . */
  private static Logger log = Logger.getLogger(Hertex.class);

  }

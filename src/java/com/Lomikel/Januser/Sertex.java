package com.Lomikel.Januser;

import com.Lomikel.DB.Client;
import com.Lomikel.Phoenixer.PhoenixClient;

// Tinker Pop
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;

// Java
import java.util.Map;
import java.util.HashMap;
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
    * Fill in all fields from the database.
    * @param vertex The original {@link Vertex}. */
  public Sertex(Vertex vertex) {
    super(vertex, null);
    }
   
  /** Dress existing {@link Vertex} with values from Phoenix.
    * @param vertex The original {@link Vertex}.
    * @param fields The fields to fill in from the database.
    *               All fields will be filled in if <tt>null</tt>. */
  public Sertex(Vertex   vertex,
                String[] fields) {
    super(vertex);
    if (_client == null) {
      log.warn("PhoenixClient is not set, not dressing Vertex as Sertex");
      }
    if (rowkeys() != null && rowkeys().length == rowkeyNames().length) {
      Map<String, String> searchMap = new HashMap<>();
      for (int i = 0; i < rowkeyNames().length; i++) {
        searchMap.put(rowkeyNames()[i], rowkeys()[i]);
        }
      Map<String, Map<String, String>> results = _client.scan(null, searchMap, "*", 0, 0, false, true);
    if (results != null && !results.isEmpty()) {
      property("phoenix", true);
      }
      setFields(results.get(rowkey()), "phoenix"); 
      }
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
  
  /** Enhance {@link Vertex} with properties from Phoenix database.
    * @param  @vertex The {@link Vertex} to be enhanced.
    * @return         The enhanced {@link Vertex}, if possible. */
  public static Vertex enhance(Vertex vertex) {
    if (_client == null || vertex.property("lbl") == null) {
      log.warn( "Cannot enhance, no client or label");
      return vertex;
      }
    Class cl = _client.representation(vertex.property("lbl").value().toString());
    if (cl == null) {
      log.warn( "Cannot enhance, cannot get enhancing Class");
      return vertex;
      }
    try {
      Constructor constructor = cl.getConstructor(new Class[]{Vertex.class});
      Vertex newVertex = (Vertex)constructor.newInstance(vertex);
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
    * @param g       The {@link GraphTraversalSource} to be used to execute operations.
    * @param enhance Whether enhance all values from the <em>Phoenix</em>.
    * @return        The created {@link Vertex}. It will be created even when no corresponding
    *                entry exists in the <em>Phoenix</em>. In that case, it can be enhanced later. */
  public static Vertex getOrCreate(String                 lbl,
                                   String[]               rowkeys,
                                   GraphTraversalSource   g,
                                   boolean                enhance) {
    Vertex v = new GremlinRecipies(g).getOrCreate(lbl, rowkeyNames(representant(lbl)), rowkeys);
    if (enhance) {
      v = enhance(v);
      }
    return v;
    }
   
  @Override
  public String toString() {
    String msg = "Vertex backed up with " + _client;
    if (_client != null) {
      msg += "\tenhancing representations: " + _client.representations();
      }
    return msg;
    }
    
  private static PhoenixClient _client;
    
  /** Logging . */
  private static Logger log = Logger.getLogger(Sertex.class);

  }

package com.Lomikel.Januser;

import com.Lomikel.DB.Client;
import com.Lomikel.Phoenixer.PhoenixClient;

// Tinker Pop
import org.apache.tinkerpop.gremlin.structure.Vertex;

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
      setFields(results.get(rowkey())); 
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
      return vertex;
      }
    Class cl = _client.representation(vertex.property("lbl").value().toString());
    if (cl == null) {
      return vertex;
      }
    try {
      Constructor constructor = cl.getConstructor(new Class[]{Vertex.class});
      Vertex newVertex = (Vertex)constructor.newInstance(vertex);
      return newVertex;
      }
    catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
      log.error("Cannot enhance as " + cl, e);
      return vertex;
      }
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

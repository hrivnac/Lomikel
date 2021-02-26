package com.Lomikel.Januser;

import com.Lomikel.DB.Client;
import com.Lomikel.HBaser.HBaseClient;

// Tinker Pop
import org.apache.tinkerpop.gremlin.structure.Vertex;

// Java
import java.util.Map;
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
    * Fill in all fields from the database.
    * @param vertex The original {@link Vertex}. */
  public Hertex(Vertex vertex) {
    this(vertex, null);
    }
   
  /** Dress existing {@link Vertex} with values from HBase.
    * @param vertex The original {@link Vertex}.
    * @param fields The fields to fill in from the database.
    *               All fields will be filled in if <tt>null</tt>. */
  public Hertex(Vertex   vertex,
                String[] fields) {
    super(vertex, fields);
    if (_client == null) {
      log.warn("HBaseClient is not set, not dressing Vertex as Hertex");
      }
    String n = null;
    Map<String, Map<String, String>> results = _client.scan(rowkey(), n, "*", 0, 0, false, true);
    setFields(results.get(rowkey())); 
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
     
  private static HBaseClient _client;
    
  /** Logging . */
  private static Logger log = Logger.getLogger(Hertex.class);

  }

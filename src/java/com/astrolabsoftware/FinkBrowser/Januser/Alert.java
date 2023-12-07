package com.astrolabsoftware.FinkBrowser.Januser;

import com.Lomikel.Januser.Hertex;

// Tinker Pop
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;

// Log4J
import org.apache.log4j.Logger;

/** <code>Alert</code> is a {@link Hertex} representing <em>alert</em>.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Alert extends Hertex {
   
  static {
    setRowkeyName("alert", Alert.class, "rowkey");
    }

  /** Dress existing {@link Vertex} with values from HBase.
    * @param vertex The original {@link Vertex}.
    * @param fields The coma-separated list of fields to fill in from the database.
    *               All fields will be filled in if <tt>null</tt>. */
  public Alert(Vertex vertex,
               String fields) {
    super(vertex, fields);
    }
   
  /** Dress existing {@link Vertex} with values from HBase.
    * @param vertex The original {@link Vertex}.
    * @param fields The fields to fill in from the database.
    *               All fields will be filled in if <tt>null</tt>. */
  public Alert(Vertex   vertex,
               String[] fields) {
    super(vertex, fields);
    }
    
  /** Get {@link Alert} backuped by <em>HBase</em>
    * from the <em>JanusGraph</em>, or create if it doesn't exist yet.
    * @param rowkey  The {@link Vertex} <tt>rowkey</tt> value.
    * @param g       The {@link GraphTraversalSource} to be used to execute operations.
    * @param fields  The coma-separated list of fields to fill.
    *                <tt>null</tt> will fill all fields.
    *                Empty String will fill nothing besides rowkey fields.
    * @return        The created {@link Vertex}. It will be created even when no corresponding
    *                entry exists in the <em>HBase</em>. In that case, it can be enhanced later. */
  public static Vertex getOrCreate(String                 rowkey,
                                   GraphTraversalSource   g,
                                   String                 fields) {
    return Hertex.getOrCreate("alert", rowkey, g, fields).get(0);
    }
    
  /** Get {@link Alert} backuped by <em>HBase</em>
    * from the <em>JanusGraph</em>, or create if it doesn't exist yet.
    * @param rowkey  The {@link Vertex} <tt>rowkey</tt> value.
    * @param g       The {@link GraphTraversalSource} to be used to execute operations.
    * @param enhance Whether enhance all values from the <em>HBase</em>.
    * @return        The created {@link Vertex}. It will be created even when no corresponding
    *                entry exists in the <em>HBase</em>. In that case, it can be enhanced later. */
  public static Vertex getOrCreate(String                 rowkey,
                                   GraphTraversalSource   g,
                                   boolean                enhance) {
    return Hertex.getOrCreate("alert", rowkey, g, enhance).get(0);
    }
        
  /** Logging . */
  private static Logger log = Logger.getLogger(Alert.class);

  }

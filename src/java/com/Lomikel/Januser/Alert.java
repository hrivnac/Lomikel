package com.Lomikel.Januser;

import com.Lomikel.Utils.LomikelException;

import com.Lomikel.HBaser.HBaseClient;

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
    setRowkeyName(Alert.class, "rowkey");
    }
  
  /** Dress existing {@link Vertex} with values from HBase.
    * Fill in all fields from the database.
    * @param vertex The original {@link Vertex}. */
  public Alert(Vertex vertex) {
    this(vertex, null);
    }
   
  /** Dress existing {@link Vertex} with values from HBase.
    * @param vertex The original {@link Vertex}.
    * @param fields The fields to fill in from the database.
    *               All fields will be filled in if <tt>null</tt>. */
  public Alert(Vertex   vertex,
               String[] fields) {
    super(vertex, fields);
    }
        
  /** Logging . */
  private static Logger log = Logger.getLogger(Alert.class);

  }

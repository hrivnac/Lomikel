package com.Lomikel.Graph;

// org.json
import org.json.JSONArray;

// Java
import java.util.TreeSet;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>Nodes</code> is {@link TreeSet} representation of
  * <a href="http://visjs.org">vis.js</a> <em>Node</em>.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Nodes extends TreeSet<Node> {
  
  /** Create. */
  public Nodes() {
    super();
    }
  
  /** Convert into {@link JSONArray}.
    * @return The {@link JSONArray} representation. */
  public JSONArray toJSONArray() {
    JSONArray ja = new JSONArray();
    for (Node node : this) {
      ja.put(node);
      }
    return ja;
    }
      
  /** Logging . */
  private static Logger log = LogManager.getLogger(Nodes.class);
   
  }

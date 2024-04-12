package com.Lomikel.Graph;

// org.json
import org.json.JSONArray;

// Java
import java.util.TreeSet;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>Edges</code> is {@link TreeSet} representation of
  * <a href="http://visjs.org">vis.js</a> <em>Edge</em>.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Edges extends TreeSet<Edge> {
  
  /** Create. */
  public Edges() {
    super();
    }
  
  /** Convert into {@link JSONArray}.
    * @return The {@link JSONArray} representation. */
  public JSONArray toJSONArray() {
    JSONArray ja = new JSONArray();
    for (Edge edge : this) {
      ja.put(edge);
      }
    return ja;
    }
  
  /** Create {@link Edge}s from a {@link Node}.
    * @param from     The staring {@link Node}.
    * @param tos      The ending {@link Node}s.
    * @param label    The Edge label.
    * @param title    The Edge title.
    * @param group    The group, to which this Edge belongs.
    * @param arrows   The Edge arrows. Blank separated list of <tt>middle to from</tt>.
    * @param color    The Edge color. If <tt>null</tt>, color willbe inheriten from the {@link Node}.
    * @param value    The Node value. */
  public Edges(String   from,
               String[] tos,
               String   label,
               String   title,
               String   group,
               String   arrows,
               String   color,
               String   value) {
    super();
    for (String to : tos) {
      add(new Edge(from,
                   to,
                   label,
                   title,
                   group,
                   arrows,
                   color,
                   value));
      }
    }
    
  /** Create {@link Edge}s to a {@link Node}.
    * @param froms    The staring {@link Node}s.
    * @param to       The ending {@link Node}.
    * @param label    The Edge label.
    * @param title    The Edge title.
    * @param group    The group, to which this Edge belongs.
    * @param arrows   The Edge arrows. Blank separated list of <tt>middle to from</tt>.
    * @param color    The Edge color. If <tt>null</tt>, color willbe inheriten from the {@link Node}.
    * @param value    The Node value. */
  public Edges(String[] froms,
               String   to,
               String   label,
               String   title,
               String   group,
               String   arrows,
               String   color,
               String   value) {
    super();
    for (String from : froms) {
      add(new Edge(from,
                   to,
                   label,
                   title,
                   group,
                   arrows,
                   color,
                   value));
      }
    }
      
  /** Logging . */
  private static Logger log = LogManager.getLogger(Edges.class);
   
  }

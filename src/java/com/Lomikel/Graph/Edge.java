package com.Lomikel.Graph;

// org.json      
import org.json.JSONObject;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>Edge</code> is {@link JSONObject} representation of
  * <a href="http://visjs.org">vis.js</a> <em>Edge</em>.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Edge extends JSONObject
                  implements Comparable<Edge> {
                  
  /** Create.
    * @param from     The {@link Node} represented by this Edge start.
    * @param to       The {@link Node} represented by this Edge end.
    * @param label    The Edge label.
    * @param title    The Edge title.
    * @param group    The group, to which this Edge belongs.
    * @param arrows   The Edge arrows. Blank separated list of <tt>middle to from</tt>.
    * @param color    The Edge color. If <tt>null</tt>, color willbe inheriten from the {@link Node}.
    * @param value    The Node value. */
  public Edge(Node   from,
              Node   to,
              String label,
              String title,
              String group,
              String arrows,
              String color,
              String value) {
    this(from.getString("id"),
         to.getString("id"),
         label,
         title,
         group,
         arrows,
         color,
         value);
    }
    
  /** Create.
    * @param from     The id of {@link Node} represented by this Edge start.
    * @param to       The id of {@link Node} represented by this Edge end.
    * @param label    The Edge label.
    * @param title    The Edge title.
    * @param group    The group, to which this Edge belongs.
    * @param arrows   The Edge arrows. Blank separated list of <tt>middle to from</tt>.
    * @param color    The Edge color. If <tt>null</tt>, color willbe inheriten from the {@link Node}.
    * @param value    The Node value. */
  public Edge(String from,
              String to,
              String label,
              String title,
              String group,
              String arrows,
              String color,
              String value) {
    super();
    put("from",     from);
    put("to",       to);
    put("label",    label);
    put("title",    title);
    put("group",    group);
    put("arrows",   arrows);
    put("value",    value);
    if (color != null) {
      JSONObject colorO = new JSONObject();
      colorO.put("color",    "grey");
      colorO.put("highlite", "grey");
      colorO.put("hover",    "grey");
      colorO.put("inherit",  false);    
      put("color",    colorO);
      }
  }
    
  @Override
  public int compareTo(Edge other) {
    return toString().compareTo(other.toString());
    }
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(Edge.class);
   
  }

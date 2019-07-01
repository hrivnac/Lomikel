package com.JHTools.Graph;

// org.json
import org.json.JSONObject;
import org.json.JSONArray;

// Log4J
import org.apache.log4j.Logger;

/** <code>Node</code> is {@link JSONObject} representation of
  * <a href="http://visjs.org">vis.js</a> <em>Node</em>.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Node extends JSONObject
                  implements Comparable<Node> {
    
  /** Create.
    * @param id       The Node id.
    * @param type     The Node type.
    * @param label    The Node label.
    * @param title    The Node title.
    * @param group    The group, to which this Node belongs.
    * @param shape    The Node shape.
    * @param value    The Node value. */
  public Node(String id,
              String type,
              String label,
              String title,
              String group,
              String shape,
              String value) {
    super();
    put("id",       id);
    put("label",    label);
    put("title",    title);
    put("group",    group);
    put("shape",    shape);
    put("value",    value);
    put("type",     type);
    JSONObject colorO = new JSONObject();
    colorO.put("background", "white");
    colorO.put("border",     "black");
    colorO.put("highlight",  "#eeeeee");
    colorO.put("inherit",    false);    
    put("color",    colorO);
    }
    
  /** Create and add to {@link Nodes} and {@Edges}.
    * @param id       The Node id.
    * @param type     The Node type.
    * @param label    The Node label.
    * @param title    The Node title.
    * @param group    The group, to which this Node belongs.
    * @param shape    The Node shape.
    * @param value    The Node value.
    * @param parent   The parent Node.
    * @param nodes    The {@link Nodes} to be added to.
    * @param edges    The {@link Edges} to to be added to. */
  public Node(String id,
              String type,
              String label,
              String title,
              String group,
              String shape,
              String value,
              Node   parent,
              Nodes  nodes,
              Edges  edges) {
    this(id,
         type,
         label,
         title,
         group,
         shape,
         value);
    nodes.add(this);
    if (parent != null) {
      edges.add(new Edge(parent, 
                         this,
                         " ",
                         " ",
                         " ",
                         " ",
                         "to",
                         " ",
                         "0"));
      }
    }
  
    
  @Override
  public int compareTo(Node other) {
    return toString().compareTo(other.toString());
    }
  
  /** Logging . */
  private static Logger log = Logger.getLogger(Node.class);
   
  }

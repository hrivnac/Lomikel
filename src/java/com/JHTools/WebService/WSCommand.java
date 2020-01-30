package com.JHTools.WebService;

import com.JHTools.Utils.Coding;
import com.JHTools.Graph.Node;
import com.JHTools.Graph.Nodes;
import com.JHTools.Graph.Edge;
import com.JHTools.Graph.Edges;

// Java
import java.util.Map;

// Log4J
import org.apache.log4j.Logger;

/** <code>WSCommand</code> is an example of the root Web Servic kernel for the
  * <em>JHTools</em>.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class WSCommand {
    
  /** Setup. */
  public void setup() {
    // Construct Graph  
    _nodes = new Nodes();
    _edges = new Edges();
    Node top = new Node("/JHTools",
                        "/",
                        "JHTools",
                        " ",
                        " ",
                        " ",
                        "0",
                        null,
                        _nodes,
                        null);
    Node node = new Node("Node",
                         "Node",
                         "Node",
                         " ",
                         " ",
                         " ",
                         "0",
                         top,
                         _nodes,
                         _edges);
    }
      
  /** Give all {@link Nodes}.
    * @return The {@link Nodes}. */
  public Nodes nodes() {
     return _nodes;
    }
    
  /** Give all {@link Edges}.
    * @return The {@link Edges}. */
  public Edges edges() {
     return _edges;
    }
    
  private Nodes _nodes;
  
  private Edges _edges;
      
  /** Logging . */
  private static Logger log = Logger.getLogger(WSCommand.class);
  
  }
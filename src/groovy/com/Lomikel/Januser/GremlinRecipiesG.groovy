package com.Lomikel.Januser;

// Tinker Pop
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>GremlinRecipiesG</code> provides various recipies to handle
  * and modify Gremlin Graphs.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class GremlinRecipiesG extends GremlinRecipies
                              implements GremlinRecipiesGT {
                                
  /** Create and attach to {@link GraphTraversalSource}.
    * @param g The attached {@link GraphTraversalSource}. */
  public GremlinRecipiesG(GraphTraversalSource g) {
    super(g);
    }
    
  /** Create and attach to {@link ModifyingGremlinClient}.
    * @param client The attached  {@link ModifyingGremlinClient}. */
  public GremlinRecipiesG(ModifyingGremlinClient client) {
    super(client);
    }
    
  /** Logging . */
  private static Logger log = LogManager.getLogger(GremlinRecipiesG.class);
    
  }
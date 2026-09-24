package com.astrolabsoftware.FinkBrowser.Januser;

import com.Lomikel.Januser.ModifyingGremlinClient;

// Tinker Pop
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;

/** Concrete composition of Fink Java mutations and Groovy traversal analyses.
  *
  * <p>This class adds no graph or lifecycle state. Ordinary inherited
  * operations share the traversal source and optional client owned by {@link
  * FinkGremlinRecipies}. Generic methods documented as opening independent
  * resources retain their own explicit ownership contracts.</p>
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class FinkGremlinRecipiesG extends FinkGremlinRecipies
                                  implements FinkGremlinRecipiesGT {
  
  /** Create and attach to {@link GraphTraversalSource}.
    * @param g The attached {@link GraphTraversalSource}. */
  public FinkGremlinRecipiesG(GraphTraversalSource g) {
    super(g);
    }
    
  /** Create and attach to {@link ModifyingGremlinClient}.
    * @param client The attached  {@link ModifyingGremlinClient}. */
  public FinkGremlinRecipiesG(ModifyingGremlinClient client) {
    super(client);
    }
    
  }

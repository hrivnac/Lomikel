package com.Lomikel.Januser;

// Tinker Pop
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;

/** Concrete composition of the Java mutation recipes and Groovy traversal
  * helpers.
  *
  * <p>This class adds no graph or lifecycle state. Ordinary inherited Java and
  * trait operations use the traversal source and optional client owned by
  * {@link GremlinRecipies}. Methods documented as opening independent
  * resources retain their own explicit ownership contracts.</p>
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
    
  }
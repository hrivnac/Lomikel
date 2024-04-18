package com.Lomikel.Januser;

// Tinker Pop
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.count;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.values;

/** <code>GremlinRecipiesGT</code> provides various recipies to handle
  * and modify Gremlin Graphs.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
trait GremlinRecipiesGT {
                   
  /** Give full statistics of {@link Vertex}es and {@link Edge}es in the database.
    * @return The full statistics of {@link Vertex}es and {@link Edge}es in
    *         the database. */
  def String stat() {
    return stat(g());
    }
    
  /** Give full statistics of {@link Vertex}es and {@link Edge}es in the database.
    * @param g The {@link GraphTraversalSource} to analyse.
    * @return  The full statistics of {@link Vertex}es and {@link Edge}es in
    *          the database. */
  def static String stat(g) {
    def v = '\nV: ' + g.V().group().by(values('lbl')).by(count()).toSet().toString();
    def e = '\nE: ' + g.E().group().by(values('lbl')).by(count()).toSet().toString();
    return v + e;
    }
    
  }
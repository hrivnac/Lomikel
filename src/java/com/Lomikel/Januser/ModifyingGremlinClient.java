package com.Lomikel.Januser;

// Tinker Pop
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.unfold;

/** <code>ModifyingGremlinClient</code> provides modifying connection to Graph with Gremlin interface.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public interface ModifyingGremlinClient {
    
  /** Give {@link GraphTraversalSource}.
    * @return {@link GraphTraversalSource}. */
  public abstract GraphTraversalSource g();

  /** Commit transaction. */
  public abstract void commit();
    
  /** Close graph. */
  public abstract void close();

  }

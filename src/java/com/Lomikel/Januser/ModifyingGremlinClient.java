package com.Lomikel.Januser;

// Tinker Pop
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;

/** Owns a traversal source and its mutation lifecycle.
  *
  * <p>{@link #commit()} is intentionally part of the smallest client contract.
  * Atomic operations that also require rollback must additionally require
  * {@link TransactionalGremlinClient}.</p>
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public interface ModifyingGremlinClient {
    
  /** Return the owned traversal source.
    * @return The traversal source. */
  public abstract GraphTraversalSource g();

  /** Commit transaction. */
  public abstract void commit();

  /** Close the client and its owned graph/remote resources. */
  public abstract void close();

  }

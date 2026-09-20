package com.Lomikel.Januser;

/** A modifying Gremlin client that owns a real rollback-capable transaction. */
public interface TransactionalGremlinClient extends ModifyingGremlinClient {

  /** Roll back the current transaction. */
  public abstract void rollback();

  }

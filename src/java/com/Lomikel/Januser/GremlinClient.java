package com.Lomikel.Januser;

import com.Lomikel.Utils.Init;

// Tinker Pop
import org.apache.tinkerpop.gremlin.driver.Cluster;
import org.apache.tinkerpop.gremlin.util.MessageSerializer;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** Shared remote Gremlin connection and cleanup lifecycle.
  *
  * <p>Subclasses select a serializer and either expose a typed remote
  * traversal source or script-submission results.</p>
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public abstract class GremlinClient {
   
  /** Create with connection parameters.
    * @param hostname The Gremlin hostname.
    * @param port     The Gremlin port. */
  public GremlinClient(String  hostname,
                       int     port) {
    this(hostname, port, false);
    }

  /** Create with optional deferred initialization for subclasses that need
    * their own state initialized before {@link #open} and {@link #connect}.
    * @param hostname            The Gremlin hostname.
    * @param port                The Gremlin port.
    * @param deferInitialization Whether the subclass will call
    *                            {@link #initialize} explicitly. */
  protected GremlinClient(String  hostname,
                          int     port,
                          boolean deferInitialization) {
    Init.init("GremlinClient");
    log.info("Opening " + hostname + ":" + port);
    if (!deferInitialization) {
      initialize(hostname, port);
      }
    }

  /** Open and connect, closing partial resources if initialization fails.
    * @param hostname The Gremlin hostname.
    * @param port     The Gremlin port. */
  protected final void initialize(String hostname,
                                  int    port) {
    try {
      open(hostname, port);
      connect();
      }
    catch (RuntimeException e) {
      try {
        close();
        }
      catch (RuntimeException cleanupFailure) {
        e.addSuppressed(cleanupFailure);
        }
      throw e;
      }
    }

  /** Accumulate cleanup failures without preventing later resources from closing.
    * @param failure  The previously collected failure, or {@code null}.
    * @param resource The resource whose close failed.
    * @param cause    The close failure.
    * @return The first failure with later failures suppressed. */
  protected static RuntimeException collectCleanupFailure(RuntimeException failure,
                                                           String           resource,
                                                           Exception        cause) {
    RuntimeException next = cause instanceof RuntimeException
                          ? (RuntimeException)cause
                          : new IllegalStateException("Cannot close " + resource, cause);
    if (failure == null) {
      return next;
      }
    failure.addSuppressed(next);
    return failure;
    }
   
  /** Open.  
    * @param hostname The Gremlin hostname.
    * @param port     The Gremlin port. */
  public abstract void open(String hostname,
                            int    port);
        
  /** Connect client. */
  public abstract void connect();
       
  /** Close client. */
  public abstract void close();

  /** Create {@link Cluster}.  
    * @param hostname The Gremlin hostname.
    * @param port     The Gremlin port.
    * @param serializer The used {@link MessageSerializer}. */
  public void createCluster(String            hostname,
                            int               port,
                            MessageSerializer serializer) {
    _cluster = Cluster.build()
                      .addContactPoint(hostname)
                      .port(port)
                      .serializer(serializer)
                      .maxContentLength(2097152)
                      .create();
    }
  
  /** Give the {@link Cluster}.
    * @return The attached {@link Cluster}. */
  public Cluster cluster() {
    return _cluster;
    }
    
  private Cluster _cluster;  

  /** Logging . */
  private static Logger log = LogManager.getLogger(GremlinClient.class);

  }

package com.Lomikel.GremlinPlugin;

// TinkerPop
import org.apache.tinkerpop.gremlin.jsr223.console.RemoteAcceptor;

// Java
import java.util.List;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** Allows remote access to {@link LomikelConnector}.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class LomikelRemoteAcceptor implements RemoteAcceptor {

  @Override
  public Object connect(final List<String> args) {
    return args;
    }

  @Override
  public Object configure(final List<String> args) {
    return args;
    }
      
  @Override
  public Object submit(final List<String> args) {
    return args;
    }
    
  @Override
  public void	 close() {
    }

  /** Logging . */
  private static Logger log = LogManager.getLogger(LomikelRemoteAcceptor.class);

  }

package com.Lomikel.GremlinPlugin;

// TinkerPop
import org.apache.tinkerpop.gremlin.jsr223.console.ConsoleCustomizer;
import org.apache.tinkerpop.gremlin.jsr223.console.GremlinShellEnvironment;
import org.apache.tinkerpop.gremlin.jsr223.console.RemoteAcceptor;

// Log4J
import org.apache.log4j.Logger;

/** Allows remote access to {@link LomikelConnector}.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class LomikelConsoleCustomizer implements ConsoleCustomizer {

  @Override
  public RemoteAcceptor	 getRemoteAcceptor(GremlinShellEnvironment environment) {
    return new LomikelRemoteAcceptor();
    }
  
  /** Logging . */
  private static Logger log = Logger.getLogger(LomikelConsoleCustomizer.class);

  }

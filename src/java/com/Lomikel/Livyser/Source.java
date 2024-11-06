package com.Lomikel.Livyser;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>Source</code> represents data source on <em>Spark</em> servers.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Source extends Element {
  
  /** Create new Source.
    * @param name The Source name.
    * @param server The {@link Server} keeping this Session. */
  public Source(String name,
                Server server) {
    super(name);
    _server = server;
    }
    
  /** Give the keeping {@link Server}.
    * @return The keeping {@link Server}. */
  public Server server() {
    return _server;
    }
    
  @Override
  public String toString() {
    return name() + " on " + _server.name();
    }
  
  private Server _server;
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(Source.class);

  }

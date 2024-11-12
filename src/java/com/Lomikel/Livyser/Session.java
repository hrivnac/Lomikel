package com.Lomikel.Livyser;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** Session represents a session on a Spark Server behind Livy Server.
  * You can send an Action to it.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Session extends Element {
  
  /** Create new Session.
    * @param name     The Session name.
    * @param id       The Session id.
    * @param language The Session {@link Language}.
    * @param server The {@link Server} keeping this Session. */
  public Session(String   name,
                 int      id,
                 Language language,
                 Server   server) {
    super(name);
    _id       = id;
    _language = language;
    _server   = server;
    }
    
  /** Give the Session id.
    * @return The Session id. */
  public int id() {
    return _id;
    }
    
  /** Give the Session {@link Language}.
    * @return The Session {@link Language}. */
  public Language language() {
    return _language;
    }
    
  /** Give the keeping {@link Server}.
    * @return The keeping {@link Server}. */
  public Server server() {
    return _server;
    }
    
  @Override
  public String toString() {
    return name() + " : " + _id + " on " + _server.name() + " in " + _language;
    }
    
  private int _id;
  
  private Language _language;
  
  private Server _server;
 
  /** Logging . */
  private static Logger log = LogManager.getLogger(Session.class);

  }

package com.Lomikel.Livyser;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>Channel</code> represents data channel between <em>Spark</em> servers.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Channel extends Element {

  /** Create new Channel.
    * @param name The Channel name. */
  public Channel(String name) {
    super(name);
    }
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(Channel.class);

  }

package com.Lomikel.Sockets;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>TestServable</code> is a simple testing {@link Servable} for interprocess communication.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class TestServable implements Servable { 

  @Override
  public String query(String request) {
    return "This was request: " + request;
    }

    /** Logging . */
  private static Logger log = LogManager.getLogger(TestServable.class);
  
  } 

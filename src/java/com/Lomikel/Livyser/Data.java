package com.Lomikel.Livyser;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>Data</code> represents data in <em>Spark</em> servers.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Data extends Element {

  /** Create new Data.
    * @param name The Data name. */
  public Data(String name) {
    super(name);
    }
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(Data.class);

  }

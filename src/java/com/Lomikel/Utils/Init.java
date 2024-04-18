package com.Lomikel.Utils;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>Init</code> provides common initialisation.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Init {

  /** Setup system. */
  public static void init() {
    init(false, false);
    }

  /** Setup system for Web Service. */
  public static void initWS() {
    init(true, false);
    }

  /** Setup system. Singleton.
    * @param ws    If initialise to run in a Web Service.
    * @param quiet If no outupt is required. */
  public static void init(boolean ws,
                          boolean quiet) {
    if (_initialised) {
      log.debug("Lomikel already initialised");
      return;
      }
    try {
      if (ws) {
        NotifierURL.notify("", "LomikelWS", Info.release());
        }
      else {
        NotifierURL.notify("", "Lomikel", Info.release());
        }
      }
    catch (Exception e) {
      System.err.println(e);
      }
    _initialised = true;
    if (!quiet) {
      log.info("Lomikel initialised, version: " + Info.release());
      }
    }
    
  public static boolean _initialised = false;  
    
  /** Logging . */
  private static Logger log = LogManager.getLogger(Init.class);

  }

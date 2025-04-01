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

  /** Setup system. Singleton.
    * @param appName The concrete application name. */
  public static void init(String appName) {
    init(appName, false, false);
    }

  /** Setup system. Singleton. */
  public static void init() {
    init("", false, false);
    }

  /** Setup system for Web Service. Singleton.
    * @param appName The concrete application name. */
  public static void initWS(String appName) {
    init(appName, true, false);
    }

  /** Setup system for Web Service. Singleton. */
  public static void initWS() {
    init("", true, false);
    }
    
  /** Setup system. Singleton.
    * @param ws    If initialise to run in a Web Service.
    * @param quiet If no outupt is required. */
  public static void init(boolean ws,
                          boolean quiet) {
    init("", ws, quiet);
    }

  /** Setup system. Singleton.
    * @param appName The application name.
    * @param ws      If initialise to run in a Web Service.
    * @param quiet   If no outupt is required. */
  public static void init(String  appName,
                          boolean ws,
                          boolean quiet) {
    if (_initialised) {
      log.debug("Lomikel already initialised");
      return;
      }
    try {
      if (ws) {
        NotifierURL.notify(appName, "LomikelWS", Info.release());
        }
      else {
        NotifierURL.notify(appName, "Lomikel", Info.release());
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

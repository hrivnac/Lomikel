package com.Lomikel.Utils;

import com.Lomikel.HBaser.HBaseEvaluator;

// Log4J
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

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
      HBaseEvaluator.setAuxFuctions("com.Lomikel.HBaser.EvaluatorFunctions",
                                    "com/Lomikel/HBaser/EvaluatorFunctions.bsh"); 
      HBaseEvaluator.setAuxFuctions(null,
                                    "com/Lomikel/WebService/HBaseColumnsProcessor.bsh"); 
      if (ws) {
        NotifierURL.notify("", "LomikelWS", Info.release());
        }
      else {
        PropertyConfigurator.configure(Init.class.getClassLoader().getResource("com/Lomikel/Utils/log4j.properties"));
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
  private static Logger log = Logger.getLogger(Init.class);

  }

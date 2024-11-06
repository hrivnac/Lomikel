package com.Lomikel.Livyser;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>Language</code> represents <em>Spark</em> command language.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
  public enum Language {
    PYTHON("pyspark") {
      @Override
      public String asSpark() {
        return "pyspark";
        }
      },
    SCALA("spark") {
      @Override
      public String asSpark() {
        return "spark";
        }
      },
    R("sparkr") {
      @Override
      public String asSpark() {
        return "sparkr";
        }
      },
    SQL("sql") {
      @Override
      public String asSpark() {
        return "sql";
        }
      };

  /** Create from the Spark language name.
    * @param spark The Spark language name. */
  Language(String spark){
    _spark = spark;
    }
    
  /** Create from the Spark language name.
    * @param spark The Spark language name.
    * @return The craeted Language. */
   static Language fromSpark(String spark) {
    switch (spark) {
      case "pyspark":
        return Language.PYTHON;
      case "spark":
        return Language.SCALA;
      case "sparkr":
        return Language.R;
      case "sql":
        return Language.SQL;
      default:
        log.error("Language " + spark + " is not known, will use Python");
        return Language.PYTHON;
      }
    }
    
  /** Give the Spark language name.
    * @return The Spark language name. */
  public abstract String asSpark();
  
  private String _spark;
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(Language.class);
 
  }

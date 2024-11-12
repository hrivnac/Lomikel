package com.Lomikel.Livyser;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** Search represents an job running on a HBase Server.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Search extends Element {  
    
  /** Create new Search.
    * @param name   The Search name.
    * @param source The hosting {@link Source}. */
  public Search(String name,
                Source source) {
    super(name);
    _source = source;
    }
  
  /** Give hosting {@link Source}.
    * @return The hosting {@link Source}. */
  public Source source() {
    return _source;
    }
     
  @Override
  public String toString() {
    return name();
    }
   
  private Source _source;  
    
  /** Logging . */
  private static Logger log = LogManager.getLogger(Search.class);

  }

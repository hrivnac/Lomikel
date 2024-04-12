package com.Lomikel.WebService;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>Profile</code> handles web service style.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Style {
    
  public void setStyle(String style) {
    _style = style;
    }
    
  public String style() {
    return _style;
    }
    
  private String _style;
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(Style.class);

  }

package com.Lomikel.WebService;

// Log4J
import org.apache.log4j.Logger;

/** <code>Profile</code> handles web service style.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Style {
    
  /** TBD */
  public void setStyle(String style) {
    _style = style;
    }
    
  /** TBD */
  public String style() {
    return _style;
    }
    
  private String _style;
  
  /** Logging . */
  private static Logger log = Logger.getLogger(Style.class);

  }

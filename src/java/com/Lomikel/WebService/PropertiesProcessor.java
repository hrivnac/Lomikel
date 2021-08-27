package com.Lomikel.WebService;

import com.Lomikel.Utils.DateTimeManagement;

// Java
import java.util.Map;

// Log4J
import org.apache.log4j.Logger;

/** <code>PropertiesProcessor</code> extracts X-axes from rows for graphs.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class PropertiesProcessor {

  /** Give the timestamp corresponding to a timestamp.
    * @param entry One row of the table.
    * @return      The corresponding timestamp. */
  public String getTimestamp(String entry) {
    return entry;
    }
  
  /** Give the date corresponding to a timestamp.
    * @param timestamp The timestamp.
    * @return      The corresponding date. */
  public String getDate(String timestamp) {
    return DateTimeManagement.time2String(Long.valueOf(timestamp), "HH:mm:ss.nnnnnnnnn dd/MM/yyyy ");
    }

  /** Give the <code>ra</code> polar coordinate in degrees.
    * @return The <code>ra</code> polar coordinate in degrees.
    *         The default is <tt>0</tt>. */
  public String ra() {
    return null;
    }
    
  /** Give the <code>dec</code> polar coordinate in degrees.
    * @return The <code>dec</code> polar coordinate in degrees.
    *         The default is <tt>0</tt>. */
  public String dec() {
    return null;
    }
    
  /** Logging . */
  private static Logger log = Logger.getLogger(PropertiesProcessor.class);

  }

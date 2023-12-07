package com.Lomikel.WebService;

import com.Lomikel.Utils.DateTimeManagement;

// Java
import java.util.Map;

// Log4J
import org.apache.log4j.Logger;

/** <code>HBaseColumnsProcessor</code> extracts X-axes from rows for graphs.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class HBaseColumnsProcessor {
  
  /** Give the value of the X-axes (=Julian date) corresponding to one table row.
    * @param entry One row of the table.
    * @return      The derived value of x-axes. */
  public String getTimestamp(Map<String, String> entry) {
    String date = getDate(entry);
    return entry.get("key:time");
    }
    
  /** Give the date corresponding to a table row.
    * @param entry One row of the table.
    * @return      The corresponding date. */
  public String getDate(Map<String, String> entry) {
    String date = getTimestamp(entry);
    return DateTimeManagement.time2String(Long.parseLong(date), "yyyy MM dd HH:mm:ss.nnnnnnnnn");
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
  private static Logger log = Logger.getLogger(HBaseColumnsProcessor.class);

  }

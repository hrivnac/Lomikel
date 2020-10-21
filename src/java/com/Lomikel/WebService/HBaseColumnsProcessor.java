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
  
  /** Give the value of the X-axes (=timestamp) corresponding to one table row.
    * @param entry0 One row of the table.
    * @return       The derived value of x-axes. */
  public String getX(Map.Entry<String, Map<String, String>> entry0) {
    return entry0.getValue().get("key:time");
    }
  
  /** Give the date (from timestamp) corresponding to one table row.
    * @param entry0 One row of the table.
    * @return       The date (may not correspond to the row timestamp). */
  public String getXDate(Map.Entry<String, Map<String, String>> entry0) {
    String date = getX(entry0);
    return DateTimeManagement.time2String(Long.valueOf(date), "yyyy MM dd HH:mm:ss.nnnnnnnnn");
    }

  /** Logging . */
  private static Logger log = Logger.getLogger(HBaseColumnsProcessor.class);

  }

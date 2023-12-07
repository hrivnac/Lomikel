package com.astrolabsoftware.FinkBrowser.WebService;

import com.Lomikel.Utils.DateTimeManagement;
import com.Lomikel.WebService.HBaseColumnsProcessor;

// Java
import java.util.Map;

// Log4J
import org.apache.log4j.Logger;

/** <code>FinkHBaseColumnsProcessor</code>  extracts X-axes from rows for graphs
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class FinkHBaseColumnsProcessor extends HBaseColumnsProcessor {
  
  /** Give the value of the X-axes (=Julian date) corresponding to one table row.
    * @param entry One row of the table.
    * @return      The derived value of x-axes. */
  @Override
  public String getTimestamp(Map<String, String> entry) {
    String date = getDate(entry);
    return Long.toString(DateTimeManagement.string2time(date, "yyyy MM dd HH:mm:ss.SSS"));
    }
  
  /** Give the date (from Julian date) corresponding to one table row.
    * @param entry One row of the table.
    * @return      The date (may not correspond to the row timestamp). */
  @Override
  public String getDate(Map<String, String> entry) {
    String days = entry.get("i:jd");
    return DateTimeManagement.julianDate2String(Double.valueOf(days), "yyyy MM dd HH:mm:ss.SSS");
    }

  @Override
  public String ra() {
    return "i:ra";
    }
    
  @Override
  public String dec() {
    return "i:dec";
    }

  /** Logging . */
  private static Logger log = Logger.getLogger(FinkHBaseColumnsProcessor.class);

  }

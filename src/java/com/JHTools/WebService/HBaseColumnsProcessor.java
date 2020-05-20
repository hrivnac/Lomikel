package com.JHTools.WebService;

// Java
import java.util.Map;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

// Log4J
import org.apache.log4j.Logger;

/** <code>HBaseColumnsProcessor</code> TBD.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
// TBD: should be implemented in clients
public class HBaseColumnsProcessor {
  
  /** TBD */
  public static String getX(Map.Entry<String, Map<String, String>> entry0) {
    return entry0.getKey().split("_")[1];
    }
  
  /** TBD */
  public static String getXDate(Map.Entry<String, Map<String, String>> entry0) {
    String days = getX(entry0);
    long ms = Math.round(Double.parseDouble(days) * 86400);
    Date date = new Date(ms);
    return _formatter.format(date);
    }
    
  private static DateFormat _formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

  /** Logging . */
  private static Logger log = Logger.getLogger(HBaseColumnsProcessor.class);

  }

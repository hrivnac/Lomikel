package com.Lomikel.Utils;

import de.lehmannet.om.util.DateConverter;

// Java
import java.util.Date;
import java.util.Calendar;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.ParseException;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>DateTimeManagement</code> manipulates date and time.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class DateTimeManagement {
  
  /** Convert Julian date to {@link String} using the default format.
    * @param jd The Julian date (incl. fraction of a day).
    * @return The {@link String} representation of Julian date. */
  public static String julianDate2String(double jd) {
    return julianDate2String(jd, null);
    }
  
  /** Convert Julian date to {@link String}.
    * @param jd     The Julian date (incl. fraction of a day).
    * @param format The date format. <tt>null</tt> or empty will use the default format.
    * @return The {@link String} representation of Julian date. */
  public static String julianDate2String(double jd,
                                         String format) {
    if (format == null || format.trim().equals("")) {
      format = FORMAT;
      }
    Calendar calendar = DateConverter.toGregorianDate(jd);
    Date date = calendar.getTime();
    SimpleDateFormat formatter = new SimpleDateFormat(format);
    return formatter.format(date);
    }
    
 /** Give the current time as Julian date.
   * @return       The Julian date (incl. fraction of a day). */
 public static double julianDate() {
   return string2julianDate(null, null);
   }
    
 /** Give {@link String} time as Julian date using the default format.
   * @param timeS  The {@link String} time.
   * @param format The date format. <tt>null</tt> or empty will use the default format.
   * @return       The Julian date (incl. fraction of a day). */
 public static double string2julianDate(String timeS) {
   return string2julianDate(timeS, null);
   }
  
 /** Give {@link String} time (up to <tt>s</tt>) as Julian date.
   * @param timeS  The {@link String} time.
   * @param format The date format. <tt>null</tt> or empty will use the default format.
   * @return       The Julian date (incl. fraction of a days). */
 public static double string2julianDate(String timeS,
                                        String format) {
   if (format == null || format.trim().equals("")) {
     format = FORMAT;
     }
   DateFormat formatter = new SimpleDateFormat(format);
   Date date = new Date();
   try {
     if (timeS != null && !timeS.trim().equals("")) {       
       date = formatter.parse(timeS);
       }
     }
   catch (ParseException e) {
     log.error("Cannot parse time " + timeS + " as " + format + ", using current time");
     }
   Calendar calendar = Calendar.getInstance();
   calendar.setTime(date);
   return DateConverter.toJulianDate(calendar);
   }
    
    
 /** Give {@link String} time in <tt>ns</tt> using the default format.
   * @param timeS  The {@link String} time.
   * @param format The date format. <tt>null</tt> or empty will use the default format.
   * @return       The time in <tt>ns</tt>. */
 public static long string2time(String timeS) {
   return string2time(timeS, null);
   }
     
 /** Give {@link String} time in <tt>ns</tt>.
   * @param timeS  The {@link String} time.
   * @param format The date format. <tt>null</tt> or empty will use the default format.
   * @return       The time in <tt>ns</tt>. */
 public static long string2time(String timeS,
                                String format) {
   if (format == null || format.trim().equals("")) {
     format = FORMAT;
     }
   DateFormat formatter = new SimpleDateFormat(format);
   long time = System.currentTimeMillis();;
   try {
     if (timeS != null && !timeS.trim().equals("")) {       
       Date timeD = formatter.parse(timeS);
       time = timeD.getTime();
       }
     }
   catch (ParseException e) {
     log.error("Cannot parse time " + timeS + " as " + format + ", using current time");
     }
   return time;
   }
    
  /** Give the current time in human readable form.
    * @param format    The date format. <tt>null</tt> or empty will use the default format.
    * @return          The time derived from timestamp. */
  public static String time2String(String format) {
    return time2String(0, format);
    }
    
  /** Give the time in human readable form using the default forma.
    * @param timestamp The timestamp in ms.
    * @return          The time derived from timestamp. */
  public static String time2String(long time) {
    return time2String(time, null);
    }
    
  /** Give the current time in human readable form using the default forma.
     * @return          The current time derived from timestamp. */
  public static String time2String() {
    return time2String(0, null);
    }
   
  /** Give the time in human readable form.
    * @param timestamp The timestamp in ms.
    * @param format    The time format. <tt>null</tt> or empty will use the default format.
    * @return          The time derived from timestamp. */
  public static String time2String(long   timestamp,
                                   String format) {
   if (format == null || !format.trim().equals("")) {
     format = FORMAT;
     }
    Date date;
    if (timestamp == 0) {
      date = new Date();
      }
    else {
      date = new Date(timestamp);
      }
    DateFormat formatter = new SimpleDateFormat(format);
    return formatter.format(date);
    }

  private static String FORMAT = "HH:mm:ss.SSS dd/MMM/yyyy";
  //private static String FORMAT = "yyyy MM dd HH:mm:ss.nnnnnnnnn";
  //private static String FORMAT = "yyyy MM dd HH:mm:ss.mm";
  //private static String FORMAT = "yyyy MM dd HH:mm:ss";
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(DateTimeManagement.class);
  
  }

package com.Lomikel.Utils;

// Java
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.JulianFields;
import java.util.Date;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.ParseException;

// Log4J
import org.apache.log4j.Logger;

/** <code>DateTimeManagement</code> manipulates date and time.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class DateTimeManagement {
  
  /** Convert Julian data to {@link String} using the default format.
    * @param jd The Julian date (up to ns).
    * @return The {@link String} representation of Julian date. */
  public static String julianDate2String(double jd) {
    return julianDate2String(jd, null);
    }
  
  /** Convert Julian data to {@link String}.
    * @param jd     The Julian date (up to ns).
    * @param format The date format. <tt>null</tt> or empty will use the default format.
    * @return The {@link String} representation of Julian date. */
  public static String julianDate2String(double jd,
                                         String format) {
    if (format == null || format.trim().equals("")) {
      format = FORMAT;
      }
    long jdn = (long)Math.floor(jd + 0.5);
    double frac = jd - jdn;
    int hours = (int)Math.floor(frac * 24);
    frac -= (double)(hours / 24.0);
    int minutes = (int)Math.floor(frac * 24 * 60);
    frac -= (double)(minutes / 24.0 / 60.0);
    int seconds = (int)Math.floor(frac * 24 * 60 * 60);
    frac -= (double)(seconds / 24.0 / 60.0 / 60.0);
    int nano = (int)Math.floor(frac * 24 * 60 * 60 * 1000000000);
    System.out.println(hours + " " + minutes + " " + nano);
    LocalDateTime ldt = LocalDateTime.MIN.with(JulianFields.JULIAN_DAY, jdn).withHour(hours).withMinute(minutes).withSecond(seconds).withNano(nano);
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
    return ldt.format(formatter);        
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
   if (format == null || !format.trim().equals("")) {
     format = FORMAT;
     }
   DateFormat formatter = new SimpleDateFormat(format);
   long time = System.currentTimeMillis();;
   try {
     if (timeS != null && !timeS.trim().equals("")) {       
       Date timeD = formatter.parse(timeS);
       Calendar timeC = GregorianCalendar.getInstance();
       timeC.setTime(timeD);
       time = timeC.getTimeInMillis();
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
  private static Logger log = Logger.getLogger(DateTimeManagement.class);
  
  }

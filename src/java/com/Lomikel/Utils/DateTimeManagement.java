package com.Lomikel.Utils;

import de.lehmannet.om.util.DateConverter;

// Java
import java.util.Date;
import java.util.Calendar;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.ParsePosition;

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
   * @return       The Julian date (incl. fraction of a day). */
 public static double string2julianDate(String timeS) {
   return string2julianDate(timeS, null);
   }
  
 /** Give {@link String} time (up to <tt>s</tt>) as Julian date.
   * @param timeS  The {@link String} time; null or blank means current time.
   * @param format The date format. <tt>null</tt> or empty will use the default format.
   * @return       The Julian date (incl. fraction of a day); invalid input uses current time.
   *               Callers must validate input separately to report errors to users. */
 public static double string2julianDate(String timeS,
                                        String format) {
   if (format == null || format.trim().equals("")) {
     format = FORMAT;
     }
   Date date = timeS == null || timeS.trim().equals("")
             ? null : parseDate(timeS, format);
   if (date == null) {
     date = new Date();
     }
   Calendar calendar = Calendar.getInstance();
   calendar.setTime(date);
   return DateConverter.toJulianDate(calendar);
   }
    
    
 /** Give {@link String} time in <tt>ms</tt> using the default format.
   * @param timeS  The {@link String} time.
   * @return       The time in milliseconds since the Unix epoch. */
 public static long string2time(String timeS) {
   return string2time(timeS, null);
   }
     
 /** Give {@link String} time in milliseconds.
   * @param timeS  The {@link String} time; null or blank means current time.
   * @param format The date format. <tt>null</tt> or empty will use the default format.
   * @return       The time in milliseconds since the Unix epoch; invalid input uses current time.
   *               Callers must validate input separately to report errors to users. */
 public static long string2time(String timeS,
                                String format) {
   if (format == null || format.trim().equals("")) {
     format = FORMAT;
     }
   if (timeS == null || timeS.trim().equals("")) {
     return System.currentTimeMillis();
     }
   Date date = parseDate(timeS, format);
   return date == null ? System.currentTimeMillis() : date.getTime();
   }

  /** Parse the entire input without calendar rollover; return null for invalid date text. */
  private static Date parseDate(String timeS, String format) {
    // The HBaseTable input uses YYYY for a calendar year, not a week-year.
    if (format.equals("dd/MM/YYYY HH:mm")) {
      format = "dd/MM/yyyy HH:mm";
      }
    DateFormat formatter = new SimpleDateFormat(format);
    formatter.setLenient(false);
    ParsePosition position = new ParsePosition(0);
    Date date = formatter.parse(timeS, position);
    return date != null && position.getIndex() == timeS.length() ? date : null;
    }
    
  /** Give the current time in human readable form.
    * @param format    The date format. <tt>null</tt> or empty will use the default format.
    * @return          The time derived from timestamp. */
  public static String time2String(String format) {
    return time2String(0, format);
    }
    
  /** Give the time in human readable form using the default format.
    * @param time The timestamp in ms; zero means current time.
    * @return          The time derived from timestamp. */
  public static String time2String(long time) {
    return time2String(time, null);
    }
    
  /** Give the current time in human readable form using the default format.
     * @return          The current time derived from timestamp. */
  public static String time2String() {
    return time2String(0, null);
    }
   
  /** Give the time in human readable form.
    * @param timestamp The timestamp in ms; zero means current time.
    * @param format    The time format. <tt>null</tt> or empty will use the default format.
    * @return          The time derived from timestamp. */
  public static String time2String(long   timestamp,
                                   String format) {
   if (format == null || format.trim().equals("")) {
     format = FORMAT;
     }
    Date date;
    if (timestamp == 0) {
      date = new Date();
      }
    else {
      date = new Date(timestamp);
      }
    // These two legacy callers request nanoseconds although timestamps are milliseconds.
    if (format.equals("yyyy MM dd HH:mm:ss.nnnnnnnnn")) {
      format = "yyyy MM dd HH:mm:ss.SSS'000000'";
      }
    else if (format.equals("HH:mm:ss.nnnnnnnnn dd/MM/yyyy ")) {
      format = "HH:mm:ss.SSS'000000' dd/MM/yyyy ";
      }
    DateFormat formatter = new SimpleDateFormat(format);
    return formatter.format(date);
    }

  private static String FORMAT = "HH:mm:ss.SSS dd/MMM/yyyy";
  //private static String FORMAT = "yyyy MM dd HH:mm:ss.nnnnnnnnn";
  //private static String FORMAT = "yyyy MM dd HH:mm:ss.mm";
  //private static String FORMAT = "yyyy MM dd HH:mm:ss";
  
  }

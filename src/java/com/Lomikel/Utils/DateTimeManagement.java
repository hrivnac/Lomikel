package com.Lomikel.Utils;

// Java
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

/** <code>DateTimeManagement</code> manipulates date and time.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
// TBD: clean
public class DateTimeManagement {
  
  /** Convert Julian data to {@link String}.
    * @param jd The Julian date (up to ns).
    * @return The {@link String} representation of Julian date. */
  // BUG: wrong !
  public static String julianDate2String(double jd) {
    int days = (int)jd;
    double fraction = jd - days;
    days -= 2451545; //  12:00 UT (noon) on January 1, 2000
    int y = (int)(days / 365); 
    days -= y * 365;
    y += 2000;
    int m = (int)(days / 30);
    days -= m * 30;
    int d = days;
    int h = (int)(24 * fraction);
    fraction -= (double)h / 24.0;
    int mi = (int)(24 * 60 * fraction);
    fraction -= (double)mi / 24.0 / 60.0;
    int s = (int)(24 * 60 * 60 * fraction);
    fraction -= (double)s / 24.0 / 60.0 / 60.0;
    long ns = (long)(24 * 60 * 60 * 1000000000L * fraction);
    fraction -= (double)ns / 24.0 / 60.0 / 60.0 / 1000000000.0;
    LocalDateTime ldt = LocalDateTime.of(y, m + 1, d + 1, h, mi, s, (int)ns);
    return  ldt.format(FORMATTER);        
    }
  /** Give the time in human readable form.
    * @param timestamp The timestamp in ms.
    * @param format    The time format.
    * @return          The time derived from timestamp. */
  public static String time2String(long timestamp,
                                   String format) {
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
    
  /** Give the current time in human readable form.
    * @param format    The time format.
    * @return          The time derived from timestamp. */
  public static String time2String(String format) {
    return time2String(0, format);
    }
    
  /** Give the time in human readable form.
    * @param timestamp The timestamp in ms.
    * @return          The time derived from timestamp. */
  public static String time2String(long timestamp) {
    return time2String(timestamp, FORMAT);
    }
    
  /** Give the current time in human readable form.
     * @return          The time derived from timestamp. */
  public static String time2String() {
    return time2String(0, FORMAT);
    }

  private static String FORMAT = "HH:mm:ss.SSS dd/MMM/yyyy";

  private static DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy MM dd HH:mm:ss.nnnnnnnnn");
  
  }

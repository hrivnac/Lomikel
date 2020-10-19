package com.Lomikel.Utils;

/** <code>JulianDate</code> converts to/from Julian date.
  * Take from <a href="https://stackoverflow.com/questions/14988459/how-do-i-use-julian-day-numbers-with-the-java-calendar-api">StackOverflow</a>.
  * Based on <em>Jean Meeus's Astronomical Algorithms, 1st ed., 1991</em>.
  * Changed <em>milis</em> to <em>nanos</em>.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class JulianDate {

  /* Convert a timestamp presented as an array of integers in the following
   * order (from index 0 to 6): year,month,day,hours,minutes,seconds,nanos
   * month (1-12), day (1-28 or 29), hours (0-23), min/sec (0-59) to a
   * Modified Julian Day Number.
   * The input values are assumed to be well-formed;
   * error checking is not implemented.*/
  public static double toJD(int[]   ymd_hms,
                            boolean modified) {
    int y = ymd_hms[YEAR];
    int m = ymd_hms[MONTH];
    double d = (double) ymd_hms[DAY];
    d = d + ((ymd_hms[HOURS]   / 24.0            ) +
             (ymd_hms[MINUTES] / 1440.0          ) +
             (ymd_hms[SECONDS] / 86400.0         ) +
             (ymd_hms[NANOS]   / 86400000000000.0));
    if (m == 1 || m == 2) {
      y--;
      m = m + 12;
      }
    double a = Math.floor(y / 100);
    double b = 2 - a + Math.floor(a / 4);
    double jd = (Math.floor(365.25 * (y + 4716.0)) +
                 Math.floor(30.6001 * (m + 1    )) +
                 d + b - 1524.5);
    if (modified) {
      jd -= 2400000.5; 
      }
    return jd;
    }

  /* Convert an Modified Julian Day Number (double) to an integer array representing
   * a timestamp (year,month,day,hours,mins,secs,nanos). */
  public static int[] toTimestamp(double  jd,
                                  boolean modified) {
    int ymd_hms[] = {-1, -1, -1, -1, -1, -1, -1};
    int a, b, c, d, e, z;
    jd += jd + 0.5;
    if (modified) {
      jd += 2400000.5;
      }
    double f, x;
    z = (int) Math.floor(jd);
    f = jd - z;
    if (z >= 2299161) {
      int alpha = (int) Math.floor((z - 1867216.25) / 36524.25);
      a = z + 1 + alpha - (int) Math.floor(alpha / 4);
      }
    else {
      a = z;
      }
    b = a + 1524;
    c = (int) Math.floor((b - 122.1) / 365.25);
    d = (int) Math.floor(365.25 * c);
    e = (int) Math.floor((b - d) / 30.6001);
    ymd_hms[DAY] = b - d - (int) Math.floor(30.6001 * e);
    ymd_hms[MONTH] = (e < 14)
      ? (e - 1)
      : (e - 13);
    ymd_hms[YEAR] = (ymd_hms[MONTH] > 2)
      ? (c - 4716)
      : (c - 4715);
    for (int i = HOURS; i <= NANOS; i++) {
      switch(i) {
        case HOURS:
          f = f * 24.0;
          break;
        case MINUTES: case SECONDS:
          f = f * 60.0;
          break;
        case NANOS:
          f = f * 1000000000.0;
          break;  
        }
      x = Math.floor(f);
      ymd_hms[i] = (int) x;
      f = f - x;
      }   
    return ymd_hms;
    } 

  private static final int YEAR    = 0;
  private static final int MONTH   = 1;
  private static final int DAY     = 2;
  private static final int HOURS   = 3;
  private static final int MINUTES = 4;
  private static final int SECONDS = 5;
  private static final int NANOS   = 6;

  }
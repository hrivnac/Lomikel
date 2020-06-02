package com.Lomikel.Utils;

// Java
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

/** <code>IDFactory</code> creates unique ids.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class IDFactory {
  
  /** Create a unique id.
    * @return The unique id. */
  public static String newID() {
    DateFormat formatter = new SimpleDateFormat(FORMAT);
    return(System.getProperty("user.name") + "/" + formatter.format(new Date()));
    }

  private static String FORMAT = "yyyy.MM.dd.HH.mm.ss.SSS";
  
  }

package com.Lomikel.Utils;

// Java
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

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
    return newID(System.currentTimeMillis());
    }

  // The random nonce separates JVMs; the sequence covers repeated or rolled-back times.
  private static final String PROCESS_NONCE = UUID.randomUUID().toString();
  private static final AtomicLong SEQUENCE = new AtomicLong();

  static String newID(long timestamp) {
    DateFormat formatter = new SimpleDateFormat(FORMAT);
    return System.getProperty("user.name") + "/" + formatter.format(new Date(timestamp))
           + "-" + PROCESS_NONCE + "-" + SEQUENCE.getAndIncrement();
    }

  private static final String FORMAT = "yyyy.MM.dd.HH.mm.ss.SSS";
  
  }

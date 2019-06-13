package com.JHTools.Utils;

// Java
import java.io.StringWriter;
import java.io.PrintWriter;

// Log4J
import org.apache.log4j.Logger;

/** <code>CommonException</code> provides the customised
  * {@link Exception} behaviour.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class CommonException extends Exception {

  public CommonException() {
    super();
    }

  public CommonException(String msg) {
    super(msg);
    }

  public CommonException(Throwable nested) {
    super(nested);
    }

  public CommonException(String msg,
                              Throwable nested) {
    super(msg, nested);
    }
    
  /** Give full stack trace.
    * @return The full stack trace. */
  public String stackTrace() {
    return stackTrace2String(this);
    }  
    
  /** Convert {@link Exception} stack trace to String.
    * @param e The {@link Exception}.
    * @return  The stack trace as a String. */
  public static String stackTrace2String(Exception e) {
    Throwable t = e;
    StringWriter sw = new StringWriter(); 
    while (t != null) {
      t.printStackTrace(new PrintWriter(sw));
      t = t.getCause();
      }
    return sw.toString();
    }

  /** Report {@link Throwable} to the logging system.
    * @param text The text to be reported.
    * @param e    The associated {@link Exception}.
    * @param l    The {@link Logger} of the origin of the {@link Throwable} . */
  public static void reportException(String text, Exception e, Logger l) {
    l.error(text + ", see JHTools.log for details");
    l.debug(stackTrace2String(e));
    }
    
    
  }

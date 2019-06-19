package com.JHTools.Utils;

// Java
import java.net.URL;
import java.net.HttpURLConnection;

// Log4J
import org.apache.log4j.Logger;

/** <code>Notifier</code> connects to Web page
  * to reqister call.
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Notifier {

  /** Connect to monitorring Web page.
    * @param message The message to be send. */
  public static void notify(String message) {
    notify(message, "JHTools");
    }

  /** Connect to monitorring Web page.
    * @param message The message to be send.
    * @param source  The message source. */
  public static void notify(String message,
                            String source) {
    Thread thread = new Thread() {
      @Override
      public void run() {
        try {
          URL url = new URL("http://cern.ch/hrivnac/cgi-bin/record.pl?page=" + (source + "_" + message + "_" + Info.release()).replaceAll(" ", "_"));
          HttpURLConnection conn = (HttpURLConnection)url.openConnection();
          conn.setRequestMethod("GET");
          conn.getInputStream();
          }
        catch (Exception e) {
          log.debug("Can not notify: " + message, e);
          }
        }
      };
    thread.start();
    }
    
  /** Logging . */
  private static Logger log = Logger.getLogger(Notifier.class);
    
  }

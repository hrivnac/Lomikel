package com.Lomikel.Utils;

// Java
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.URLEncoder;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>NotifierURL</code> connects to Web page
  * to reqister call.
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class NotifierURL {

  /** Connect to monitorring Web page.
    * @param message The message to be send. */
  public static void notify(String message) {
    notify(message, "Lomikel");
    }

  /** Connect to monitorring Web page.
    * @param message The message to be send.
    * @param source  The message source. */
  public static void notify(String message,
                            String source) {
    notify(message, source, Info.release() + " of Lomikel");                       
    }
    
  /** Connect to monitorring Web page.
    * @param message The message to be send.
    * @param source  The message source.
    * @param release The service release. */
  public static void notify(String message,
                            String source,
                            String release) {
    notify(message, source, release, null);
    }

  /** Connect to monitorring Web page.
    * @param message The message to be send.
    * @param source  The message source.
    * @param release The service release.
    * @param text    The additional text. */
  public static void notify(String message,
                            String source,
                            String release,
                            String text) {
    Thread thread = new Thread() {
      @Override
      public void run() {
        try {
          String urlS = "https://hrivnac.web.cern.ch/cgi-bin/record.pl?page=" + URLEncoder.encode((source + "_" + message + "_" + release).replaceAll(" ", "_"), "UTF-8");
          if (text != null) {
            urlS += "&text=" + URLEncoder.encode(text, "UTF-8");
            }
          URL url = new URL(urlS);
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
  private static Logger log = LogManager.getLogger(NotifierURL.class);
    
  }

package com.Lomikel.Utils;

// Java
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>NotifierURL</code> connects to Web page
  * to reqister call.
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class NotifierURL {

  /** Package-private offline seam; production uses the URL's ordinary connection. */
  @FunctionalInterface
  interface ConnectionFactory {
    HttpURLConnection open(URL url) throws IOException;
    }

  static volatile ConnectionFactory connectionFactory = url -> (HttpURLConnection)url.openConnection();

  /** One daemon worker and a finite backlog; telemetry must never hold up callers or shutdown. */
  private static final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L,
                                                                            TimeUnit.MILLISECONDS,
                                                                            new ArrayBlockingQueue<>(64),
                                                                            task -> {
                                                                              Thread thread = new Thread(task, "NotifierURL-worker");
                                                                              thread.setDaemon(true);
                                                                              return thread;
                                                                              });

  /** Connect to monitorring Web page.
    * Non-blocking.
    * @param message The message to be send. */
  public static void notify(String message) {
    notify(message, "Lomikel");
    }

  /** Connect to monitorring Web page.
    * Non-blocking.
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
    * Non-blocking.
    * @param message The message to be send.
    * @param source  The message source.
    * @param release The service release.
    * @param text    The additional text. */
  public static void notify(String message,
                            String source,
                            String release,
                            String text) {
    try {
      executor.execute(() -> notifyExecution(message, source, release, text));
      }
    catch (RejectedExecutionException e) {
      log.debug("Notification queue full; dropping notification");
      }
    }
    
  /** Connect to monitorring Web page.
    * Blocking.
    * @param message The message to be send.
    * @param source  The message source.
    * @param release The service release.
    * @param text    The additional text. */
  public static void notifyExecution(String message,
                                     String source,
                                     String release,
                                     String text) {
    try {
      String urlS = "https://hrivnac.web.cern.ch/cgi-bin/record.pl?page=" + URLEncoder.encode((source + "_" + message + "_" + release).replaceAll(" ", "_"), "UTF-8");
      if (text != null) {
        urlS += "&text=" + URLEncoder.encode(text, "UTF-8");
        }
      URL url = new URL(urlS);
      HttpURLConnection conn = connectionFactory.open(url);
      try {
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestMethod("GET");
        InputStream stream = conn.getInputStream();
        try {
          int rc = conn.getResponseCode();
          String msg = conn.getResponseMessage();
          log.debug(msg + " : " + rc);
          }
        finally {
          stream.close();
          }
        }
      finally {
        try {
          InputStream error = conn.getErrorStream();
          if (error != null) error.close();
          }
        finally {
          conn.disconnect();
          }
        }
      }
    catch (Exception e) {
      log.debug("Can not notify: " + message, e);
      }
    }  
    
  /** Logging . */
  private static Logger log = LogManager.getLogger(NotifierURL.class);
    
  }

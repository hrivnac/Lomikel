package com.JHTools.Utils;

// Java
import java.net.Socket;
import java.net.URL;
import java.net.MalformedURLException;

// Log4J
import org.apache.log4j.Logger;

/** <code>Network</code> provides netowk utilities.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Network {

  /** Check server availability..
    * @param urlS The server url.
    * @return     Whether server can be reached. */
  public static boolean checkPort(String urlS) {
    URL url = null;
    try {
      url = new URL(urlS);
      }
    catch (MalformedURLException e) {
      log.error("Bad URL: " + urlS, e);
      return false;
      }
    if (url == null) {
      return false;
      }
    return checkPort(url.getHost(), url.getPort());
    }
  
  /** Check server availability..
    * @param host The server host.
    * @param port The server port.
    * @return     Whether server can be reached. */
  public static boolean checkPort(String host,
                                  int    port) {
    Socket s = null;
    try {
      s = new Socket(host, port);
      return true;
      }
    catch (Exception e) {
      return false;
      }
    finally {
      if (s != null)
        try {
          s.close();
          }
        catch(Exception e){}
       }
     }
  
  /** Logging . */
  private static Logger log = Logger.getLogger(Network.class);
    
  }

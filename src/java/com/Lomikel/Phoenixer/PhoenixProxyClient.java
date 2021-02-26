package com.Lomikel.Phoenixer;

import com.Lomikel.Sockets.SocketClient;
import com.Lomikel.Utils.LomikelException;
import com.Lomikel.DB.SearchMap;

// Java
import java.util.Map;
import java.util.TreeMap;

// Log4J
import org.apache.log4j.Logger;

/** <code>PhoenixProxyClient</code> connects to PhoenixProxyServer over socket.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class PhoenixProxyClient extends PhoenixClient {
       
  /** Create and connect to the {@link PhoenixProxyServer}.
    * @param proxyIp   The {@link PhoenixProxyServer} ip.
    *                  If <code>null</code>, no connection will be made.
    * @param proxyPort The {@link PhoenixProxyServer} port.
    *                  If <code>null</code>, no connection will be made.    
    * @throws LomikelException If anything goes wrong. */
  public PhoenixProxyClient(String         proxyIp,
                            int            proxyPort) throws LomikelException {
    super();
    if (proxyIp != null && proxyPort != 0) {
      log.info("Opening " + proxyIp + ":" + proxyPort);
      _socketClient = new SocketClient(proxyIp, proxyPort);
      }
    }

  @Override
  public Map<String, Map<String, String>> scan(String    key,
                                               SearchMap searchMap,
                                               String    filter,
                                               long      start,
                                               long      stop,
                                               boolean   ifkey,
                                               boolean   iftime) {
    String sql = formSqlRequest(key, searchMap, filter, start, stop, ifkey, iftime);
    try {
      String answer = _socketClient.send(sql);
      return interpretSqlAnswer(answer);
      }
    catch (LomikelException e) {
      log.error("Cannot scan", e);
      return new TreeMap<String, Map<String, String>>();
      }
    }
    
  @Override
  public void close() {
    _socketClient.close();
    }
	
	SocketClient _socketClient;
  
  /** Logging . */
  private static Logger log = Logger.getLogger(PhoenixProxyClient.class);
    
  }

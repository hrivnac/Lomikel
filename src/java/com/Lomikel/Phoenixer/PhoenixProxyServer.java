package com.Lomikel.Phoenixer;

import com.Lomikel.Utils.Init;
import com.Lomikel.Utils.Info;
import com.Lomikel.Sockets.SocketServer;
import com.Lomikel.Sockets.Servable;
import com.Lomikel.Utils.LomikelException;

// Java
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

// Log4J
import org.apache.log4j.Logger;

/** {@link PhoenixProxyServer} executes requests on the Phoenix Proxy via JDBC.
  * It listenes on the socket.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class PhoenixProxyServer extends PhoenixClient
                                implements Servable {
    
  /** Start {@link SocketServer}.
    * @param port       The proxyPort for this sever.
    *                   Will use the default port if <code>null</code>.
    * @param phoenixUrl The {@link Phoenix} url.
    *                   Will use the default url if <code>null</code>.
    * @param ef         The {@link ElementFactory} for {@link Element}s creation.
    * @throws LomikelException If anhything goes wrong. */
  public PhoenixProxyServer(int            proxyPort,
                            String         phoenixUrl,
                            ElementFactory ef) throws LomikelException {
     super(phoenixUrl == null ? DEFAULT_PHOENIX_URL : phoenixUrl, ef);
    if (proxyPort == 0) {
      proxyPort = DEFAULT_PROXY_PORT;
      }
    log.info("Serving " + (phoenixUrl == null ? DEFAULT_PHOENIX_URL : phoenixUrl) + " on " + proxyPort);
    PhoenixProxyServer proxy = this;
    final int proxyPortF = proxyPort;
    Thread thread = new Thread() {
      @Override
      public void run() {
        try {
          SocketServer server = new SocketServer(proxy, proxyPortF);
          while (true) {
            server.accept();
            }
          }
        catch (LomikelException e) {
          log.error("Cannot create SocketServer", e);
          }
        }
      };
    thread.start();
    }
        
  private String _url; 
    
	private static int DEFAULT_PROXY_PORT = 5000;
   
	private static String DEFAULT_PHOENIX_URL = "jdbc:phoenix:localhost:2181";
      
  /** Logging . */
  private static Logger log = Logger.getLogger(PhoenixProxyServer.class);
    
  }

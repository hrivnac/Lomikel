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

  /** Start {@link PhoenixProxyServer}. 
    * @param args[0] The port for this sever.
    * @param args[1] The url of the remote Phoenix server. */
  public static void main(String[] args) throws LomikelException {
    Init.init();
    new PhoenixProxyServer(new Integer(args[0]), args[1]);
    }
    
  /** Start {@link SocketServer}.
    * @param port       The proxyPort for this sever.
    * @param phoenixUrl The {@link Phoenix} url.
    * @throws LomikelException If anhything goes wrong. */
  public PhoenixProxyServer(int            proxyPort,
                            String         phoenixUrl) throws LomikelException {
    super(phoenixUrl);
    log.info("Serving " + phoenixUrl + " on " + proxyPort);
    PhoenixProxyServer proxy = this;
    final int proxyPortF = proxyPort;
    Thread thread = new Thread() {
      @Override
      public void run() {
        while (true) {
          log.info("Starting the server");
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
        }
      };
    thread.start();
    }
        
  private String _url; 
     
 /** Logging . */
 private static Logger log = Logger.getLogger(PhoenixProxyServer.class);
   
 }

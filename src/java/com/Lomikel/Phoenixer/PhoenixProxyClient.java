package com.Lomikel.Phoenixer;

import com.Lomikel.Utils.Init;
import com.Lomikel.Sockets.SocketServer;
import com.Lomikel.Sockets.SocketClient;
import com.Lomikel.Utils.LomikelException;

// Tinker Pop
import org.apache.tinkerpop.gremlin.structure.Vertex;

// Java
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.sql.Date;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

// Log4J
import org.apache.log4j.Logger;

/** <code>PhoenixProxyClient</code> connects to PhoenixProxyServer over socket.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
//public class PhoenixProxyClient extends PhoenixClient {
//       
//  /** Create and connect to the default {@link PhoenixProxyServer}.
//    * @param ef The {@link ElementFactory} for {@link Element}s creation.
//    * @throws LomikelException If anything goes wrong. */
//  public PhoenixProxyClient(ElementFactory ef) throws LomikelException {
//    this(DEFAULT_PROXY_IP, DEFAULT_PROXY_PORT, ef);
//    }
//       
//  /** Create and connect to the {@link PhoenixProxyServer}.
//    * @param proxyIp   The {@link PhoenixProxyServer} ip.
//    *                  If <code>null</code>, no connection will be made.
//    * @param proxyPort The {@link PhoenixProxyServer} port.
//    *                  If <code>null</code>, no connection will be made.    
//    * @param ef        The {@link ElementFactory} for {@link Element}s creation.
//    * @throws LomikelException If anything goes wrong. */
//  public PhoenixProxyClient(String         proxyIp,
//                            int            proxyPort,
//                            ElementFactory ef) throws LomikelException {
//    super(ef);
//    if (proxyIp != null && proxyPort != 0) {
//      log.info("Opening " + proxyIp + ":" + proxyPort);
//      _socketClient = new SocketClient(proxyIp, proxyPort);
//    }
//    }
//    
//  @Override
//  public List<Element> search(Element prototype) throws LomikelException {
//    return parsePhoenixResults(_socketClient.send(phoenixSearch(prototype)), prototype);
//    }
//    
//  @Override
//  public String search(String sql) throws LomikelException {
//    return _socketClient.send(sql);
//    }
//    
//  @Override
//  public void close() {
//    _socketClient.close();
//    }
//	
//	private static String DEFAULT_PROXY_IP = "127.0.0.1";
//	
//	private static int DEFAULT_PROXY_PORT = 5000;
//	
//	SocketClient _socketClient;
//  
//  /** Logging . */
//  private static Logger log = Logger.getLogger(PhoenixProxyClient.class);
//    
//  }

package com.Lomikel.Py4J;

import com.Lomikel.Utils.Info;
import com.Lomikel.Utils.NotifierURL;

// Py4J
import py4j.GatewayServer;

// Java
import java.net.InetAddress;
import java.net.UnknownHostException;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** Simple {@link GatewayServer}. 
  * The Python client is called like:
  * <pre>
  * from py4j.java_gateway import (JavaGateway, GatewayParameters)
  * gateway = JavaGateway(gateway_parameters = GatewayParameters(address = "127.0.0.1",
  *                                                              port = 25333))
  * gateway.jvm.java.lang.System.out.println("test")
  * </pre>
  * To access it from the remote client, you may need to setup a tunnel first:
  * <pre>
  * ssh -L local_port:localhost:server_port remote_id@server_ip
  * for example:
  * ssh -L 25333:localhost:25333 centos@157.136.253.253
  * ssh -L 25333:localhost:25333 almalinux@134.158.243.144
  * </pre>
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class LomikelGatewayServer {

  /** Start the loopback-only server; remote clients can use an SSH tunnel. */
  public static void main(String[] args) throws UnknownHostException {
    GatewayServer gatewayServer = createGatewayServer(GatewayServer.DEFAULT_PORT);
    gatewayServer.start();
    log.info("Gateway Server Started at port " + GatewayServer.DEFAULT_PORT);
    try {
      NotifierURL.notify("", "Lomikel-GatewayServer", Info.release());
      }
    catch (Exception e) {
      System.err.println(e);
      }
    }

  /** Construct an unstarted gateway for a specific port (zero for tests). */
  static GatewayServer createGatewayServer(int port) throws UnknownHostException {
    // The documented Python client uses IPv4 even on IPv6-preferred JVMs.
    InetAddress loopback = InetAddress.getByName("127.0.0.1");
    return new GatewayServer(new LomikelGatewayServer(),
                             port,
                             GatewayServer.DEFAULT_PYTHON_PORT,
                             loopback,
                             loopback,
                             0,
                             0,
                             null);
    }
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(LomikelGatewayServer.class);

  }

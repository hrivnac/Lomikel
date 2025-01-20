package com.Lomikel.Py4J;

import com.Lomikel.Utils.NotifierURL;

// Py4J
import py4j.GatewayServer;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** Simple {@link GatewayServer}. 
  * The Python client is called like:
  * <pre>
  * from py4j.java_gateway import JavaGateway
  * gateway = JavaGateway()
  * gateway.jvm.java.lang.System.out.println("pokus")
  * </pre>
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class LomikelGatewayServer {

  /** Start the server. */
  public static void main(String[] args) {
    GatewayServer gatewayServer = new GatewayServer(new LomikelGatewayServer());
    gatewayServer.start();
    log.info("Gateway Server Started");
    try {
      NotifierURL.notify("", "Lomikel-GatewayServer", Info.release());
      }
    catch (Exception e) {
      System.err.println(e);
      }
    }
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(LomikelGatewayServer.class);

  }

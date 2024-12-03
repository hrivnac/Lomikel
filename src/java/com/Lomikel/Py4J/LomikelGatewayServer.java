package com.Lomikel.Py4J;

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
    }
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(LomikelGatewayServer.class);

  }

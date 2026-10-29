package com.Lomikel.Py4J;

import py4j.GatewayServer;
import java.net.Socket;

/** Offline default-binding smoke test: never opens a non-loopback listener. */
public final class LomikelGatewayServerOfflineTest {
  public static void main(String[] args) throws Exception {
    GatewayServer gateway = LomikelGatewayServer.createGatewayServer(0);
    if (!gateway.getAddress().isLoopbackAddress()) {
      throw new AssertionError("Gateway must bind to loopback by default");
    }
    try {
      gateway.start();
      int port = gateway.getListeningPort();
      if (port <= 0) { throw new AssertionError("No ephemeral listener"); }
      try (Socket client = new Socket("127.0.0.1", port)) {
        if (!client.isConnected()) { throw new AssertionError("Loopback connection failed"); }
      }
    }
    finally { gateway.shutdown(); }
    System.out.println("LomikelGatewayServerOfflineTest PASS");
  }
}

package com.Lomikel.Utils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URLConnection;
import java.net.URL;
import java.net.URLStreamHandler;

/**
 * Offline loopback checks for Network. Run main() in a fresh JVM: the custom
 * URLStreamHandlerFactory is process-wide and may be installed only once.
 * Do not call main() from a test runner that shares its JVM with other tests.
 */
public final class NetworkTest {
  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static void defaultPort() throws Exception {
    try (ServerSocket listener = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))) {
      int port = listener.getLocalPort();
      URL.setURLStreamHandlerFactory(protocol -> "loopback-network-test".equals(protocol)
          ? new URLStreamHandler() {
              @Override protected int getDefaultPort() { return port; }
              @Override protected URLConnection openConnection(URL url) throws IOException {
                throw new UnsupportedOperationException("No HTTP connection required");
              }
            } : null);
      String url = "loopback-network-test://127.0.0.1/";
      check(Network.checkPort(url), "URL scheme default port should be used");
      check(Network.checkPort("loopback-network-test://127.0.0.1:" + port + "/"), "explicit URL port should work");
      check(Network.checkPort("127.0.0.1", port), "host/port API should work");
    }
  }

  private static void invalidPort() {
    // Invalid port is a deterministic offline failure. Kernel accept-queue
    // saturation cannot reliably force a connection timeout across platforms.
    check(!Network.checkPort("127.0.0.1", -1), "invalid port should fail");
  }

  public static void main(String[] args) throws Exception {
    defaultPort();
    invalidPort();
    System.out.println("PASS NetworkTest");
  }
}

package com.Lomikel.Utils;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/** Offline TLS regression tests. Run each mode in a fresh JVM (shared client is JVM-owned). */
public class SmallHttpClientTlsTest {
  private static final char[] PASSWORD = "loopback-only".toCharArray();

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  public static void main(String[] args) throws Exception {
    Path store = Files.createTempFile("small-http-tls-", ".p12");
    try {
      Files.delete(store); // keytool creates its own temporary fixture, never a repository credential.
      Process keytool = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
          "-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048",
          "-validity", "1", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost",
          "-storetype", "PKCS12", "-keystore", store.toString(),
          "-storepass", new String(PASSWORD), "-keypass", new String(PASSWORD), "-noprompt")
          .redirectErrorStream(true).start();
      String output = new String(keytool.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      check(keytool.waitFor() == 0, "fixture keytool failed: " + output);

      KeyStore keys = KeyStore.getInstance("PKCS12");
      try (java.io.InputStream in = Files.newInputStream(store)) { keys.load(in, PASSWORD); }
      KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      managers.init(keys, PASSWORD);
      SSLContext serverContext = SSLContext.getInstance("TLS");
      serverContext.init(managers.getKeyManagers(), null, null);

      HttpsServer server = HttpsServer.create(new InetSocketAddress(InetAddress.getByName("localhost"), 0), 0);
      server.setHttpsConfigurator(new HttpsConfigurator(serverContext));
      server.createContext("/", exchange -> {
        try {
          byte[] body = "trusted".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
        } finally { exchange.close(); }
      });
      server.start();
      try {
        // Trusting a certificate must never silently disable hostname validation.
        if (!args[0].equals("untrusted")) {
          System.setProperty("javax.net.ssl.trustStore", store.toString());
          System.setProperty("javax.net.ssl.trustStorePassword", new String(PASSWORD));
          System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");
        }
        String host = args[0].equals("mismatch") ? "127.0.0.1" : "localhost";
        String url = "https://" + host + ":" + server.getAddress().getPort() + "/";
        if (args[0].equals("trusted")) {
          check("trusted\n".equals(SmallHttpClient.get(url)), "trusted localhost certificate rejected");
        } else if (args[0].equals("untrusted") || args[0].equals("mismatch")) {
          try {
            SmallHttpClient.get(url);
            throw new AssertionError("TLS accepted " + args[0] + " certificate");
          } catch (LomikelException expected) {
            check("HTTP request failed".equals(expected.getMessage()), "error contract changed");
          }
        } else throw new IllegalArgumentException(args[0]);
      } finally { server.stop(0); }
      System.out.println("PASS " + args[0]);
    } finally { Files.deleteIfExists(store); }
  }
}

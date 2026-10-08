package com.Lomikel.Utils;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.apache.http.HttpVersion;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;

/** Offline loopback regression tests; no external service is contacted. */
public class SmallHttpClientTest {
  private static class Fake implements AutoCloseable {
    final HttpServer server;
    volatile int status = 200;
    volatile byte[] answer = "ok".getBytes(StandardCharsets.UTF_8);
    volatile boolean gzip;
    volatile String body;
    volatile String contentType;
    Fake() throws Exception {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", this::respond);
      server.start();
    }
    void respond(HttpExchange exchange) {
      try {
        body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        byte[] bytes = answer;
        if (gzip) {
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          try (GZIPOutputStream gz = new GZIPOutputStream(out)) { gz.write(bytes); }
          bytes = out.toByteArray();
          exchange.getResponseHeaders().set("Content-Encoding", "gzip");
        }
        exchange.getResponseHeaders().set("X-Result", "token=value");
        exchange.sendResponseHeaders(status, status == 204 || status == 205 ? -1 : bytes.length);
        if (status != 204 && status != 205) exchange.getResponseBody().write(bytes);
      } catch (Exception e) { throw new RuntimeException(e); }
      finally { exchange.close(); }
    }
    String url() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/"; }
    public void close() { server.stop(0); }
  }
  private static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
  private interface Action { void run() throws Exception; }
  private static LomikelException fails(Action action) throws Exception {
    try { action.run(); } catch (LomikelException e) { return e; }
    throw new AssertionError("Expected LomikelException");
  }
  private static void utf8JSON() throws Exception {
    try (Fake fake = new Fake()) {
      String json = "{\"name\":\"café 雪\"}";
      check(SmallHttpClient.postJSON(fake.url(), json, null, null).equals("ok\n"), "POST body changed");
      check(json.equals(fake.body), "POST JSON bytes not UTF-8: " + fake.body);
      check(fake.contentType.startsWith("application/json"), "POST content type changed");
      check(SmallHttpClient.putJSON(fake.url(), json, null, null).equals("ok\n"), "PUT body changed");
      check(json.equals(fake.body), "PUT JSON bytes not UTF-8: " + fake.body);
    }
  }
  private static void statuses() throws Exception {
    try (Fake fake = new Fake()) {
      for (int status : new int[]{202, 206, 204, 205}) {
        fake.status = status;
        String expected = status == 204 || status == 205 ? "" : "ok\n";
        check(SmallHttpClient.get(fake.url()).equals(expected), "GET " + status);
        check(SmallHttpClient.delete(fake.url()).equals(expected), "DELETE " + status);
        check(SmallHttpClient.post(fake.url(), Collections.emptyMap()).equals(expected), "POST form " + status);
        check(SmallHttpClient.put(fake.url(), Collections.emptyMap()).equals(expected), "PUT form " + status);
        check(SmallHttpClient.postJSON(fake.url(), "{}", null, null).equals(expected), "POST JSON " + status);
        check(SmallHttpClient.postNDJSON(fake.url(), "{}\n", null, null).equals(expected), "POST NDJSON " + status);
        check(SmallHttpClient.postXML(fake.url(), "<x/>", null, null).equals(expected), "POST XML " + status);
        check(SmallHttpClient.putJSON(fake.url(), "{}", null, null).equals(expected), "PUT JSON " + status);
        check(SmallHttpClient.putXML(fake.url(), "<x/>", null, null).equals(expected), "PUT XML " + status);
      }
    }
  }
  private static void noEntity() throws Exception {
    BasicHttpResponse response = new BasicHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1, 204, "No Content"));
    check(SmallHttpClient.getResponseBody(response).equals(""), "null entity should be empty");
  }
  private static void closesFailedBody() throws Exception {
    BasicHttpResponse response = new BasicHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"));
    final boolean[] closed = {false};
    InputStream broken = new InputStream() {
      public int read() throws IOException { throw new IOException("read failed"); }
      public void close() { closed[0] = true; }
    };
    response.setEntity(new InputStreamEntity(broken));
    try { SmallHttpClient.getResponseBody(response); throw new AssertionError("Expected read failure"); }
    catch (IOException expected) { check(closed[0], "response stream retained after read failure"); }
  }
  private static void compatibility() throws Exception {
    try (Fake fake = new Fake()) {
      fake.answer = "one\ntwo".getBytes(StandardCharsets.UTF_8);
      fake.gzip = true;
      check(SmallHttpClient.get(fake.url()).equals("one\ntwo\n"), "gzip/newlines changed");
      check(SmallHttpClient.postJSON(fake.url(), "{}", null, "X-Result").equals("token = value\n"), "header-only changed");
      check(SmallHttpClient.putJSON(fake.url(), "{}", null, "X-Result").equals("token = value\n"), "PUT header-only changed");
    }
  }
  private static void boundedErrors() throws Exception {
    try (Fake fake = new Fake()) {
      fake.status = 503;
      String secret = "PRIVATE_MARKER_" + "z".repeat(10000);
      for (String operation : new String[]{"json", "ndjson", "put", "xml"}) {
        LomikelException e;
        switch (operation) {
          case "json": e = fails(() -> SmallHttpClient.postJSON(fake.url(), secret, null, null)); break;
          case "ndjson": e = fails(() -> SmallHttpClient.postNDJSON(fake.url(), secret, null, null)); break;
          case "put": e = fails(() -> SmallHttpClient.putJSON(fake.url(), secret, null, null)); break;
          default: e = fails(() -> SmallHttpClient.postXML(fake.url(), secret, null, null)); break;
        }
        check(!e.toString().contains(secret) && !e.toString().contains("PRIVATE_MARKER") && e.getMessage().length() < 1000,
              operation + " leaked payload");
      }
    }
  }
  private static void hostileStatusLine() throws Exception {
    String marker = "SERVER_SECRET_MARKER";
    String formSecret = "FORM_SECRET_MARKER";
    String querySecret = "QUERY_SECRET_MARKER";
    try (ServerSocket server = new ServerSocket(0, 16, java.net.InetAddress.getByName("127.0.0.1"))) {
      server.setSoTimeout(5000);
      Thread responder = new Thread(() -> {
        try {
          for (int i = 0; i < 9; i++) {
            try (Socket socket = server.accept()) {
              socket.setSoTimeout(5000);
              InputStream input = socket.getInputStream();
              int matched = 0;
              while (matched < 4) {
                int b = input.read();
                if (b < 0) throw new IOException("early request EOF");
                matched = b == "\r\n\r\n".charAt(matched) ? matched + 1 : (b == '\r' ? 1 : 0);
              }
              byte[] response = ("HTTP/1.1 503 " + marker + "x".repeat(4000) +
                                 "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
              socket.getOutputStream().write(response);
              socket.getOutputStream().flush();
            }
          }
        } catch (Exception e) { throw new RuntimeException(e); }
      });
      responder.setDaemon(true);
      responder.start();
      String url = "http://127.0.0.1:" + server.getLocalPort() + "/?key=" + querySecret;
      Map<String, String> params = Collections.singletonMap("password", formSecret);
      Action[] actions = {
        () -> SmallHttpClient.get(url), () -> SmallHttpClient.delete(url),
        () -> SmallHttpClient.post(url, params), () -> SmallHttpClient.put(url, params),
        () -> SmallHttpClient.postJSON(url, "{}", null, null),
        () -> SmallHttpClient.postNDJSON(url, "{}\n", null, null),
        () -> SmallHttpClient.postXML(url, "<x/>", null, null),
        () -> SmallHttpClient.putJSON(url, "{}", null, null),
        () -> SmallHttpClient.putXML(url, "<x/>", null, null)
      };
      for (Action action : actions) {
        LomikelException failure = fails(action);
        check(failure.getMessage().contains("503"), "HTTP status lost: " + failure);
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
          String text = cause.toString();
          check(text.length() < 512 && !text.contains(marker) && !text.contains(formSecret) &&
                !text.contains(querySecret), "unbounded or secret-bearing failure: " + text.length());
        }
      }
      responder.join(5000);
      check(!responder.isAlive(), "response server did not finish");
    }
  }
  public static void main(String[] args) throws Exception {
    switch (args[0]) {
      case "utf8JSON": utf8JSON(); break;
      case "statuses": statuses(); break;
      case "noEntity": noEntity(); break;
      case "closesFailedBody": closesFailedBody(); break;
      case "compatibility": compatibility(); break;
      case "boundedErrors": boundedErrors(); break;
      case "hostileStatusLine": hostileStatusLine(); break;
      default: throw new IllegalArgumentException(args[0]);
    }
    System.out.println("PASS " + args[0]);
  }
}

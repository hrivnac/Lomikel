package com.Lomikel.GroovyServer;

import com.sun.net.httpserver.HttpServer;
import java.net.URI;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Loopback-only HTTP regression tests; never contacts a remote service. */
public final class RemoteGroovyOfflineTest {
  private static final String TOKEN = "test-only-0123456789abcdef";
  private static void check(boolean value, String message) {
    if (!value) { throw new AssertionError(message); }
  }
  private static HttpResponse<String> request(URI url, String token, String body) throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(5));
    if (token != null) { builder.header("X-Auth-Token", token); }
    if (body == null) { builder.GET(); }
    else { builder.POST(HttpRequest.BodyPublishers.ofString(body)); }
    return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }
  public static void main(String[] args) throws Exception {
    try { RemoteGroovyServer.createServer(0, null);
          throw new AssertionError("Missing operator token must fail closed"); }
    catch (IllegalArgumentException expected) { /* expected */ }
    try { RemoteGroovyServer.createServer(0, "secret");
          throw new AssertionError("Weak default-style token must be rejected"); }
    catch (IllegalArgumentException expected) { /* expected */ }
    HttpServer server = RemoteGroovyServer.createServer(0, TOKEN);
    check(server.getAddress().getAddress().isLoopbackAddress(), "Server must bind to loopback");
    server.start();
    try {
      URI url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/eval");
      check(request(url, null, "42").statusCode() == 401, "Unauthorized POST");
      check(request(url, TOKEN, null).statusCode() == 405, "Non-POST rejected");
      try (Socket raw = new Socket("127.0.0.1", server.getAddress().getPort())) {
        raw.setSoTimeout(3000);
        String wire = "POST /eval?bad=%GG HTTP/1.1\r\nHost: 127.0.0.1\r\n" +
                      "X-Auth-Token: " + TOKEN + "\r\nContent-Length: 2\r\n" +
                      "Connection: close\r\n\r\n42";
        try (Socket valid = new Socket("127.0.0.1", server.getAddress().getPort())) {
          valid.setSoTimeout(3000);
          valid.getOutputStream().write(wire.replace("bad=%GG", "valid=ok")
                                            .getBytes(StandardCharsets.US_ASCII));
          String validStatus = new BufferedReader(new InputStreamReader(valid.getInputStream(),
                                             StandardCharsets.US_ASCII)).readLine();
          check(validStatus.contains(" 200 "), "Raw authenticated header was not accepted");
        }
        raw.getOutputStream().write(wire.getBytes(StandardCharsets.US_ASCII));
        String status = new BufferedReader(new InputStreamReader(raw.getInputStream(),
                                                        StandardCharsets.US_ASCII)).readLine();
        check(status.contains(" 400 "), "Malformed percent encoding is a client error: " + status);
      }
      HttpResponse<String> oversized = request(url, TOKEN, "x".repeat(65537));
      check(oversized.statusCode() == 413, "Bound Groovy request body");
      check("42\n".equals(request(url, TOKEN, "service.multiply(6, 7)").body()),
            "Successful formula evaluation");
      HttpResponse<String> failed = request(url, TOKEN, "throw new Exception('private detail')");
      check(failed.statusCode() == 500 && !failed.body().contains("private detail"),
            "Server exceptions must not leak details");
      RemoteGroovyClient client = new RemoteGroovyClient(url.toString(), TOKEN);
      check("42\n".equals(client.eval("service.multiply(6, 7)")), "Client round trip");
      RemoteGroovyClient exampleHost = new RemoteGroovyClient(
        "http://localhost:" + server.getAddress().getPort() + "/eval", TOKEN);
      check("42\n".equals(exampleHost.eval("service.multiply(6, 7)")),
            "Existing localhost client reaches the IPv4-only server");
      try { new RemoteGroovyClient("http://example.com/eval", TOKEN);
            throw new AssertionError("Remote plaintext token must be rejected"); }
      catch (IllegalArgumentException expected) { /* expected */ }
    }
    finally { server.stop(0); }
    System.out.println("RemoteGroovyOfflineTest PASS");
  }
}

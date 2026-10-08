package com.Lomikel.Utils;

import com.sun.net.httpserver.HttpServer;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPOutputStream;

/** Loopback-only pooling and request isolation probes. */
public class SmallHttpClientPoolTest {
  static void check(boolean ok, String description) { if (!ok) throw new AssertionError(description); }
  static void consumeHeaders(InputStream in) throws Exception {
    int match = 0;
    while (match < 4) {
      int b = in.read();
      if (b < 0) throw new AssertionError("early EOF");
      match = b == "\r\n\r\n".charAt(match) ? match + 1 : (b == '\r' ? 1 : 0);
    }
  }
  static void reuseAndReconnect() throws Exception {
    try (ServerSocket server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))) {
      server.setSoTimeout(1500);
      AtomicInteger connections = new AtomicInteger();
      Thread responder = new Thread(() -> {
        try {
          for (int i = 0; i < 2; i++) {
            try (Socket socket = server.accept()) {
              connections.incrementAndGet();
              socket.setSoTimeout(2000);
              for (int j = 0; j < (i == 0 ? 2 : 1); j++) {
                consumeHeaders(socket.getInputStream());
                byte[] bytes = ("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: " +
                                (j == 0 && i == 0 ? "keep-alive" : "close") + "\r\n\r\nok")
                               .getBytes(StandardCharsets.US_ASCII);
                socket.getOutputStream().write(bytes);
                socket.getOutputStream().flush();
              }
            }
          }
        } catch (Exception e) { throw new RuntimeException(e); }
      });
      responder.setDaemon(true);
      responder.start();
      String url = "http://127.0.0.1:" + server.getLocalPort() + "/";
      for (int i = 0; i < 3; i++) check("ok\n".equals(SmallHttpClient.get(url)), "response " + i);
      responder.join(3000);
      check(!responder.isAlive() && connections.get() == 2, "expected reuse then server-close reconnect: " + connections.get());
    }
  }
  static void concurrentIsolation() throws Exception {
    AtomicInteger active = new AtomicInteger();
    AtomicInteger peak = new AtomicInteger();
    AtomicInteger[] routeActive = {new AtomicInteger(), new AtomicInteger()};
    AtomicInteger[] routePeak = {new AtomicInteger(), new AtomicInteger()};
    Set<String> seen = Collections.synchronizedSet(new HashSet<>());
    HttpServer a = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    HttpServer b = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    ExecutorService servers = Executors.newCachedThreadPool();
    ExecutorService workers = Executors.newFixedThreadPool(24);
    for (HttpServer server : new HttpServer[]{a, b}) {
      server.setExecutor(servers);
      server.createContext("/", exchange -> {
        int n = active.incrementAndGet();
        peak.accumulateAndGet(n, Math::max);
        int route = server == a ? 0 : 1;
        int onRoute = routeActive[route].incrementAndGet();
        routePeak[route].accumulateAndGet(onRoute, Math::max);
        try {
          String auth = exchange.getRequestHeaders().getFirst("Authorization");
          seen.add(server.getAddress().getPort() + ":" + auth);
          Thread.sleep(60);
          byte[] body = auth.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
        } catch (Exception e) { throw new RuntimeException(e); }
        finally { exchange.close(); active.decrementAndGet(); routeActive[route].decrementAndGet(); }
      });
      server.start();
    }
    try {
      CountDownLatch start = new CountDownLatch(1);
      java.util.List<Future<?>> jobs = new java.util.ArrayList<>();
      for (int i = 0; i < 48; i++) {
        final int id = i;
        final int port = (i % 2 == 0 ? a : b).getAddress().getPort();
        jobs.add(workers.submit(() -> {
          start.await();
          String token = "Bearer-" + id;
          String result = SmallHttpClient.get("http://127.0.0.1:" + port + "/",
              Collections.singletonMap("Authorization", token));
          check((token + "\n").equals(result), "cross-request auth " + id + ": " + result);
          return null;
        }));
      }
      start.countDown();
      for (Future<?> job : jobs) job.get(15, TimeUnit.SECONDS);
      check(seen.size() == 48, "missing or mixed headers " + seen.size());
      check(peak.get() > 1, "requests serialized: " + peak.get());
      check(peak.get() <= 16, "pool unbounded: " + peak.get());
      check(routePeak[0].get() <= 8 && routePeak[1].get() <= 8, "per-route pool unbounded");
      check("done\n".equals(ping(a)), "pool retained a lease");
    } finally {
      workers.shutdownNow();
      a.stop(0); b.stop(0);
      servers.shutdownNow();
    }
  }
  static String ping(HttpServer server) throws Exception {
    // The existing handler echoes the Authorization header.
    return SmallHttpClient.get("http://127.0.0.1:" + server.getAddress().getPort() + "/",
                               Collections.singletonMap("Authorization", "done"));
  }
  static void leaseReleaseOnAllPaths() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    ExecutorService executor = Executors.newCachedThreadPool();
    server.createContext("/", exchange -> {
      try {
        exchange.getRequestBody().readAllBytes();
        String path = exchange.getRequestURI().getPath();
        exchange.getResponseHeaders().set("X-Result", "token=value");
        int status = path.equals("/fail") ? 503 : path.equals("/empty") ? 204 : 200;
        byte[] bytes = "ok".getBytes(StandardCharsets.UTF_8);
        if (path.equals("/gzip")) {
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          try (GZIPOutputStream gzip = new GZIPOutputStream(out)) { gzip.write(bytes); }
          bytes = out.toByteArray();
          exchange.getResponseHeaders().set("Content-Encoding", "gzip");
        }
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
        if (status != 204) exchange.getResponseBody().write(bytes);
      } catch (Exception e) { throw new RuntimeException(e); }
      finally { exchange.close(); }
    });
    server.setExecutor(executor);
    server.start();
    try {
      String url = "http://127.0.0.1:" + server.getAddress().getPort();
      for (int i = 0; i < 24; i++) {
        try { SmallHttpClient.get(url + "/fail"); throw new AssertionError("expected status failure"); }
        catch (LomikelException e) { check(e.getMessage().contains("503"), "lost status"); }
        check("".equals(SmallHttpClient.get(url + "/empty")), "bodyless response");
        check("token = value\n".equals(SmallHttpClient.postJSON(url + "/header", "{}", null, "X-Result")), "header-only response");
        check("ok\n".equals(SmallHttpClient.get(url + "/gzip")), "gzip response");
      }
      check("ok\n".equals(SmallHttpClient.get(url + "/")), "leases exhausted");
    } finally { server.stop(0); executor.shutdownNow(); }
  }
  static void cookieIsolation() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicInteger requests = new AtomicInteger();
    server.createContext("/", exchange -> {
      int index = requests.incrementAndGet();
      String cookie = exchange.getRequestHeaders().getFirst("Cookie");
      if (index == 1) exchange.getResponseHeaders().set("Set-Cookie", "session=secret; Path=/");
      byte[] bytes = (cookie == null ? "none" : cookie).getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
      check("none\n".equals(SmallHttpClient.get(url)), "first request cookie");
      check("none\n".equals(SmallHttpClient.get(url)), "cookie leaked into next request");
    } finally { server.stop(0); }
  }
  public static void main(String[] args) throws Exception {
    if (args[0].equals("reuse")) reuseAndReconnect();
    else if (args[0].equals("concurrent")) concurrentIsolation();
    else if (args[0].equals("leases")) leaseReleaseOnAllPaths();
    else if (args[0].equals("cookies")) cookieIsolation();
    else throw new IllegalArgumentException(args[0]);
    System.out.println("PASS " + args[0]);
  }
}

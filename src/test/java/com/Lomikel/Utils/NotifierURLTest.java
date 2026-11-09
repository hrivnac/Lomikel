package com.Lomikel.Utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Offline regression tests: the connection factory never opens the fixed telemetry URL. */
public class NotifierURLTest {
  private static void check(boolean result, String message) {
    if (!result) throw new AssertionError(message);
  }
  private static class Connection extends HttpURLConnection {
    boolean closed;
    boolean disconnected;
    boolean fail;
    boolean errorClosed;
    String method;
    Connection(URL url) { super(url); }
    @Override public void disconnect() { disconnected = true; }
    @Override public boolean usingProxy() { return false; }
    @Override public void connect() { connected = true; }
    @Override public void setRequestMethod(String value) { method = value; }
    @Override public InputStream getInputStream() throws IOException {
      if (fail) throw new IOException("deliberate failure");
      return new ByteArrayInputStream(new byte[0]) {
        @Override public void close() throws IOException { closed = true; super.close(); }
      };
    }
    @Override public InputStream getErrorStream() {
      return new ByteArrayInputStream(new byte[0]) {
        @Override public void close() throws IOException { errorClosed = true; super.close(); }
      };
    }
    @Override public int getResponseCode() { return 200; }
    @Override public String getResponseMessage() { return "OK"; }
  }
  private static void blockingCleanup() throws Exception {
    Connection[] seen = new Connection[1];
    NotifierURL.connectionFactory = url -> { seen[0] = new Connection(url); return seen[0]; };
    try {
      NotifierURL.notifyExecution("a b", "source", "release", "é &");
      check(seen[0].getURL().toString().contains("page=source_a_b_release&text=%C3%A9+%26"), "URL encoding changed");
      check(seen[0].getConnectTimeout() > 0 && seen[0].getReadTimeout() > 0, "network timeouts missing");
      check("GET".equals(seen[0].method) && seen[0].closed && seen[0].disconnected, "successful stream/connection not cleaned");
    } finally { NotifierURL.connectionFactory = url -> (HttpURLConnection)url.openConnection(); }
  }
  private static void failureCleanup() throws Exception {
    Connection[] seen = new Connection[1];
    NotifierURL.connectionFactory = url -> { seen[0] = new Connection(url); seen[0].fail = true; return seen[0]; };
    try {
      NotifierURL.notifyExecution("m", "s", "r", null);
      check(seen[0].errorClosed && seen[0].disconnected, "failed request retains error stream or connection");
    } finally { NotifierURL.connectionFactory = url -> (HttpURLConnection)url.openConnection(); }
  }
  private static void boundedAsync() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger opened = new AtomicInteger();
    NotifierURL.connectionFactory = url -> {
      opened.incrementAndGet();
      return new Connection(url) {
        @Override public InputStream getInputStream() throws IOException {
          started.countDown();
          try { if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("test timed out"); }
          catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
          return super.getInputStream();
        }
      };
    };
    try {
      NotifierURL.notify("blocked", "s", "r", null);
      check(started.await(2, TimeUnit.SECONDS), "notification did not start");
      long before = System.nanoTime();
      for (int i = 0; i < 500; i++) NotifierURL.notify("queued", "s", "r", null);
      check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before) < 2000, "notify blocked on saturated queue");
      int workers = 0;
      for (Thread thread : Thread.getAllStackTraces().keySet()) {
        if (thread.getName().startsWith("NotifierURL-")) {
          workers++;
          check(thread.isDaemon(), "notification worker could keep JVM alive");
        }
      }
      check(workers > 0 && workers <= 4, "notification threads unbounded: " + workers);
      java.lang.reflect.Field field = NotifierURL.class.getDeclaredField("executor");
      field.setAccessible(true);
      java.util.concurrent.ThreadPoolExecutor pool = (java.util.concurrent.ThreadPoolExecutor)field.get(null);
      check(pool.getQueue().size() <= 64, "notification backlog unbounded");
      release.countDown();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while ((pool.getActiveCount() != 0 || !pool.getQueue().isEmpty()) && System.nanoTime() < deadline) {
        Thread.sleep(10);
      }
      check(pool.getActiveCount() == 0 && pool.getQueue().isEmpty(), "worker did not drain offline queue");
      check(opened.get() == 65, "accepted notifications were lost or queue was unbounded: " + opened.get());
    } finally {
      release.countDown();
    }
  }
  /** A short program (e.g. a Groovy script) calls notify() and ends at once: the queued
    * notification must still be sent before the JVM exits. Runs a child JVM against a local server. */
  private static void sentBeforeExit() throws Exception {
    childAgainst(300, 30, 1);
  }
  /** A hanging monitoring server must not keep a finished program alive for long. */
  private static void exitBounded() throws Exception {
    long before = System.nanoTime();
    childAgainst(60000, 30, 0);
    long seconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - before);
    check(seconds < 20, "program exit delayed too long by a hanging server: " + seconds + " s");
  }
  private static void childAgainst(long serverDelayMs, int childLimitS, int expectedHits) throws Exception {
    com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
        new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0);
    server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; }));
    AtomicInteger hits = new AtomicInteger();
    server.createContext("/", exchange -> {
      try { Thread.sleep(serverDelayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
      hits.incrementAndGet();
      exchange.sendResponseHeaders(200, -1);
      exchange.close();
    });
    server.start();
    try {
      String java = System.getProperty("java.home") + "/bin/java";
      Process child = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                                         NotifierURLTest.class.getName(), "childNotifyAndExit",
                                         "http://127.0.0.1:" + server.getAddress().getPort() + "/")
                          .inheritIO().start();
      check(child.waitFor(childLimitS, TimeUnit.SECONDS), "child JVM did not exit (notification blocks shutdown)");
      check(child.exitValue() == 0, "child JVM failed");
      check(hits.get() == expectedHits, "notification lost when the program ended right after notify(): " + hits.get());
    } finally {
      server.stop(0);
    }
  }
  private static void childNotifyAndExit(String target) {
    NotifierURL.connectionFactory = url -> (HttpURLConnection)new URL(target).openConnection();
    NotifierURL.notify("aaa", "Lomikel", "test");
    // main returns immediately, as a Groovy script does after its last statement
  }
  public static void main(String[] args) throws Exception {
    switch (args[0]) {
      case "sentBeforeExit": sentBeforeExit(); break;
      case "exitBounded": exitBounded(); break;
      case "childNotifyAndExit": childNotifyAndExit(args[1]); return;
      case "blockingCleanup": blockingCleanup(); break;
      case "failureCleanup": failureCleanup(); break;
      case "boundedAsync": boundedAsync(); break;
      default: throw new IllegalArgumentException(args[0]);
    }
    System.out.println("PASS " + args[0]);
  }
}

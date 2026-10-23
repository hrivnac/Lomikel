package com.Lomikel.Utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Standalone offline regression tests; run with java com.Lomikel.Utils.IDFactoryTest. */
public class IDFactoryTest {

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static void repeatedCallsAreUnique() {
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < 10_000; i++) {
      String id = IDFactory.newID();
      check(ids.add(id), "duplicate ID: " + id);
    }
  }

  private static void rollbackDoesNotReuseIDs() {
    Set<String> ids = new HashSet<>();
    long[] times = {1_700_000_000_000L, 1_700_000_000_000L,
                    1_699_999_999_000L, 1_700_000_000_000L};
    for (long time : times) {
      String id = IDFactory.newID(time);
      String prefix = System.getProperty("user.name") + "/" +
                      new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss.SSS").format(new Date(time));
      check(id.matches(java.util.regex.Pattern.quote(prefix) +
                       "-[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}-[0-9]+"),
            "lost username/timestamp prefix or nonce/sequence suffix: " + id);
      check(ids.add(id), "clock rollback reused ID: " + id);
    }
  }

  private static void concurrentCallsAreUnique() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(8);
    CountDownLatch start = new CountDownLatch(1);
    Set<String> ids = java.util.Collections.synchronizedSet(new HashSet<>());
    try {
      Future<?>[] jobs = new Future<?>[8];
      for (int worker = 0; worker < jobs.length; worker++) {
        jobs[worker] = pool.submit(() -> {
          start.await();
          for (int i = 0; i < 2_000; i++) {
            String id = IDFactory.newID();
            check(ids.add(id), "concurrent duplicate ID: " + id);
          }
          return null;
        });
      }
      start.countDown();
      for (Future<?> job : jobs) job.get(30, TimeUnit.SECONDS);
      check(ids.size() == 16_000, "missing concurrent IDs");
    } finally {
      pool.shutdownNow();
    }
  }

  private static String idFromSeparateJvm(long timestamp, String username) throws Exception {
    Process child = new ProcessBuilder(
        Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
        "-Duser.name=" + username, "-cp", System.getProperty("java.class.path"),
        IDFactoryTest.class.getName(), "child", Long.toString(timestamp))
        .redirectErrorStream(true).start();
    try {
      check(child.waitFor(30, TimeUnit.SECONDS), "child JVM timed out");
      String line;
      try (BufferedReader output = new BufferedReader(
          new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
        line = output.readLine();
      }
      check(child.exitValue() == 0, "child JVM failed: " + line);
      return line;
    } finally {
      child.destroyForcibly();
    }
  }

  private static void separateJvmsDoNotReuseIds() throws Exception {
    long timestamp = 1_700_000_000_000L;
    String username = "same-user";
    String first = idFromSeparateJvm(timestamp, username);
    String second = idFromSeparateJvm(timestamp, username);
    String prefix = username + "/" +
                    new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss.SSS").format(new Date(timestamp));
    check(first.startsWith(prefix + "-") && second.startsWith(prefix + "-"),
          "lost username/timestamp prefix: " + first + ", " + second);
    check(!first.equals(second), "separate JVMs reused ID: " + first);
  }

  public static void main(String[] args) throws Exception {
    if (args.length > 0 && args[0].equals("child")) {
      System.out.println(IDFactory.newID(Long.parseLong(args[1])));
      return;
    }
    repeatedCallsAreUnique();
    rollbackDoesNotReuseIDs();
    concurrentCallsAreUnique();
    separateJvmsDoNotReuseIds();
    System.out.println("IDFactoryTest passed");
  }
}

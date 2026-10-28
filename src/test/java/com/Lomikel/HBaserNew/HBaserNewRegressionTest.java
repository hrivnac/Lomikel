package com.Lomikel.HBaserNew;

import com.Lomikel.Utils.LomikelException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import sun.misc.Unsafe;

/** Offline regression checks for the isolated HBaserNew package. */
public class HBaserNewRegressionTest {
  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static void shortRoundTrip() {
    HBaseSchema schema = new HBaseSchema("test", Map.of("r:short", "short"));
    byte[] bytes = schema.encode("r:short", "1234").bytes();
    require("1234".equals(schema.decode("r:short", bytes)), "short decode must match encode");
    require("1234".equals(schema.decode2Content("r:short", bytes).asString()),
            "CellContent short decode must match encode");
  }

  private static void repositoryContainsWrites() throws Exception {
    Path root = Files.createTempDirectory("hbasernew-repository-");
    try {
      Path dir = root.resolve("store");
      FilesBinaryDataRepository repo = new FilesBinaryDataRepository(dir.toString());
      repo.put("normal", new byte[]{1, 2, 3});
      require(java.util.Arrays.equals(new byte[]{1, 2, 3}, repo.get("normal")),
              "repository round-trip failed");
      boolean rejected = false;
      try { repo.put("../escaped", new byte[]{4}); }
      catch (IllegalArgumentException expected) { rejected = true; }
      require(rejected && !Files.exists(root.resolve("escaped")),
              "repository must reject path traversal before writing outside its directory");
    } finally {
      try (var paths = Files.walk(root)) {
        paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
          try { Files.deleteIfExists(path); }
          catch (java.io.IOException error) { throw new RuntimeException(error); }
        });
      }
    }
  }

  public static class PausedAsyncClient extends AsynchHBaseClient {
    CountDownLatch entered;
    CountDownLatch release;
    public PausedAsyncClient() throws LomikelException { super("unused", "2181"); }
    @Override public void run() {
      entered.countDown();
      try { release.await(3, TimeUnit.SECONDS); }
      catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
  }

  private static PausedAsyncClient unconnectedAsyncClient() throws Exception {
    Field field = Unsafe.class.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    Unsafe unsafe = (Unsafe)field.get(null);
    PausedAsyncClient client = (PausedAsyncClient)unsafe.allocateInstance(PausedAsyncClient.class);
    client.entered = new CountDownLatch(1);
    client.release = new CountDownLatch(1);
    return client;
  }

  private static void repeatedStartDoesNotSpawnWorker() throws Exception {
    PausedAsyncClient client = unconnectedAsyncClient();
    Field threadField = AsynchHBaseClient.class.getDeclaredField("_thread");
    threadField.setAccessible(true);
    Thread first = null, second = null;
    try {
      client.startScan(null, null, "*", 0, 0, false, false);
      require(client.entered.await(2, TimeUnit.SECONDS), "worker did not start");
      first = (Thread)threadField.get(client);
      client.startScan(null, null, "*", 0, 0, false, false);
      second = (Thread)threadField.get(client);
      require(first == second, "ignored scan spawned a second worker");
    } finally {
      client.release.countDown();
      if (first != null) first.join(3000);
      if (second != null && second != first) second.join(3000);
    }
    require(!first.isAlive(), "test worker did not exit");
  }

  private static void stopBeforeFirstScanIsSafe() throws Exception {
    PausedAsyncClient client = unconnectedAsyncClient();
    client.stop(true);
    require(!client.scanPending(), "stopping an idle client left a scan pending");
  }

  private static void asynchronousResultsRetainRowKeys() {
    var queue = new ConcurrentLinkedQueue<Map.Entry<String, Map<String, String>>>();
    var processor = new AsynchHBaseProcessor(queue);
    Map<String, Map<String, String>> rows = new TreeMap<>();
    rows.put("row-42", new TreeMap<>(Map.of("r:flux", "17")));
    processor.processResults(rows);
    require(queue.peek() != null, "processor did not queue row");
    try {
      PausedAsyncClient client = unconnectedAsyncClient();
      Field queueField = AsynchHBaseClient.class.getDeclaredField("_queue");
      queueField.setAccessible(true);
      queueField.set(client, queue);
      Map<String, Map<String, String>> polled = client.poll(1);
      require(polled.containsKey("row-42"), "poll must identify the row without ifkey");
      require(!polled.get("row-42").containsKey("key:key"),
              "ifkey=false must not add a key:key column to the returned row");
    } catch (Exception e) {
      throw new AssertionError("could not poll queued row", e);
    }
  }

  public static class ControlledAsyncClient extends AsynchHBaseClient {
    CountDownLatch firstRow;
    CountDownLatch release;
    java.util.concurrent.atomic.AtomicInteger interrupts;
    public ControlledAsyncClient() throws LomikelException { super("unused", "2181"); }
    @Override public Map<String, Map<String, String>> scan(String key, String search,
        String filter, long start, long stop, boolean ifkey, boolean iftime) {
      processResults(new TreeMap<>(Map.of("row-1", new TreeMap<>(Map.of("r:v", "1")))));
      firstRow.countDown();
      while (release.getCount() > 0) {
        try { release.await(100, TimeUnit.MILLISECONDS); }
        catch (InterruptedException ignored) {
          interrupts.incrementAndGet(); /* Model a scanner that does not stop on interrupt. */
        }
      }
      processResults(new TreeMap<>(Map.of("row-2", new TreeMap<>(Map.of("r:v", "2")))));
      return Map.of();
    }
  }

  private static ControlledAsyncClient controlledClient() throws Exception {
    Field field = Unsafe.class.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    ControlledAsyncClient client = (ControlledAsyncClient)((Unsafe)field.get(null))
        .allocateInstance(ControlledAsyncClient.class);
    client.firstRow = new CountDownLatch(1);
    client.release = new CountDownLatch(1);
    client.interrupts = new java.util.concurrent.atomic.AtomicInteger();
    client._queue = new ConcurrentLinkedQueue<>();
    client._processor = new AsynchHBaseProcessor(client._queue);
    client.setLoopWait(10);
    return client;
  }

  private static void cancellationPreventsLateQueuedRows() throws Exception {
    ControlledAsyncClient client = controlledClient();
    try {
      client.startScan(null, null, "*", 0, 0, false, false);
      require(client.firstRow.await(2, TimeUnit.SECONDS), "first row not processed");
      require(client.size() == 1, "first row not queued");
      client.stop(false);
      client.release.countDown();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while (client.scanPending() && System.nanoTime() < deadline) Thread.sleep(5);
      require(!client.scanPending(), "scan did not finish after cancellation");
      require(client.size() == 0, "cancelled scan enqueued rows after stop returned");
    } finally {
      client.release.countDown();
      client.stop(true);
    }
  }

  private static void rejectedTimedScanCannotInterruptOwner() throws Exception {
    ControlledAsyncClient client = controlledClient();
    try {
      client.startScan(null, null, "*", 0, 0, false, false);
      require(client.firstRow.await(2, TimeUnit.SECONDS), "first scan did not start");
      boolean rejected = false;
      try { client.scan(null, null, "*", 0, 0, false, false, 0); }
      catch (IllegalStateException expected) { rejected = true; }
      Thread.sleep(20);
      require(rejected, "timed scan must explicitly reject concurrent admission");
      require(client.interrupts.get() == 0 && client.scanPending(),
              "rejected timed scan interrupted a worker it did not own");
    } finally {
      client.release.countDown();
      client.stop(true);
    }
  }

  private static void timedScanRejectsUnconsumedOlderRows() throws Exception {
    ControlledAsyncClient client = controlledClient();
    client._queue.add(new java.util.AbstractMap.SimpleImmutableEntry<>("old-row",
        new TreeMap<>(Map.of("r:v", "old"))));
    boolean rejected = false;
    try { client.scan(null, null, "*", 0, 0, false, false, 0); }
    catch (IllegalStateException expected) { rejected = true; }
    require(rejected, "timed scan must not return rows retained from an older scan");
    require(!client.scanPending() && client.poll(1).containsKey("old-row"),
            "rejected timed scan must leave prior results available to their owner");
  }

  public static class OverfullAsyncClient extends AsynchHBaseClient {
    CountDownLatch entered;
    CountDownLatch completed;
    public OverfullAsyncClient() throws LomikelException { super("unused", "2181"); }
    @Override public Map<String, Map<String, String>> scan(String key, String search,
        String filter, long start, long stop, boolean ifkey, boolean iftime) {
      Map<String, Map<String, String>> rows = new TreeMap<>();
      rows.put("one", new TreeMap<>(Map.of("r:v", "1")));
      rows.put("two", new TreeMap<>(Map.of("r:v", "2")));
      entered.countDown();
      processResults(rows);
      completed.countDown();
      return Map.of();
    }
  }

  private static void stopRetainingOverfullQueueTerminatesWorker() throws Exception {
    Field field = Unsafe.class.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    OverfullAsyncClient client = (OverfullAsyncClient)((Unsafe)field.get(null))
        .allocateInstance(OverfullAsyncClient.class);
    client.entered = new CountDownLatch(1);
    client.completed = new CountDownLatch(1);
    client._queue = new ConcurrentLinkedQueue<>();
    client._processor = new AsynchHBaseProcessor(client._queue);
    client._processor.setMaxQueueSize(0);
    try {
      client.startScan(null, null, "*", 0, 0, false, false);
      require(client.entered.await(2, TimeUnit.SECONDS), "overfull scan did not enter");
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while (client.size() < 1 && System.nanoTime() < deadline) Thread.sleep(5);
      require(client.size() >= 1, "processor never reached backpressure");
      client.stop(true);
      require(client.completed.await(2, TimeUnit.SECONDS),
              "stop(true) left an overfull processor sleeping after cancellation");
      require(client.size() == 1, "stop(true) must preserve rows already queued");
    } finally {
      client.stop(false);
    }
  }

  public static class FailingAsyncClient extends AsynchHBaseClient {
    public FailingAsyncClient() throws LomikelException { super("unused", "2181"); }
    @Override public Map<String, Map<String, String>> scan(String key, String search,
        String filter, long start, long stop, boolean ifkey, boolean iftime) {
      throw new IllegalStateException("offline synthetic scan failure");
    }
  }

  private static void failureIsPublishedBeforeCompletion() throws Exception {
    Field field = Unsafe.class.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    FailingAsyncClient client = (FailingAsyncClient)((Unsafe)field.get(null))
        .allocateInstance(FailingAsyncClient.class);
    client._queue = new ConcurrentLinkedQueue<>();
    client._processor = new AsynchHBaseProcessor(client._queue);
    client.startScan(null, null, "*", 0, 0, false, false);
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    while (client.scanPending() && System.nanoTime() < deadline) Thread.sleep(1);
    require(!client.scanPending(), "failed scan did not complete");
    require(client.scanFailure() instanceof IllegalStateException,
            "scan failure must be visible when scanPending becomes false");
  }

  private static void repositoriesHaveSeparateMemory() {
    BinaryDataRepository first = new BinaryDataRepository();
    first.put("source", new byte[]{7});
    BinaryDataRepository second = new BinaryDataRepository();
    second.put("other", new byte[]{8});
    require(java.util.Arrays.equals(first.get("source"), new byte[]{7}),
            "constructing another repository erased the first client's binary data");
    require(second.get("source") == null, "repositories must not share entries");
    first.clear();
    require(java.util.Arrays.equals(second.get("other"), new byte[]{8}),
            "one repository must not clear another client's data");
  }

  public static void main(String[] args) throws Exception {
    shortRoundTrip();
    repositoryContainsWrites();
    repeatedStartDoesNotSpawnWorker();
    stopBeforeFirstScanIsSafe();
    asynchronousResultsRetainRowKeys();
    cancellationPreventsLateQueuedRows();
    rejectedTimedScanCannotInterruptOwner();
    timedScanRejectsUnconsumedOlderRows();
    stopRetainingOverfullQueueTerminatesWorker();
    failureIsPublishedBeforeCompletion();
    repositoriesHaveSeparateMemory();
    System.out.println("HBaserNew offline regression checks passed");
  }
}

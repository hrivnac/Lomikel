package com.Lomikel.ElasticSearcher;

import com.Lomikel.Utils.LomikelException;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.json.JSONArray;

/** Loopback-only behavioral contract and request-count benchmark. */
public class ESClientBufferedDoubleTest {
  static class Fixture implements AutoCloseable {
    final HttpServer server;
    final List<String> bodies = new ArrayList<>();
    final List<String> paths = new ArrayList<>();
    int failId = -1;
    boolean malformed;
    String forcedAnswer;
    int httpStatus = 200;
    Fixture() throws Exception {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", this::respond);
      server.start();
    }
    void respond(HttpExchange ex) {
      try {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        bodies.add(body); paths.add(ex.getRequestURI().getPath());
        String response;
        if (ex.getRequestURI().getPath().equals("/_bulk")) {
          JSONArray items = new JSONArray();
          String[] lines = body.split("\n");
          for (int i = 0; i < lines.length; i += 2) {
            JSONObject meta = new JSONObject(lines[i]).getJSONObject("update");
            String id = meta.getString("_id");
            int status = id.equals("id" + failId) ? 429 : 200;
            items.put(new JSONObject().put("update", new JSONObject().put("_id", id)
                .put("_index", meta.getString("_index")).put("status", status)
                .put("result", status == 200 ? "updated" : "error")));
          }
          response = forcedAnswer != null ? forcedAnswer : malformed ? "not-json" :
              new JSONObject().put("errors", failId >= 0).put("items", items).toString();
        } else if (ex.getRequestMethod().equals("GET")) {
          String index = ex.getRequestURI().getPath().substring(1);
          response = new JSONObject().put(index, new JSONObject()).toString();
        } else response = "{\"result\":\"updated\"}";
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(httpStatus, bytes.length);
        ex.getResponseBody().write(bytes); ex.close();
      } catch (Exception e) { throw new RuntimeException(e); }
    }
    ESClient client() { return new ESClient("http://127.0.0.1:" + server.getAddress().getPort()); }
    public void close() { server.stop(0); }
  }
  static void check(boolean b, String message) { if (!b) throw new AssertionError(message); }
  static void fails(RunnableWithException r) throws Exception {
    try { r.run(); } catch (LomikelException expected) { return; }
    throw new AssertionError("Expected failure");
  }
  interface RunnableWithException { void run() throws Exception; }
  static void grouping() throws Exception {
    try (Fixture f = new Fixture()) {
      ESClient es = f.client();
      es.bufferDoubleArray("a", "mjd", "id0", 1);
      es.bufferDoubleArray("a", "mjd", "id0", 2);
      es.bufferDoubleArray("a", "mjd", "id0", 1);
      es.bufferDoubleArray("b", "other", "id1", 3);
      check(f.paths.isEmpty(), "buffer sent prematurely");
      es.flushBufferedDoubleArrays(2);
      check(f.paths.size() == 1 && f.paths.get(0).equals("/_bulk"), "not one bulk request");
      String[] lines = f.bodies.get(0).split("\n");
      check(lines.length == 4, "not grouped by document");
      JSONObject first = new JSONObject(lines[1]);
      JSONArray values = first.getJSONObject("script").getJSONObject("params").getJSONArray("values");
      check(values.length() == 2 && values.getDouble(0) == 1 && values.getDouble(1) == 2,
          "values duplicated or reordered");
      String source = first.getJSONObject("script").getString("source");
      check(source.contains("changed = true") && source.contains("if (!changed) { ctx.op = 'noop'; }"),
          "a later duplicate could suppress an earlier insertion");
      check(first.getBoolean("scripted_upsert") && first.getJSONObject("upsert").length() == 0,
          "not an idempotent upsert");
      check(first.getJSONObject("script").getString("source").contains("contains(v)"), "replay not guarded");
      es.flushBufferedDoubleArrays(1);
      check(f.paths.size() == 1, "successful buffer retained");
    }
  }
  static void partialAndMalformed() throws Exception {
    try (Fixture f = new Fixture()) {
      ESClient es = f.client();
      es.bufferDoubleArray("a", "mjd", "id0", 1);
      es.bufferDoubleArray("a", "mjd", "id1", 2);
      f.failId = 1;
      fails(() -> es.flushBufferedDoubleArrays(2));
      check(f.bodies.size() == 2 && f.bodies.get(0).split("\n").length == 4 &&
            f.bodies.get(1).split("\n").length == 2 && f.bodies.get(1).contains("id1") &&
            !f.bodies.get(1).contains("id0"), "retried successful item");
      f.failId = -1;
      f.malformed = true;
      fails(() -> es.flushBufferedDoubleArrays(1));
      f.malformed = false;
      es.flushBufferedDoubleArrays(1);
      check(f.bodies.get(3).split("\n").length == 2, "lost pending item on malformed response");
      es.flushBufferedDoubleArrays(1);
      check(f.bodies.size() == 4, "pending items not cleared");
    }
  }
  static void boundsAndBenchmark() throws Exception {
    try (Fixture f = new Fixture()) {
      ESClient es = f.client();
      for (int i = 0; i < 600; i++) es.bufferDoubleArray("idx", "mjd", "id" + i, i);
      es.flushBufferedDoubleArrays(2);
      int bulkRequests = f.bodies.size();
      check(bulkRequests > 1 && bulkRequests < 600, "missing bounded automatic flush");
      for (String body : f.bodies) check(body.getBytes(StandardCharsets.UTF_8).length <= 65536,
          "request byte cap exceeded");
      for (int i = 0; i < 600; i++) es.updateDoubleArray("idx", "mjd", "id" + i, i);
      check(f.paths.size() == bulkRequests + 600, "baseline request count wrong");
      System.out.println("BENCHMARK loopback 600 distinct IDs: synchronous=600 buffered=" + bulkRequests);
    }
    try (Fixture f = new Fixture()) {
      ESClient es = f.client();
      for (int i = 0; i < 600; i++) es.bufferDoubleArray("idx", "mjd", "id" + (i % 12), i);
      es.flushBufferedDoubleArrays(2);
      check(f.bodies.size() < 20, "grouped-document workload sent too many requests");
      System.out.println("BENCHMARK loopback 600 updates / 12 IDs: synchronous=600 buffered=" + f.bodies.size());
    }
  }
  static void malformedItemsRetained() throws Exception {
    String[] answers = {"{\"errors\":false,\"items\":[]}",
        "{\"errors\":false,\"items\":[{\"update\":{\"_index\":\"idx\",\"_id\":\"wrong\",\"status\":200,\"result\":\"updated\"}}]}",
        "{\"errors\":false,\"items\":[{\"update\":{\"_index\":\"idx\",\"_id\":\"id0\",\"status\":200}}]}",
        "{\"errors\":true,\"items\":[{\"update\":{\"_index\":\"idx\",\"_id\":\"id0\",\"status\":200,\"result\":\"updated\"}}]}"};
    for (String answer : answers) try (Fixture f = new Fixture()) {
      ESClient es = f.client();
      es.bufferDoubleArray("idx", "mjd", "id0", 1);
      f.forcedAnswer = answer;
      fails(() -> es.flushBufferedDoubleArrays(2));
      check(f.bodies.size() == 1, "malformed response should fail closed");
      f.forcedAnswer = null;
      es.flushBufferedDoubleArrays(1);
      check(f.bodies.size() == 2 && f.bodies.get(0).equals(f.bodies.get(1)),
          "malformed response lost pending update");
    }
  }
  static void transportAndLimit() throws Exception {
    try (Fixture f = new Fixture()) {
      ESClient es = f.client();
      es.bufferDoubleArray("idx", "mjd", "id0", 1);
      f.httpStatus = 503;
      fails(() -> es.flushBufferedDoubleArrays(2));
      check(f.bodies.size() == 2, "transport retries not bounded");
      f.httpStatus = 200;
      es.flushBufferedDoubleArrays(1);
      check(f.bodies.size() == 3 && f.bodies.get(0).equals(f.bodies.get(2)),
          "ambiguous transport failure discarded buffered update");
    }
    try (Fixture f = new Fixture()) {
      ESClient es = f.client();
      boolean rejected = false;
      try { es.bufferDoubleArray("idx", "mjd", "x".repeat(66000), 1); }
      catch (IllegalArgumentException expected) { rejected = true; }
      check(rejected && f.paths.isEmpty(), "oversized single update sent to server");
    }
  }
  static void longSingleDocument() throws Exception {
    try (Fixture f = new Fixture()) {
      ESClient es = f.client();
      long start = System.nanoTime();
      for (int i = 0; i < 20000; i++) es.bufferDoubleArray("idx", "mjd", "id0", i);
      es.flushBufferedDoubleArrays(2);
      check(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 20,
          "20,000-value admission/flush exceeded 20 seconds");
      check(f.bodies.size() > 1, "long document did not rotate into chunks");
      int next = 0;
      for (String body : f.bodies) {
        check(body.getBytes(StandardCharsets.UTF_8).length <= 65536, "chunk exceeded byte cap");
        String[] lines = body.split("\n");
        check(lines.length == 2, "long document produced unexpected bulk actions");
        JSONArray values = new JSONObject(lines[1]).getJSONObject("script")
            .getJSONObject("params").getJSONArray("values");
        for (int j = 0; j < values.length(); j++)
          check(values.getInt(j) == next++, "chunks lost or reordered values");
      }
      check(next == 20000, "long document lost values");
    }
    try (Fixture f = new Fixture()) {
      ESClient es = f.client();
      f.httpStatus = 503;
      boolean failed = false;
      for (int i = 0; i < 20000; i++) {
        try { es.bufferDoubleArray("idx", "mjd", "id0", i); }
        catch (LomikelException expected) { failed = true; break; }
      }
      check(failed && !f.bodies.isEmpty(), "rotation failure was not reported");
      String pending = f.bodies.get(0);
      for (String body : f.bodies)
        check(body.equals(pending), "failed rotation changed the replayable chunk");
      f.httpStatus = 200;
      es.flushBufferedDoubleArrays(2);
      check(f.bodies.get(f.bodies.size() - 1).equals(pending),
          "retry did not replay the exact failed chunk");
      JSONArray values = new JSONObject(pending.split("\n")[1])
          .getJSONObject("script").getJSONObject("params").getJSONArray("values");
      check(values.length() > 5000 && values.getInt(0) == 0,
          "failed rotation discarded the pending chunk");
    }
  }
  static void lifecycleWithPendingBufferedDoubles() throws Exception {
    try (Fixture f = new Fixture()) {
      ESClient es = f.client();
      es.bufferDoubleArray("idx", "mjd", "id0", 1);
      fails(() -> es.deleteIndex("idx"));
      fails(() -> es.cleanIndex("idx"));
      check(f.paths.isEmpty(), "destructive operation sent a request despite pending buffer");
      es.flushBufferedDoubleArrays(1);
      check(f.paths.equals(List.of("/_bulk")), "pending buffer was not replayable");
      es.bufferDoubleArray("other", "mjd", "id0", 2);
      fails(() -> es.deleteIndex("other"));
      fails(() -> es.cleanIndex("other"));
      check(f.paths.equals(List.of("/_bulk")), "pending other index was preflighted");
    }
  }
  static void lifecycleAdmissionRace() throws Exception {
    for (boolean clean : new boolean[]{false, true}) {
      CountDownLatch lookup = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      CountDownLatch admissionStarted = new CountDownLatch(1);
      AtomicReference<Throwable> failure = new AtomicReference<>();
      try (Fixture f = new Fixture()) {
        f.server.createContext("/idx", ex -> {
          try {
            if (ex.getRequestMethod().equals("GET")) {
              lookup.countDown();
              if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("lookup wait timed out");
            }
            String response = ex.getRequestMethod().equals("GET") ? "{\"idx\":{}}" :
                clean ? "{\"total\":0,\"deleted\":0,\"version_conflicts\":0,\"failures\":[],\"timed_out\":false}" :
                        "{\"acknowledged\":true}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
          } catch (Exception e) { failure.compareAndSet(null, e); ex.close(); }
        });
        ESClient es = f.client();
        Thread destructive = new Thread(() -> {
          try { if (clean) es.cleanIndex("idx"); else es.deleteIndex("idx"); }
          catch (Throwable t) { failure.compareAndSet(null, t); }
        });
        Thread admission = new Thread(() -> {
          admissionStarted.countDown();
          try { es.bufferDoubleArray("idx", "mjd", "id0", 1); }
          catch (Throwable t) { failure.compareAndSet(null, t); }
        });
        destructive.start();
        try {
          check(lookup.await(5, TimeUnit.SECONDS), "preflight lookup not reached");
          admission.start();
          check(admissionStarted.await(5, TimeUnit.SECONDS), "admission thread not started");
          long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
          while (admission.isAlive() && admission.getState() != Thread.State.BLOCKED &&
                 System.nanoTime() < deadline) Thread.yield();
          check(admission.getState() == Thread.State.BLOCKED,
                "buffer admission ran during destructive preflight");
        } finally {
          release.countDown();
          destructive.join(5000);
          admission.join(5000);
        }
        check(!destructive.isAlive() && !admission.isAlive(), "concurrent operation hung");
        if (failure.get() != null) throw new AssertionError("concurrent operation failed", failure.get());
      }
    }
  }
  static void callerWiring() throws Exception {
    String cc = java.nio.file.Files.readString(java.nio.file.Path.of("src/work/CC/fillES-mjd.groovy"));
    String ijc = java.nio.file.Files.readString(java.nio.file.Path.of("src/work/IJCLab/fillES.groovy"));
    check(cc.contains("esclient.bufferDoubleArray(") &&
          cc.contains("public void cleanup() {\n    esclient.flushBufferedDoubleArrays(10);") &&
          cc.indexOf("reader.cleanup();") < cc.indexOf("psizes = reader.sizes();"),
          "CC must buffer and flush before final sizes");
    check(ijc.contains("esclient.bufferDoubleArray(") &&
          ijc.contains("esclient.flushBufferedDoubleArrays(10);") &&
          ijc.contains("esclient.commitWithRetry(10);\n        esclient.flushBufferedDoubleArrays(10);") &&
          ijc.contains("esclient.commitWithRetry(10);\nesclient.flushBufferedDoubleArrays(10);\n\nString psizes = sizes();"),
          "IJCLab must flush periodically and before final sizes");
  }
  public static void main(String[] args) throws Exception {
    if (args.length > 0 && args[0].equals("callers")) { callerWiring(); System.out.println("PASS callers"); return; }
    if (args.length > 0 && args[0].equals("lifecycle")) { lifecycleWithPendingBufferedDoubles(); lifecycleAdmissionRace(); return; }
    if (args.length > 0 && args[0].equals("stress")) { longSingleDocument(); return; }
    grouping(); partialAndMalformed(); malformedItemsRetained(); transportAndLimit();
    longSingleDocument(); lifecycleWithPendingBufferedDoubles(); lifecycleAdmissionRace(); boundsAndBenchmark(); callerWiring();
    System.out.println("PASS ESClientBufferedDoubleTest");
  }
}

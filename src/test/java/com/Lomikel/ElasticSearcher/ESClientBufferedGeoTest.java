package com.Lomikel.ElasticSearcher;

import com.Lomikel.Utils.LomikelException;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Offline loopback contract for opt-in grouped geo_point updates. */
public class ESClientBufferedGeoTest {
  static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
  static void fails(ESClientBufferedDoubleTest.RunnableWithException action) throws Exception {
    ESClientBufferedDoubleTest.fails(action);
  }
  static JSONArray points(String body) {
    return new JSONObject(body.split("\n")[1]).getJSONObject("script")
        .getJSONObject("params").getJSONArray("values");
  }
  static void groupedAndScript() throws Exception {
    try (ESClientBufferedDoubleTest.Fixture f = new ESClientBufferedDoubleTest.Fixture()) {
      ESClient es = f.client();
      es.bufferGeoPointArray("ss", "location", "id0", 12, -4);
      es.bufferGeoPointArray("ss", "location", "id0", 15, 5);
      es.bufferGeoPointArray("ss", "location", "id0", 12, -4);
      es.bufferGeoPointArray("ss", "other", "id0", 12, -4);
      es.bufferGeoPointArray("other", "location", "id0", 12, -4);
      es.bufferGeoPointArray("ss", "location", "id1", 12, -4);
      check(f.bodies.isEmpty(), "not opt-in buffered");
      es.flushBufferedGeoPointArrays(2);
      check(f.bodies.size() == 1 && f.bodies.get(0).split("\n").length == 8, "not grouped");
      JSONArray values = points(f.bodies.get(0));
      check(values.length() == 2 && values.getJSONObject(0).getDouble("lat") == -4 &&
          values.getJSONObject(0).getDouble("lon") == -168 &&
          values.getJSONObject(1).getDouble("lat") == 5 &&
          values.getJSONObject(1).getDouble("lon") == -165, "order, dedup or coordinates");
      JSONObject update = new JSONObject(f.bodies.get(0).split("\n")[1]);
      String source = update.getJSONObject("script").getString("source");
      check(update.getBoolean("scripted_upsert") && update.getJSONObject("upsert").isEmpty(), "upsert contract");
      check(source.contains("instanceof List") && source.contains("a.add(old)") &&
          source.contains("changed = true") && source.contains("if (!changed) { ctx.op = 'noop'; }"),
          "scalar conversion / idempotent noop contract");
      es.flushBufferedGeoPointArrays(1);
      check(f.bodies.size() == 1, "success retained");
    }
  }
  @SuppressWarnings("unchecked")
  static Map<String, Object> runScript(JSONObject update, Object existing) {
    Map<String, Object> source = new LinkedHashMap<>();
    if (existing != null) source.put("location", existing);
    Map<String, Object> ctx = new LinkedHashMap<>();
    ctx.put("_source", source);
    Binding binding = new Binding();
    binding.setVariable("ctx", ctx);
    binding.setVariable("params", update.getJSONObject("script").getJSONObject("params").toMap());
    // Groovy needs statement separators after Painless block statements.
    String painless = update.getJSONObject("script").getString("source");
    new GroovyShell(binding).evaluate(painless.replace("} for (", "}\nfor (")
        .replace("} if (", "}\nif ("));
    return ctx;
  }
  static Map<String, Object> point(Number lat, Number lon) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("lat", lat); result.put("lon", lon);
    return result;
  }
  static void scriptReplayAndLegacyNumbers() throws Exception {
    try (ESClientBufferedDoubleTest.Fixture f = new ESClientBufferedDoubleTest.Fixture()) {
      ESClient es = f.client();
      es.bufferGeoPointArray("ss", "location", "étoile-星", 12, -4);
      es.flushBufferedGeoPointArrays(1);
      JSONObject update = new JSONObject(f.bodies.get(0).split("\n")[1]);
      Map<String, Object> missing = runScript(update, null);
      check(((List<?>) ((Map<?, ?>) missing.get("_source")).get("location")).size() == 1 &&
          !"noop".equals(missing.get("op")), "missing field must create a point");
      Map<String, Object> scalar = runScript(update, point(-4, -168));
      Object normalized = ((Map<?, ?>) scalar.get("_source")).get("location");
      check(normalized instanceof List && ((List<?>) normalized).size() == 1 &&
          !"noop".equals(scalar.get("op")), "duplicate scalar must persist list normalization");
      List<Object> old = new ArrayList<>();
      old.add(point(-4.0, -168.0));
      Map<String, Object> replay = runScript(update, old);
      check(((List<?>) ((Map<?, ?>) replay.get("_source")).get("location")).size() == 1 &&
          "noop".equals(replay.get("op")), "mixed numeric types must deduplicate legacy list");
      List<Object> different = new ArrayList<>();
      different.add(point(-4, -167));
      Map<String, Object> added = runScript(update, different);
      List<?> result = (List<?>) ((Map<?, ?>) added.get("_source")).get("location");
      check(result.size() == 2 && result.get(0) == different.get(0) &&
          !"noop".equals(added.get("op")), "new coordinate must append after old point");
    }
  }
  static void failures() throws Exception {
    try (ESClientBufferedDoubleTest.Fixture f = new ESClientBufferedDoubleTest.Fixture()) {
      ESClient es = f.client();
      es.bufferGeoPointArray("idx", "location", "id0", 1, 2);
      es.bufferGeoPointArray("idx", "location", "id1", 3, 4);
      f.failId = 1;
      fails(() -> es.flushBufferedGeoPointArrays(2));
      check(f.bodies.size() == 2 && f.bodies.get(1).split("\n").length == 2 &&
          f.bodies.get(1).contains("id1") && !f.bodies.get(1).contains("id0"), "retry not limited to failures");
      f.failId = -1; f.malformed = true;
      fails(() -> es.flushBufferedGeoPointArrays(2));
      f.malformed = false;
      es.flushBufferedGeoPointArrays(1);
      check(f.bodies.get(2).equals(f.bodies.get(3)), "malformed response lost replay");
      es.bufferGeoPointArray("idx", "location", "id2", 2, 3);
      f.httpStatus = 503;
      fails(() -> es.flushBufferedGeoPointArrays(2));
      f.httpStatus = 200;
      es.flushBufferedGeoPointArrays(1);
      check(f.bodies.get(4).equals(f.bodies.get(6)), "transport lost ambiguous update");
    }
    String[] invalid = {"{\"errors\":false,\"items\":[]}",
        "{\"errors\":false,\"items\":[{\"update\":{\"_index\":\"idx\",\"_id\":\"wrong\",\"status\":200,\"result\":\"updated\"}}]}",
        "{\"errors\":false,\"items\":[{\"update\":{\"_index\":\"idx\",\"_id\":\"id0\",\"status\":200}}]}",
        "{\"errors\":true,\"items\":[{\"update\":{\"_index\":\"idx\",\"_id\":\"id0\",\"status\":200,\"result\":\"updated\"}}]}"};
    for (String bad : invalid) try (ESClientBufferedDoubleTest.Fixture f = new ESClientBufferedDoubleTest.Fixture()) {
      ESClient es = f.client(); es.bufferGeoPointArray("idx", "location", "id0", 1, 2);
      f.forcedAnswer = bad;
      fails(() -> es.flushBufferedGeoPointArrays(2));
      f.forcedAnswer = null; es.flushBufferedGeoPointArrays(1);
      check(f.bodies.size() == 2 && f.bodies.get(0).equals(f.bodies.get(1)), "invalid item lost replay");
    }
  }
  static void limitsAndRequests() throws Exception {
    try (ESClientBufferedDoubleTest.Fixture f = new ESClientBufferedDoubleTest.Fixture()) {
      ESClient es = f.client();
      long start = System.nanoTime();
      for (int i = 0; i < 12000; i++) es.bufferGeoPointArray("ss", "location", "id0", i, i);
      es.flushBufferedGeoPointArrays(2);
      check(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 25, "quadratic admission");
      check(f.bodies.size() > 1, "single object not rotated");
      int next = 0;
      for (String body : f.bodies) {
        check(body.getBytes(StandardCharsets.UTF_8).length <= 65536, "byte cap exceeded");
        for (Object point : points(body)) check(((JSONObject) point).getDouble("lat") == next++, "chunk order");
      }
      check(next == 12000, "lost points");
      int bulk = f.bodies.size();
      for (int i = 0; i < 30; i++) es.updateGeoPointArray("ss", "location", "sync" + i, i, i);
      check(f.bodies.size() == bulk + 30 && bulk < 30, "loopback request reduction");
      System.out.println("loopback: 12000 grouped points=" + bulk + " bulk requests; 30 synchronous=30 requests");
    }
    try (ESClientBufferedDoubleTest.Fixture f = new ESClientBufferedDoubleTest.Fixture()) {
      ESClient es = f.client();
      for (int i = 0; i < 600; i++) es.bufferGeoPointArray("ss", "location", "id" + i, i, i);
      es.flushBufferedGeoPointArrays(1);
      check(f.bodies.size() > 1 && f.bodies.size() < 600, "document cap");
      for (String body : f.bodies) check(body.getBytes(StandardCharsets.UTF_8).length <= 65536, "byte cap");
    }
  }
  static void rotationFailureAndOversize() throws Exception {
    try (ESClientBufferedDoubleTest.Fixture f = new ESClientBufferedDoubleTest.Fixture()) {
      ESClient es = f.client(); f.httpStatus = 503;
      boolean failed = false;
      for (int i = 0; i < 12000; i++) {
        try { es.bufferGeoPointArray("idx", "location", "id0", i, i); }
        catch (LomikelException expected) { failed = true; break; }
      }
      check(failed && !f.bodies.isEmpty(), "failed rotation not reported");
      String pending = f.bodies.get(0);
      for (String body : f.bodies) check(body.equals(pending), "failed rotation changed chunk");
      f.httpStatus = 200; es.flushBufferedGeoPointArrays(1);
      check(f.bodies.get(f.bodies.size() - 1).equals(pending), "failed chunk not replayed");
    }
    try (ESClientBufferedDoubleTest.Fixture f = new ESClientBufferedDoubleTest.Fixture()) {
      ESClient es = f.client();
      boolean rejected = false;
      try { es.bufferGeoPointArray("idx", "location", "x".repeat(66000), 1, 2); }
      catch (IllegalArgumentException expected) { rejected = true; }
      check(rejected && f.paths.isEmpty(), "oversized action sent");
    }
  }
  static void callerWiring() throws Exception {
    String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/work/CC/fillES-radec.groovy"));
    check(source.contains("esclient.bufferGeoPointArray(\"ss_radec\"") &&
        source.contains("esclient.putGeoPoint(\"dia_radec\"") &&
        source.contains("esclient.commitWithRetry(10);\n        esclient.flushBufferedGeoPointArrays(10);") &&
        source.contains("public void cleanup() {\n    esclient.commitWithRetry(10);\n    esclient.flushBufferedGeoPointArrays(10);") &&
        source.indexOf("reader.cleanup();") < source.indexOf("psizes = reader.sizes();"),
        "CC SS flush / DIA preservation / final size order");
  }
  static void lifecycle() throws Exception {
    try (ESClientBufferedDoubleTest.Fixture f = new ESClientBufferedDoubleTest.Fixture()) {
      ESClient es = f.client(); es.bufferGeoPointArray("idx", "location", "id0", 1, 2);
      fails(() -> es.cleanIndex("idx")); fails(() -> es.deleteIndex("idx"));
      check(f.paths.isEmpty(), "destructive preflight despite pending geo");
      es.flushBufferedGeoPointArrays(1);
    }
    for (boolean clean : new boolean[]{true, false}) {
      CountDownLatch lookup = new CountDownLatch(1), release = new CountDownLatch(1);
      AtomicReference<Throwable> failure = new AtomicReference<>();
      try (ESClientBufferedDoubleTest.Fixture f = new ESClientBufferedDoubleTest.Fixture()) {
        f.server.createContext("/idx", ex -> {
          try {
            if (ex.getRequestMethod().equals("GET")) { lookup.countDown(); release.await(5, TimeUnit.SECONDS); }
            String answer = ex.getRequestMethod().equals("GET") ? "{\"idx\":{}}" : clean ?
                "{\"total\":0,\"deleted\":0,\"version_conflicts\":0,\"failures\":[],\"timed_out\":false}" : "{\"acknowledged\":true}";
            byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, bytes.length); ex.getResponseBody().write(bytes); ex.close();
          } catch (Throwable t) { failure.set(t); ex.close(); }
        });
        ESClient es = f.client();
        Thread destroy = new Thread(() -> { try { if (clean) es.cleanIndex("idx"); else es.deleteIndex("idx"); }
            catch (Throwable t) { failure.set(t); } });
        Thread admit = new Thread(() -> { try { es.bufferGeoPointArray("idx", "location", "id0", 1, 2); }
            catch (Throwable t) { failure.set(t); } });
        destroy.start();
        try {
          check(lookup.await(5, TimeUnit.SECONDS), "preflight not reached"); admit.start();
          long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
          while (admit.isAlive() && admit.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) Thread.yield();
          check(admit.getState() == Thread.State.BLOCKED, "admission not serialized through mutation");
        } finally { release.countDown(); destroy.join(5000); admit.join(5000); }
        check(!destroy.isAlive() && !admit.isAlive() && failure.get() == null, "race failed");
      }
    }
  }
  public static void main(String[] args) throws Exception {
    groupedAndScript(); scriptReplayAndLegacyNumbers(); failures(); limitsAndRequests(); rotationFailureAndOversize(); lifecycle(); callerWiring();
    System.out.println("PASS ESClientBufferedGeoTest");
  }
}

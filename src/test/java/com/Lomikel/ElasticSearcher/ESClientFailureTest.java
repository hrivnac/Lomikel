package com.Lomikel.ElasticSearcher;

import com.Lomikel.Utils.LomikelException;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Offline behavioral regression tests; never connects to a real ES instance. */
public class ESClientFailureTest {
  private static final class Fake implements AutoCloseable {
    final HttpServer server;
    final List<String> paths = new ArrayList<>();
    final List<String> bodies = new ArrayList<>();
    int status = 200;
    String answer = "{}";
    Fake() throws Exception {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", this::respond);
      server.start();
    }
    void respond(HttpExchange exchange) {
      try {
        paths.add(exchange.getRequestURI().getPath());
        bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
      } catch (Exception e) { throw new RuntimeException(e); }
    }
    ESClient client() { return new ESClient("http://127.0.0.1:" + server.getAddress().getPort()); }
    public void close() { server.stop(0); }
  }
  private static void check(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }
  private static LomikelException fails(Action action) throws Exception {
    try { action.run(); } catch (LomikelException e) { return e; }
    throw new AssertionError("Expected LomikelException");
  }
  private interface Action { void run() throws Exception; }

  private static void retries() throws Exception {
    try (Fake fake = new Fake()) {
      fake.status = 503;
      ESClient es = fake.client();
      es.putValue("idx", "v", "id", "data");
      LomikelException error = fails(() -> es.commitWithRetry(2));
      check(fake.paths.size() == 2, "exhausted bulk retries: " + fake.paths);
      check(error.getMessage().contains("idx"), "missing index context: " + error);
      check(error.getCause() != null, "lost underlying transport error");
      fake.status = 200;
      fake.answer = "{\"errors\":false,\"items\":[{\"index\":{\"_id\":\"id\",\"status\":201}}]}";
      es.commitWithRetry(1);
      check(fake.paths.size() == 3, "failed bulk lost pending command");
      es.commitWithRetry(1);
      check(fake.paths.size() == 3, "successful bulk not cleared");
    }
    try (Fake fake = new Fake()) {
      fake.status = 503;
      LomikelException error = fails(() -> fake.client().updateDoubleArrayWithRetry("idx", "mjd", "id", 1.0, 2));
      check(fake.paths.size() == 2 && error.getCause() != null, "double update failures hidden");
      error = fails(() -> fake.client().updateGeoPointArrayWithRetry("idx", "location", "id", 1.0, 2.0, 2));
      check(fake.paths.size() == 4 && error.getCause() != null, "geo update failures hidden");
    }
  }

  private static void bulk() throws Exception {
    try (Fake fake = new Fake()) {
      fake.answer = "{\"errors\":true,\"items\":[{\"index\":{\"_id\":\"good\",\"status\":201}}," +
                    "{\"index\":{\"_id\":\"bad\",\"status\":400,\"error\":{\"type\":\"mapper_parsing_exception\",\"reason\":\"bad shape\"}}}]}";
      ESClient es = fake.client();
      es.putValue("idx", "v", "good", "first");
      es.putValue("idx", "v", "bad", "second");
      LomikelException error = fails(() -> es.commitWithRetry(1));
      check(error.getMessage().contains("bad") && error.getMessage().contains("400") &&
            error.getMessage().contains("mapper_parsing_exception") && error.getMessage().contains("bad shape"),
            "missing item diagnostic: " + error.getMessage());
      fake.answer = "{\"errors\":false,\"items\":[{\"index\":{\"_id\":\"good\",\"status\":200}}," +
                    "{\"index\":{\"_id\":\"bad\",\"status\":201}}]}";
      es.commitWithRetry(1);
      check(fake.bodies.size() == 2 && fake.bodies.get(0).equals(fake.bodies.get(1)) &&
            fake.bodies.get(1).contains("\"_id\":\"bad\""), "_id index batch changed on retry");
      es.commitWithRetry(1);
      check(fake.bodies.size() == 2, "successful retry did not clear batch");
    }
  }

  private static void reads() throws Exception {
    try (Fake fake = new Fake()) {
      ESClient es = fake.client();
      fake.answer = "{\"hits\":{\"hits\":[]}}";
      check(es.searchValue("idx", "v", "missing").isEmpty(), "valid empty search failed");
      fake.answer = "{\"count\":0}";
      check(es.size("idx") == 0, "valid zero count failed");
      fake.answer = "{\"error\":{\"reason\":\"broken query\"}}";
      fails(() -> es.searchValue("idx", "v", "bad"));
      fails(() -> es.size("idx"));
      fake.answer = "not-json";
      fails(() -> es.searchValue("idx", "v", "bad"));
      fails(() -> es.size("idx"));
      fake.status = 503;
      fails(() -> es.searchValue("idx", "v", "bad"));
      fails(() -> es.size("idx"));
    }
  }

  private static void malformedBulk() throws Exception {
    try (Fake fake = new Fake()) {
      fake.answer = "not-json";
      ESClient es = fake.client();
      es.putValue("idx", "v", "id", "data");
      LomikelException failure = fails(() -> es.commitWithRetry(2));
      check(fake.paths.size() == 2 && failure.getCause() != null, "malformed bulk did not retry");
      fake.answer = "{\"errors\":false,\"items\":[{\"index\":{\"_id\":\"id\",\"status\":201}}]}";
      es.commitWithRetry(1);
      check(fake.paths.size() == 3, "malformed bulk lost pending command");
    }
  }

  private static void plainCommit() throws Exception {
    try (Fake fake = new Fake()) {
      fake.status = 503;
      ESClient es = fake.client();
      es.putValue("idx", "v", "id", "data");
      fails(es::commit);
      check(fake.paths.size() == 1, "plain commit should not retry");
    }
  }

  private static void inconsistentBulk() throws Exception {
    try (Fake fake = new Fake()) {
      fake.answer = "{\"errors\":false,\"items\":[{\"index\":{\"_id\":\"bad\",\"status\":400," +
                    "\"error\":{\"type\":\"mapper_parsing_exception\",\"reason\":\"bad shape\"}}}]}";
      ESClient es = fake.client();
      es.putValue("idx", "v", "bad", "data");
      check(fails(() -> es.commitWithRetry(1)).getMessage().contains("bad shape"),
            "per-item error ignored when summary inconsistent");
    }
  }

  private static void truncatedBulkResponse() throws Exception {
    try (Fake fake = new Fake()) {
      ESClient es = fake.client();
      es.putValue("idx", "v", "id", "data");
      fake.answer = "{\"errors\":false,\"items\":[]}";
      fails(es::commit);
      fake.answer = "{\"errors\":false,\"items\":[{\"index\":{\"_id\":\"id\",\"status\":201}}]}";
      es.commit();
      check(fake.paths.size() == 2, "short bulk response silently discarded queued write");
    }
  }

  private static void rejectsBulkItem(String item, String diagnostic) throws Exception {
    try (Fake fake = new Fake()) {
      ESClient es = fake.client();
      es.putValue("idx", "v", "id", "data");
      fake.answer = "{\"errors\":false,\"items\":[" + item + "]}";
      LomikelException failure = fails(() -> es.commitWithRetry(2));
      check(fake.paths.size() == 2, "invalid item did not retry: " + item);
      check(failure.getMessage().contains("idx") && failure.getMessage().contains(diagnostic),
            "missing bulk context: " + failure);
      fake.answer = "{\"errors\":false,\"items\":[{\"index\":{\"_id\":\"id\",\"status\":201}}]}";
      es.commit();
      check(fake.bodies.size() == 3 && fake.bodies.get(0).equals(fake.bodies.get(2)),
            "invalid item discarded queued command: " + item);
      es.commit();
      check(fake.bodies.size() == 3, "valid bulk did not clear command");
    }
  }

  private static void strictBulkItems() throws Exception {
    rejectsBulkItem("{}", "item");
    rejectsBulkItem("{\"index\":{\"_id\":\"id\"}}", "status");
    rejectsBulkItem("{\"delete\":{\"_id\":\"id\",\"status\":200}}", "delete");
    rejectsBulkItem("{\"index\":{\"_id\":\"other\",\"status\":201}}", "other");
    rejectsBulkItem("{\"index\":{\"_id\":\"id\",\"status\":201},\"delete\":{\"_id\":\"id\",\"status\":200}}", "item");
  }

  private static void malformedBulkItem() throws Exception {
    rejectsBulkItem("null", "item");
    rejectsBulkItem("{\"index\":\"broken\"}", "item");
  }

  private static void updateErrorBodies() throws Exception {
    try (Fake fake = new Fake()) {
      fake.answer = "{\"error\":{\"type\":\"script_exception\",\"reason\":\"double failed\"}}";
      LomikelException failure = fails(() -> fake.client().updateDoubleArrayWithRetry("idx", "mjd", "id", 1.0, 2));
      check(fake.paths.size() == 2 && failure.getMessage().contains("idx") &&
            failure.getCause() != null && failure.getCause().getMessage().contains("double failed"),
            "double update HTTP-200 error ignored: " + failure);
      fake.answer = "{\"error\":{\"type\":\"script_exception\",\"reason\":\"geo failed\"}}";
      failure = fails(() -> fake.client().updateGeoPointArrayWithRetry("idx", "location", "id", 1.0, 2.0, 2));
      check(fake.paths.size() == 4 && failure.getCause() != null &&
            failure.getCause().getMessage().contains("geo failed"),
            "geo update HTTP-200 error ignored: " + failure);
      fake.answer = "{}";
      fails(() -> fake.client().updateDoubleArrayWithRetry("idx", "mjd", "id", 1.0, 1));
      fake.answer = "{\"result\":\"updated\"}";
      fake.client().updateDoubleArrayWithRetry("idx", "mjd", "id", 1.0, 2);
      fake.client().updateGeoPointArrayWithRetry("idx", "location", "id", 1.0, 2.0, 2);
      check(fake.paths.size() == 7, "normal update did not succeed on first try");
    }
  }

  public static void main(String[] args) throws Exception {
    switch (args[0]) {
      case "retries": retries(); break;
      case "bulk": bulk(); break;
      case "reads": reads(); break;
      case "malformedBulk": malformedBulk(); break;
      case "plainCommit": plainCommit(); break;
      case "inconsistentBulk": inconsistentBulk(); break;
      case "truncatedBulkResponse": truncatedBulkResponse(); break;
      case "strictBulkItems": strictBulkItems(); break;
      case "malformedBulkItem": malformedBulkItem(); break;
      case "updateErrorBodies": updateErrorBodies(); break;
      default: throw new IllegalArgumentException(args[0]);
    }
    System.out.println("PASS " + args[0]);
  }
}

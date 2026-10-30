package com.Lomikel.ElasticSearcher;

import com.Lomikel.Utils.LomikelException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/** Offline-only index lifecycle tests; never connects to an Elasticsearch service. */
public class ESClientIndexLifecycleTest {
  private static final class Fake implements AutoCloseable {
    final HttpServer server;
    final List<String> methods = new ArrayList<>();
    final List<String> urls = new ArrayList<>();
    final List<String> bodies = new ArrayList<>();
    int status = 200;
    int lookupStatus = 200;
    String response = "{}";
    String lookupResponse = "{\"science_2026\":{\"mappings\":{},\"settings\":{}}}";
    Fake() throws Exception {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", this::respond);
      server.start();
    }
    void respond(HttpExchange exchange) {
      try {
        methods.add(exchange.getRequestMethod());
        urls.add(exchange.getRequestURI().toString());
        bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        check(auth != null && auth.startsWith("Basic ") && auth.length() > 6,
              "index lifecycle request must retain client authentication");
        boolean lookup = exchange.getRequestMethod().equals("GET");
        byte[] bytes = (lookup ? lookupResponse : response).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(lookup ? lookupStatus : status, bytes.length);
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
  private interface Action { void run() throws Exception; }
  private static void fails(Action action) throws Exception {
    try { action.run(); } catch (LomikelException e) { return; }
    throw new AssertionError("Expected LomikelException");
  }
  private static void delete() throws Exception {
    try (Fake fake = new Fake()) {
      ESClient es = fake.client();
      fake.response = "{\"acknowledged\":true}";
      es.deleteIndex("science_2026");
      check(fake.methods.equals(List.of("GET", "DELETE")) &&
            fake.urls.equals(List.of("/science_2026", "/science_2026")),
            "delete should target exactly one index");
      check(fake.bodies.equals(List.of("", "")), "index lookup and DELETE should not carry a body");
      for (String bad : new String[]{"*", "_all", "a,b", "a/b", "a?b", "UPPER", "", ".system", "a#b"})
        fails(() -> es.deleteIndex(bad));
      fails(() -> es.deleteIndex(null));
      check(fake.methods.size() == 2, "invalid names sent a destructive request");
      fake.lookupResponse = "{\"concrete-index\":{\"aliases\":{\"science_2026\":{}}}}";
      fails(() -> es.deleteIndex("science_2026"));
      check(fake.methods.equals(List.of("GET", "DELETE", "GET")), "alias must not be deleted");
      fake.lookupResponse = "{\"science_2026\":{},\"another\":{}}";
      fails(() -> es.deleteIndex("science_2026"));
      check(fake.methods.get(fake.methods.size() - 1).equals("GET"), "multiple targets must not be deleted");
      fake.lookupResponse = "{\"science_2026\":{\"mappings\":{},\"settings\":{}}}";
      fake.response = "{\"acknowledged\":false}";
      fails(() -> es.deleteIndex("science_2026"));
      fake.response = "{\"acknowledged\":\"true\"}";
      fails(() -> es.deleteIndex("science_2026"));
      fake.response = "{\"acknowledged\":true,\"error\":\"partial deletion\"}";
      fails(() -> es.deleteIndex("science_2026"));
      fake.response = "{\"error\":\"not deleted\"}";
      fails(() -> es.deleteIndex("science_2026"));
      fake.response = "not-json";
      fails(() -> es.deleteIndex("science_2026"));
      fake.status = 503;
      fails(() -> es.deleteIndex("science_2026"));
    }
  }
  private static void clean() throws Exception {
    try (Fake fake = new Fake()) {
      ESClient es = fake.client();
      fake.response = "{\"total\":2,\"deleted\":2,\"version_conflicts\":0,\"failures\":[],\"timed_out\":false}";
      es.cleanIndex("science_2026");
      check(fake.methods.equals(List.of("GET", "POST")) &&
            fake.urls.equals(List.of("/science_2026", "/science_2026/_delete_by_query?refresh=true&wait_for_completion=true")),
            "clean should delete documents and refresh only the named index");
      JSONObject body = new JSONObject(fake.bodies.get(1));
      check(body.getJSONObject("query").has("match_all"), "clean should match every document");
      check(!body.has("script"), "clean must not use a scripted update");
      fails(() -> es.cleanIndex("*"));
      fails(() -> es.cleanIndex(null));
      check(fake.methods.size() == 2, "invalid clean sent a request");
      fake.lookupResponse = "{\"first-index\":{},\"second-index\":{}}";
      fails(() -> es.cleanIndex("science_2026"));
      check(fake.methods.equals(List.of("GET", "POST", "GET")), "alias must not trigger delete-by-query");
      fake.lookupResponse = "not-json";
      fails(() -> es.cleanIndex("science_2026"));
      check(fake.methods.get(fake.methods.size() - 1).equals("GET"), "malformed lookup must not clean");
      fake.lookupResponse = "{\"science_2026\":{\"mappings\":{},\"settings\":{}}}";
      es.putValue("science_2026", "v", "id", "queued");
      fails(() -> es.cleanIndex("science_2026"));
      fails(() -> es.deleteIndex("science_2026"));
      check(fake.methods.size() == 4, "pending writes must not silently repopulate a cleared index");
    }
    for (String response : new String[]{
      "{\"total\":2,\"deleted\":1,\"version_conflicts\":1,\"failures\":[]}",
      "{\"total\":2,\"deleted\":1,\"version_conflicts\":0,\"failures\":[{\"reason\":\"bad\"}]}",
      "{\"total\":0,\"deleted\":0,\"version_conflicts\":0,\"failures\":[],\"timed_out\":true}",
      "{\"total\":0,\"deleted\":0,\"version_conflicts\":0,\"failures\":[]}",
      "{\"total\":0,\"deleted\":0,\"version_conflicts\":0,\"failures\":[],\"timed_out\":\"false\"}",
      "{\"error\":\"failed\"}", "{}", "not-json"}) {
      try (Fake fake = new Fake()) {
        fake.response = response;
        fails(() -> fake.client().cleanIndex("science_2026"));
      }
    }
    try (Fake fake = new Fake()) {
      fake.status = 503;
      fails(() -> fake.client().cleanIndex("science_2026"));
    }
  }
  public static void main(String[] args) throws Exception {
    delete();
    clean();
    System.out.println("ESClient index lifecycle loopback tests passed");
  }
}

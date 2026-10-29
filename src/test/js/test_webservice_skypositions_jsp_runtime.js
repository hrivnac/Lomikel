'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawnSync} = require('node:child_process');
const root = path.resolve(__dirname, '../../..');
const page = fs.readFileSync(path.join(root, 'src/webservice/SkyPositions.jsp'), 'utf8');
const declaration = page.match(/<%!([\s\S]*?)%>/)[1];
const body = page.slice(page.indexOf('%>', page.indexOf('<%!')) + 2).match(/<%([\s\S]*?)%>/)[1];
const jar = path.join(root, 'extlib/import/org.json.jar');
const dir = fs.mkdtempSync(path.join(process.env.TMPDIR || path.join(os.homedir(), '.hermes/cache/scratch'), 'skypositions-jsp-'));
try {
  const java = `import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.sun.net.httpserver.HttpServer;
public class SkyPositionsJspTest {
${declaration}
  static class Request {
    byte[] bytes;
    Request(String text) { bytes = text.getBytes(StandardCharsets.UTF_8); }
    String getMethod() { return "POST"; }
    InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
  }
  static class Response {
    int status = 200;
    void setStatus(int value) { status = value; }
    void setHeader(String key, String value) { }
  }
  static void execute(Request request, Response response, PrintWriter out) throws Exception {
${body}
  }
  static JSONObject runIds(JSONArray ids, int expectedStatus) throws Exception {
    Request request = new Request(new JSONObject().put("ids", ids).toString());
    Response response = new Response();
    StringWriter writer = new StringWriter();
    PrintWriter out = new PrintWriter(writer);
    execute(request, response, out);
    out.flush();
    if (response.status != expectedStatus) throw new AssertionError("Unexpected HTTP " + response.status + ": " + writer);
    return new JSONObject(writer.toString());
  }
  static JSONObject run(String id, int expectedStatus) throws Exception {
    return runIds(new JSONArray().put("ZTFabc").put(id), expectedStatus);
  }
  static void check(boolean value, String description) {
    if (!value) throw new AssertionError(description);
  }
  public static void main(String[] args) throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    ArrayList<String> paths = new ArrayList<>();
    ArrayList<String> bodies = new ArrayList<>();
    server.createContext("/", exchange -> {
      String index = exchange.getRequestURI().getPath();
      String sent = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      paths.add(index); bodies.add(sent);
      JSONArray docs = new JSONArray();
      for (Object raw : new JSONObject(sent).getJSONArray("docs")) {
        JSONObject requestDoc = (JSONObject)raw;
        String id = requestDoc.getString("_id");
        JSONObject row = new JSONObject().put("_id", id);
        if (index.equals("/radec/_mget") ||
            (index.equals("/ss_radec/_mget") &&
              (id.equals("1") || id.equals("2") || id.equals("4") || id.equals("5") ||
               id.equals("7") || id.matches("1[0-9]"))) ||
            (index.equals("/dia_radec/_mget") &&
              (id.equals("2") || id.equals("4") || id.equals("5") || id.equals("6")))) {
          Object location = new JSONObject().put("lon", id.equals("4") && index.equals("/ss_radec/_mget") ? 200 : 0)
            .put("lat", index.equals("/ss_radec/_mget") ? 20 : 10);
          if ((id.equals("5") && index.equals("/dia_radec/_mget")) ||
              (id.equals("7") && index.equals("/ss_radec/_mget"))) {
            JSONArray oversized = new JSONArray();
            for (int p=0;p<4001;p++) oversized.put(location);
            location = oversized;
          }
          row.put("found", true);
          if (!Boolean.FALSE.equals(requestDoc.opt("_source")))
            row.put("_source", new JSONObject().put("location", location));
        }
        docs.put(row);
      }
      boolean fail = sent.contains("\\\"_id\\\":\\\"3\\\"");
      byte[] response = new JSONObject().put("docs", docs).toString().getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(fail ? 503 : 200, response.length);
      try (OutputStream output = exchange.getResponseBody()) { output.write(response); }
    });
    server.start();
    System.setProperty("fink.skyview.ztfEsUrl", "http://127.0.0.1:" + server.getAddress().getPort());
    System.setProperty("fink.skyview.lsstEsUrl", "http://127.0.0.1:" + server.getAddress().getPort());
    try {
      JSONObject first = run("1", 200);
      JSONArray logged = first.getJSONArray("esRequests");
      check(logged.length() == 2, "solar lookup must be represented");
      for (int i = 0; i < logged.length(); i++) {
        check(logged.getJSONObject(i).getString("path").equals(paths.get(i)), "wrong index path");
        check(logged.getJSONObject(i).getString("body").equals(bodies.get(i)), "body differs from bytes executed");
        check(!logged.getJSONObject(i).toString().contains("127.0.0.1"), "private host leaked");
      }
      check(logged.getJSONObject(1).getString("path").equals("/ss_radec/_mget") &&
        new JSONObject(bodies.get(1)).getJSONArray("docs").getJSONObject(0).getString("_id").equals("1") &&
        !new JSONObject(bodies.get(1)).getJSONArray("docs").getJSONObject(0).getBoolean("_source"),
        "SS detection queries identity without loading unpaired position arrays");
      JSONObject second = run("2", 200);
      check(second.getJSONArray("esRequests").length() == 2, "SS detection must avoid DIA collision request");
      check(second.getJSONArray("ssObjectIds").length() == 1 &&
        second.getJSONArray("ssObjectIds").getString(0).equals("2"), "collision ID must be SS");
      int collisionPoints = 0;
      for (Object raw : second.getJSONArray("points"))
        if (((JSONObject)raw).getString("objectId").equals("2")) {
          collisionPoints++;
          check(((JSONObject)raw).getDouble("dec") == 20, "SS coordinate must take precedence");
        }
      check(collisionPoints == 0, "SS positions must come from paired history, not the coordinate probe");
      check(paths.size() == 4, "logging triggered additional requests");
      JSONObject malformed = run("4", 200);
      check(malformed.getJSONArray("ssObjectIds").toString().contains("\\\"4\\\"") &&
        !malformed.getJSONArray("missing").toString().contains("\\\"4\\\""),
        "found SS with bad coordinates must not be misidentified as DIA");
      check(malformed.getJSONArray("points").length() == 1, "SS cannot inherit colliding DIA positions");
      JSONObject hugeCollision = run("5", 200);
      check(hugeCollision.getJSONArray("points").length() == 1,
        "discarded DIA collision points must not trip the retained-point cap");
      JSONObject longSs = runIds(new JSONArray().put("7"), 200);
      check(longSs.getJSONArray("ssObjectIds").toString().contains("\\\"7\\\"") &&
        longSs.getJSONArray("points").length() == 0,
        "long SS history must be identified without overflowing the coordinate snapshot");
      JSONArray tenLongIds = new JSONArray();
      for (int i=10;i<20;i++) tenLongIds.put(String.valueOf(i));
      JSONObject longSet = runIds(tenLongIds, 200);
      check(longSet.getJSONArray("ssObjectIds").length() == 10 &&
        longSet.getJSONArray("points").length() == 0,
        "a LongSS-sized set must pass only IDs to the paired-history loader");
      JSONObject ordinaryDia = run("6", 200);
      check(ordinaryDia.getJSONArray("ssObjectIds").length() == 0 &&
        ordinaryDia.getJSONArray("points").length() == 2 &&
        ordinaryDia.getJSONArray("esRequests").length() == 3 &&
        ordinaryDia.getJSONArray("esRequests").getJSONObject(2).getString("path").equals("/dia_radec/_mget"),
        "ordinary numeric DIA requires a not-found SS lookup followed by DIA lookup");
      int firstMixed = paths.size();
      JSONObject mixed = runIds(new JSONArray().put("ZTFabc").put("2").put("6"), 200);
      check(mixed.getJSONArray("points").length() == 2 &&
        mixed.getJSONArray("ssObjectIds").length() == 1 &&
        mixed.getJSONArray("ssObjectIds").getString(0).equals("2"),
        "mixed ZTF/SS/DIA returns only static positions; SS history is fetched separately");
      JSONArray mixedRequests = mixed.getJSONArray("esRequests");
      check(mixedRequests.length() == 3 &&
        new JSONObject(mixedRequests.getJSONObject(2).getString("body"))
          .getJSONArray("docs").length() == 1 &&
        new JSONObject(mixedRequests.getJSONObject(2).getString("body"))
          .getJSONArray("docs").getJSONObject(0).getString("_id").equals("6"),
        "mixed DIA request excludes the identified SS ID");
      for (int i=0;i<mixedRequests.length();i++)
        check(mixedRequests.getJSONObject(i).getString("body").equals(bodies.get(firstMixed+i)),
          "mixed request log must match the executed bytes");
      JSONObject failed = run("3", 502);
      check(failed.getJSONArray("esRequests").length() == 2, "failed ES lookup loses executed requests");
      check(failed.getJSONArray("esRequests").getJSONObject(1).getString("body").equals(bodies.get(bodies.size()-1)),
        "failed upstream body differs from bytes executed");
      System.out.println("SkyPositions JSP: SS-first identity, retained point cap and index/body metadata passed");
    } finally { server.stop(0); }
  }
}`;
  fs.writeFileSync(path.join(dir, 'SkyPositionsJspTest.java'), java);
  for (const [command, args] of [['javac', ['-cp', jar, 'SkyPositionsJspTest.java']],
                                  ['java', ['-cp', `${dir}${path.delimiter}${jar}`, 'SkyPositionsJspTest']]]) {
    const result = spawnSync(command, args, {cwd: dir, encoding: 'utf8', timeout: 30000});
    assert.equal(result.status, 0, `${command}: ${result.stdout}${result.stderr}`);
    if (command === 'java') process.stdout.write(result.stdout);
  }
} finally { fs.rmSync(dir, {recursive: true, force: true}); }

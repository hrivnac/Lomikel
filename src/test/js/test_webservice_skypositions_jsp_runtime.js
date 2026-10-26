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
  static JSONObject run(String id, int expectedStatus) throws Exception {
    Request request = new Request(new JSONObject().put("ids", new JSONArray().put("ZTFabc").put(id)).toString());
    Response response = new Response();
    StringWriter writer = new StringWriter();
    PrintWriter out = new PrintWriter(writer);
    execute(request, response, out);
    out.flush();
    if (response.status != expectedStatus) throw new AssertionError("Unexpected HTTP " + response.status + ": " + writer);
    return new JSONObject(writer.toString());
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
        String id = ((JSONObject)raw).getString("_id");
        JSONObject row = new JSONObject().put("_id", id);
        if (index.equals("/radec/_mget") || index.equals("/ss_radec/_mget") || id.equals("2"))
          row.put("found", true).put("_source", new JSONObject().put("location",
            new JSONObject().put("lon", 0).put("lat", 10)));
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
      check(logged.length() == 3, "solar fallback must be represented");
      for (int i = 0; i < logged.length(); i++) {
        check(logged.getJSONObject(i).getString("path").equals(paths.get(i)), "wrong index path");
        check(logged.getJSONObject(i).getString("body").equals(bodies.get(i)), "body differs from bytes executed");
        check(!logged.getJSONObject(i).toString().contains("127.0.0.1"), "private host leaked");
      }
      check(new JSONObject(bodies.get(2)).getJSONArray("docs").getJSONObject(0).getString("_id").equals("1"),
        "solar fallback includes non-missing IDs");
      JSONObject second = run("2", 200);
      check(second.getJSONArray("esRequests").length() == 2, "unneeded solar fallback logged");
      check(paths.size() == 5, "logging triggered additional requests");
      JSONObject failed = run("3", 502);
      check(failed.getJSONArray("esRequests").length() == 2, "failed ES lookup loses executed requests");
      check(failed.getJSONArray("esRequests").getJSONObject(1).getString("body").equals(bodies.get(6)),
        "failed upstream body differs from bytes executed");
      System.out.println("SkyPositions JSP: executed index/body metadata and conditional fallback passed");
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

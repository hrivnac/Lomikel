'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawnSync} = require('node:child_process');
const root = path.resolve(__dirname, '../../..');
const page = fs.readFileSync(path.join(root, 'src/webservice/AlertsView/SSTrajectory.jsp'), 'utf8');
const declaration = page.match(/<%!([\s\S]*?)%>/)[1];
const body = page.slice(page.indexOf('%>', page.indexOf('<%!')) + 2).match(/<%([\s\S]*?)%>/)[1];
const jar = path.join(root, 'extlib/import/org.json.jar');
const dir = fs.mkdtempSync(path.join(process.env.TMPDIR || path.join(os.homedir(), '.hermes/cache/scratch'), 'sstrajectory-jsp-'));
try {
  const java = `import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Semaphore;
import com.sun.net.httpserver.HttpServer;
public class SSTrajectoryJspTest {
${declaration}
  static class Request {
    String id;
    Request(String id) { this.id = id; }
    String getParameter(String key) { return key.equals("id") ? id : null; }
  }
  static class Response {
    int status = 200;
    void setStatus(int value) { status = value; }
    void setHeader(String key, String value) { }
  }
  static void execute(Request request, Response response, PrintWriter out) throws Exception {
${body}
  }
  static JSONObject run(String id, int status) throws Exception {
    Response response = new Response();
    StringWriter writer = new StringWriter();
    PrintWriter out = new PrintWriter(writer);
    execute(new Request(id), response, out);
    out.flush();
    if (response.status != status) throw new AssertionError("HTTP " + response.status + ": " + writer);
    return new JSONObject(writer.toString());
  }
  static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
  public static void main(String[] args) throws Exception {
    String id = "1234567890123456789";
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    ArrayList<String> paths = new ArrayList<>(), bodies = new ArrayList<>();
    int[] mode = {0};
    server.createContext("/", exchange -> {
      String requestPath = exchange.getRequestURI().getPath();
      String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      paths.add(requestPath); bodies.add(requestBody);
      String responseText;
      if (requestPath.equals("/api/v1/resolver")) {
        responseText = new JSONArray().put(new JSONObject().put("r:ssObjectId", id)
          .put("r:packed_primary_provisional_designation", "K26A00A")).toString();
      } else if (requestPath.equals("/api/v1/sso")) {
        JSONArray rows = new JSONArray();
        int count = mode[0] == 1 ? 4001 : mode[0] == 4 ? 0 : 2;
        for (int i = 0; i < count; i++) rows.put(new JSONObject()
          .put("r:ssObjectId", id).put("r:diaSourceId", mode[0] == 5 ?
            (i == 0 ? "90071992547409931" : "90071992547409930") : "9007199254740993" + i)
          .put("r:midpointMjdTai", mode[0] == 5 || mode[0] == 7 ? 61001.0 : 61001.0 - i * 0.001)
          .put("r:ra", mode[0] == 7 ? 20.0 : 20.0 + i * 0.001).put("r:dec", 10.0));
        responseText = mode[0] == 2 ? " ".repeat(4000001) : rows.toString();
      } else {
        JSONObject source = requestPath.equals("/ss_mjd/_mget")
          ? new JSONObject().put("mjd", mode[0] == 7 ? new JSONArray().put(61001.0) :
              new JSONArray().put(61001.0).put(61000.999))
          : new JSONObject().put("location", new JSONArray().put(new JSONObject().put("lon", -160).put("lat", 10)));
        if (mode[0] == 6 && requestPath.equals("/ss_mjd/_mget")) {
          JSONArray tooMany = new JSONArray();
          for (int k=0;k<4001;k++) tooMany.put(61001.0);
          source.put("mjd", tooMany);
        }
        boolean absent = mode[0] == 3 || (mode[0] == 8 && requestPath.equals("/ss_mjd/_mget"));
        JSONObject doc = new JSONObject().put("_id", id).put("found", !absent);
        if (!absent) doc.put("_source", source);
        responseText = new JSONObject().put("docs", new JSONArray().put(doc)).toString();
      }
      byte[] bytes = responseText.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, bytes.length);
      try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
    });
    server.start();
    String url = "http://127.0.0.1:" + server.getAddress().getPort();
    System.setProperty("fink.sstrajectory.esUrl", url);
    System.setProperty("fink.sstrajectory.apiUrl", url);
    try {
      JSONObject success = run(id, 200);
      check(success.getJSONArray("sources").length() == 2, "source rows absent");
      check(success.getJSONArray("sources").getJSONObject(1).getString("sourceId").equals("90071992547409930"), "source ID changed");
      check(success.getJSONArray("sources").getJSONObject(0).getDouble("mjd") <
        success.getJSONArray("sources").getJSONObject(1).getDouble("mjd"), "trajectory not time ordered");
      JSONArray recorded = success.getJSONArray("finkRequests");
      check(recorded.length() == 2, "missing resolver/sso metadata");
      for (int i = 0; i < recorded.length(); i++) {
        JSONObject entry = recorded.getJSONObject(i);
        check(entry.getString("path").equals(paths.get(i + 2)), "wrong path");
        check(entry.getString("body").equals(bodies.get(i + 2)), "wrong body");
        check(!entry.toString().contains(url), "host leaked");
      }
      mode[0] = 1;
      JSONObject excessRows = run(id, 502);
      check(excessRows.has("error") && excessRows.getJSONArray("finkRequests").length() == 2, "row cap did not fail with metadata");
      mode[0] = 2;
      JSONObject excessBytes = run(id, 502);
      check(excessBytes.has("error") && excessBytes.getJSONArray("finkRequests").length() == 2, "byte cap did not fail with metadata");
      mode[0] = 3;
      JSONObject missing = run(id, 404);
      check(missing.getJSONArray("esRequests").length() == 2 &&
        missing.getJSONArray("finkRequests").length() == 0, "404 request metadata absent");
      mode[0] = 4;
      JSONObject empty = run(id, 502);
      check(empty.getJSONArray("finkRequests").length() == 2, "empty source history must fail explicitly");
      mode[0] = 5;
      JSONObject tied = run(id, 200);
      check(tied.getJSONArray("sources").getJSONObject(0).getString("sourceId").equals("90071992547409930"),
        "equal-time sources must sort deterministically by exact source ID");
      HISTORY_SLOTS.acquire(6);
      try {
        int before = paths.size();
        JSONObject busy = run(id, 503);
        check(busy.has("error") && paths.size() == before, "busy server must reject before upstream calls");
      } finally { HISTORY_SLOTS.release(6); }
      mode[0] = 6;
      JSONObject excessiveEs = run(id, 200);
      check(!excessiveEs.getBoolean("esCoverageComplete"),
        "excess ES values must fail coverage safely instead of nested scanning");
      mode[0] = 7;
      JSONObject repeated = run(id, 200);
      check(repeated.getJSONArray("sources").length() == 2 && repeated.getBoolean("esCoverageComplete"),
        "distinct source IDs with equal MJD and position must survive deduplicated ES coverage");
      mode[0] = 8;
      JSONObject unfilledMjd = run(id, 200);
      check(unfilledMjd.getJSONArray("sources").length() == 2 &&
        unfilledMjd.getBoolean("esCoverageComplete") && !unfilledMjd.getBoolean("esMjdAvailable"),
        "SS positions and paired Fink sources must work while ss_mjd backfill is incomplete");
      System.out.println("SSTrajectory JSP: paired rows, exact request metadata, row and byte caps passed");
    } finally { server.stop(0); }
  }
}`;
  fs.writeFileSync(path.join(dir, 'SSTrajectoryJspTest.java'), java);
  for (const [command, args] of [['javac', ['-cp', jar, 'SSTrajectoryJspTest.java']],
                                  ['java', ['-cp', `${dir}${path.delimiter}${jar}`, 'SSTrajectoryJspTest']]]) {
    const result = spawnSync(command, args, {cwd: dir, encoding: 'utf8', timeout: 60000});
    assert.equal(result.status, 0, `${command}: ${result.stdout}${result.stderr}`);
    if (command === 'java') process.stdout.write(result.stdout);
  }
} finally { fs.rmSync(dir, {recursive: true, force: true}); }

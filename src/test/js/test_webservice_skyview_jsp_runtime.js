'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawnSync} = require('node:child_process');
const root = path.resolve(__dirname, '../../..');
const page = fs.readFileSync(path.join(root, 'src/webservice/d3/skyview.jsp'), 'utf8');
const declaration = page.match(/<%!([\s\S]*?)%>/)[1];
const body = page.slice(page.indexOf('%>', page.indexOf('<%!')) + 2).match(/<%([\s\S]*?)%>/)[1];
const jar = path.join(root, 'extlib/import/org.json.jar');
const dir = fs.mkdtempSync(path.join(process.env.TMPDIR || path.join(os.homedir(), '.hermes/cache/scratch'), 'skyview-jsp-'));
try {
  const java = `import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;
public class SkyviewJspTest {
${declaration}
  static class Request {
    byte[] bytes; long length; int read;
    Request(String form, long length) { bytes = form.getBytes(StandardCharsets.UTF_8); this.length = length; }
    String getMethod() { return "POST"; }
    long getContentLengthLong() { return length; }
    InputStream getInputStream() { return new ByteArrayInputStream(bytes) {
      public synchronized int read(byte[] b, int off, int len) { int n = super.read(b, off, len); if (n > 0) read += n; return n; }
    }; }
  }
  static class Response { int status = 200; void setStatus(int status) { this.status = status; } }
  static class Result { int status, count, requested, read; String error; }
  static Result run(Request request) throws Exception {
    Response response = new Response();
${body}
    Result result = new Result();
    result.status = response.status; result.count = points.length();
    result.requested = requested; result.read = request.read; result.error = errorMessage;
    return result;
  }
  static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
  public static void main(String[] args) throws Exception {
    JSONArray points = new JSONArray();
    for (int i = 0; i < 4000; i++) points.put(new JSONObject().put("objectId", "ZTF" + i).put("ra", i % 360).put("dec", 0));
    String form = "points=" + URLEncoder.encode(points.toString(), "UTF-8") + "&requested=500&missing=0";
    Result valid = run(new Request(form, -1));
    check(valid.status == 200 && valid.count == 4000 && valid.requested == 500, "4000 point POST rejected");
    String huge = "points=" + "x".repeat(MAX_REQUEST_BYTES + 100);
    Result known = run(new Request(huge, huge.length()));
    check(known.status == 413 && known.read == 0 && "Request too large".equals(known.error), "known length not rejected early");
    Result unknown = run(new Request(huge, -1));
    check(unknown.status == 413 && unknown.read <= MAX_REQUEST_BYTES + 1, "chunked body read unbounded");
    System.out.println("SkyView JSP runtime: valid 4000, known/unknown oversize 413 passed");
  }
}`;
  fs.writeFileSync(path.join(dir, 'SkyviewJspTest.java'), java);
  for (const [command, args] of [['javac', ['-cp', jar, 'SkyviewJspTest.java']],
                                  ['java', ['-cp', `${dir}${path.delimiter}${jar}`, 'SkyviewJspTest']]]) {
    const result = spawnSync(command, args, {cwd: dir, encoding: 'utf8'});
    assert.equal(result.status, 0, `${command}: ${result.stdout}${result.stderr}`);
    if (command === 'java') process.stdout.write(result.stdout);
  }
} finally { fs.rmSync(dir, {recursive: true, force: true}); }

'use strict';
// Runs the real TagObject.jsp scriptlet (declarations + body) against in-memory JanusGraph instances
// configured through the same system properties the WAR uses. Needs a Lomikel build jar and the
// JanusGraph libraries (override with LOMIKEL_JAR / JANUSGRAPH_LIB); prints SKIP when they are absent.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawnSync} = require('node:child_process');
const root = path.resolve(__dirname, '../../..');
const page = fs.readFileSync(path.join(root, 'src/webservice/TagObject.jsp'), 'utf8');

// Static contract checks that never need the Java libraries.
assert.match(page, /"GraphBrowser"/);
assert.match(page, /registerOCol\(Classifier\.instance\("TAG", survey, ""\), TAG, objectId, 1\.0, "", ""\)/);
assert.match(page, /"POST"\.equals\(request\.getMethod\(\)\)/);
assert.match(page, /X-Fink-Tag/);
assert.doesNotMatch(page, /getParameter\(/, 'no query-string input is ever read');
assert.doesNotMatch(page, /password|elastic/i, 'no credentials in the page');

const lomikel = process.env.LOMIKEL_JAR || ['/disk2/agent/Lomikel-SS-Cutouts/lib/Lomikel-04.00.00x.jar']
  .find(file => fs.existsSync(file));
const janus = process.env.JANUSGRAPH_LIB || '/disk2/opt/janusgraph-full-1.0.0/lib';
const orgJson = path.join(root, 'extlib/import/org.json.jar');
if (!lomikel || !fs.existsSync(janus) || !fs.existsSync(orgJson)) {
  console.log('SKIP TagObject.jsp runtime test: Lomikel jar / JanusGraph libraries not found (static checks passed)');
  process.exit(0);
}

const declaration = page.match(/<%!([\s\S]*?)%>\s*\n\s*<%\s*\n/)[1];
const body = page.slice(page.lastIndexOf('<%\n') + 3, page.lastIndexOf('%>'));
const dir = fs.mkdtempSync(path.join(process.env.TMPDIR || path.join(os.homedir(), '.hermes/cache/scratch'), 'tag-jsp-'));
try {
  const imports = [...page.matchAll(/<%@ page import="([^"]+)" %>/g)].map(m => `import ${m[1]};`).join('\n');
  const java = `${imports}
import java.io.*;
public class TagObjectJspTest {
${declaration}
 static class Request {
   String method="POST", contentType="application/json", origin=null, host="fink.example", tag="1"; byte[] bytes;
   Request(String body) { bytes=body.getBytes(StandardCharsets.UTF_8); }
   String getMethod() { return method; }
   String getContentType() { return contentType; }
   String getHeader(String name) { return name.equals("Origin")?origin:name.equals("Host")?host:name.equals("X-Fink-Tag")?tag:null; }
   long getContentLengthLong() { return bytes.length; }
   InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
 }
 static class Response { int status=200; void setHeader(String k,String v) {} void setStatus(int s) {status=s;} }
 static void execute(Request request,Response response,PrintWriter out) throws Exception {
${body}
 }
 static File bundledDir; static boolean bundleMissing=false;
 static class App { InputStream getResourceAsStream(String name) throws Exception { File f=new File(bundledDir,new File(name).getName()); return bundleMissing||!f.exists()?null:new FileInputStream(f); }
   void log(String m, Throwable t) { lastLog=m; }\n   void log(String m) { } }
 static String lastLog; static App application=new App();
 static String lastBody;
 static int run(Request request) throws Exception {
   Response response=new Response();
   StringWriter writer=new StringWriter(); PrintWriter out=new PrintWriter(writer);
   execute(request,response,out); out.flush(); lastBody=writer.toString();
   return response.status;
 }
 static int post(String id) throws Exception { return run(new Request(new JSONObject().put("objectId",id).toString())); }
 static void check(boolean okay,String msg){ if(!okay) throw new AssertionError(msg+" | last="+lastBody); }
 static String config(File dir,String name,boolean readOnly) throws Exception {
   File f=new File(dir,name+".properties");
   try(FileWriter w=new FileWriter(f)) { w.write("gremlin.graph=org.janusgraph.core.JanusGraphFactory\\nstorage.backend=inmemory\\nstorage.read-only="+readOnly+"\\n"); }
   return f.getPath();
 }
 static long ocolEdges(JanusClient c) {
   return ((Number)c.g().V().has("lbl","OCol").has("cls",TAG).outE("deepcontains").count().next()).longValue();
 }
 public static void main(String[] args) throws Exception {
   try { run(args); System.out.println("TagObject JSP: LSST/ZTF routing, idempotent GraphBrowser tag, fail-closed configuration and request validation passed"); System.out.flush(); System.exit(0); }
   catch (Throwable t) { t.printStackTrace(); System.exit(1); }
 }
 static void run(String[] args) throws Exception {
   File dir=new File(args[0]);
   String lsst="313761043604045880", ztf="ZTF21aaxtctv";
   // 1. Bundled configuration: a read-only or missing one fails closed before anything is opened.
   bundledDir=new File(dir,"bundle"); bundledDir.mkdirs();
   config(bundledDir,"CCRW",true);
   check(post(lsst)==503 && lastLog!=null,"read-only bundled configuration must be refused");
   bundleMissing=true;
   check(post(lsst)==503,"missing bundled configuration must be refused");
   bundleMissing=false;
   config(bundledDir,"CCRW",false); config(bundledDir,"IJCLabRW",false);
   check(recipes("LSST",application.getResourceAsStream("/WEB-INF/janus/CCRW.properties"))!=null,"bundled LSST configuration opens");
   check(recipes("ZTF",application.getResourceAsStream("/WEB-INF/janus/IJCLabRW.properties"))!=null,"bundled ZTF configuration opens");
   check(new File(System.getProperty("java.io.tmpdir")).listFiles((d,n)->n.startsWith("fink-tag-"))==null || new File(System.getProperty("java.io.tmpdir")).listFiles((d,n)->n.startsWith("fink-tag-")).length==0,"no temporary configuration file is left behind");
   // 2. An object that is not in the survey graph is never created by Tag.
   check(post(lsst)==404,"unknown object must be 404");
   JanusClient lsstGraph=CLIENTS.get("LSST");
   check(lsstGraph!=null && ((Number)lsstGraph.g().V().count().next()).longValue()==0,"nothing was created");
   // 3. Existing LSST object gets registered once; repeating is idempotent.
   lsstGraph.g().addV("object").property("lbl","object").property("objectId",lsst).iterate(); lsstGraph.commit();
   check(post(lsst)==200,"tag existing object");
   JSONObject ok=new JSONObject(lastBody);
   check(ok.getBoolean("ok") && !ok.getBoolean("alreadyTagged") && ok.getString("survey").equals("LSST") && ok.getString("tag").equals("GraphBrowser") && ok.getLong("members")==1,"response shape");
   check(ocolEdges(lsstGraph)==1,"one deepcontains edge");
   Object weight=lsstGraph.g().V().has("lbl","OCol").has("cls",TAG).outE("deepcontains").values("weight").next();
   check(((Number)weight).doubleValue()==1.0,"weight 1.0");
   Map<Object,Object> ocol=lsstGraph.g().V().has("lbl","OCol").has("cls",TAG).valueMap("classifier","survey","flavor").next();
   check(String.valueOf(ocol).contains("TAG") && String.valueOf(ocol).contains("ANY"),"TAG classifier, survey ANY: "+ocol);
   check(post(lsst)==200 && new JSONObject(lastBody).getBoolean("alreadyTagged"),"second call reports alreadyTagged");
   check(ocolEdges(lsstGraph)==1,"still exactly one edge after repeating");
   // 4. A ZTF object is written to the ZTF graph only.
   check(post(ztf)==404,"unknown ZTF object");
   JanusClient ztfGraph=CLIENTS.get("ZTF");
   ztfGraph.g().addV("object").property("lbl","object").property("objectId",ztf).iterate(); ztfGraph.commit();
   check(post(ztf)==200 && new JSONObject(lastBody).getString("survey").equals("ZTF"),"tag ZTF object");
   check(ocolEdges(ztfGraph)==1,"ZTF graph tagged");
   check(ocolEdges(lsstGraph)==1 && ((Number)lsstGraph.g().V().has("objectId",ztf).count().next()).longValue()==0,"LSST graph untouched by the ZTF object");
   // 5. Request validation.
   Request get=new Request("{}"); get.method="GET"; check(run(get)==405,"GET refused");
   Request cross=new Request("{\\"objectId\\":\\""+lsst+"\\"}"); cross.origin="https://evil.example"; check(run(cross)==403,"cross-origin refused");
   Request sibling=new Request("{\\"objectId\\":\\""+lsst+"\\"}"); sibling.origin="http://fink.example.evil.com"; check(run(sibling)==403,"look-alike origin refused");
   Request same=new Request("{\\"objectId\\":\\""+lsst+"\\"}"); same.origin="https://fink.example"; same.host="fink.example"; check(run(same)==200,"same origin accepted");
   Request samePort=new Request("{\\"objectId\\":\\""+lsst+"\\"}"); samePort.origin="http://localhost:8888"; samePort.host="localhost:8888"; check(run(samePort)==200,"same origin with port accepted");
   Request noHeader=new Request("{\\"objectId\\":\\""+lsst+"\\"}"); noHeader.tag=null; check(run(noHeader)==400,"custom header required");
   Request form=new Request("objectId="+lsst); form.contentType="application/x-www-form-urlencoded"; check(run(form)==400,"JSON content type required");
   for (String bad : new String[]{"", "ZTF", "12ab", "1');g.V().drop();//", "../../x", "ZTF1'x", "1".repeat(30), "313761043604045880 "}) check(post(bad)==400,"bad id "+bad);
   check(run(new Request("{broken"))==400,"malformed JSON");
   check(run(new Request("{\\"objectId\\":\\""+"1".repeat(600)+"\\"}"))==400,"oversized body");
   check(ocolEdges(lsstGraph)==1 && ocolEdges(ztfGraph)==1,"validation failures wrote nothing");
   }
}`;
  fs.writeFileSync(path.join(dir, 'TagObjectJspTest.java'), java);
  const classpath = [lomikel, orgJson, path.join(janus, '*')].join(path.delimiter);
  for (const [cmd, args] of [['javac', ['-nowarn', '-cp', classpath, 'TagObjectJspTest.java']],
                             ['java', ['-cp', `${dir}${path.delimiter}${classpath}`, 'TagObjectJspTest', dir]]]) {
    const r = spawnSync(cmd, args, {cwd: dir, encoding: 'utf8', timeout: 180000});
    assert.equal(r.status, 0, `${cmd}: ${r.stdout}${r.stderr}`.slice(-4000));
    if (cmd === 'java') process.stdout.write(r.stdout.split('\n').filter(line => /^TagObject JSP/.test(line)).join('\n') + '\n');
  }
} finally { fs.rmSync(dir, {recursive: true, force: true}); }

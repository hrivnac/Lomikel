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
assert.match(page, /\(\(TagClassifier\)Classifier\.instance\("TAG", survey, ""\)\)\.classify\(gr, objectId, tag, value, author, annotation, ref\)/);
assert.match(page, /getResourceAsStream\(TAG_NAMES\)/);
const tagNames = fs.readFileSync(path.join(root, 'src/java/com/astrolabsoftware/FinkBrowser/Januser/TagNames.txt'), 'utf8')
  .split('\n').map(line => line.replace(/#.*/, '').trim()).filter(Boolean);
assert.deepEqual(tagNames, ['GraphBrowser', 'Temp']);
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
 static int post(String id) throws Exception { return tag(id, "GraphBrowser", 1.0, null, null, null); }
 static int tag(String id, String name, Object value, String author, String annotation, String ref) throws Exception {
   JSONObject b=new JSONObject().put("objectId",id).put("tag",name);
   if (value!=null) b.put("value",value);
   if (author!=null) b.put("author",author); if (annotation!=null) b.put("annotation",annotation); if (ref!=null) b.put("ref",ref);
   return run(new Request(b.toString()));
 }
 static void check(boolean okay,String msg){ if(!okay) throw new AssertionError(msg+" | last="+lastBody); }
 static String config(File dir,String name,boolean readOnly) throws Exception {
   File f=new File(dir,name+".properties");
   try(FileWriter w=new FileWriter(f)) { w.write("gremlin.graph=org.janusgraph.core.JanusGraphFactory\\nstorage.backend=inmemory\\nstorage.read-only="+readOnly+"\\n"); }
   return f.getPath();
 }
 static long ocolEdges(JanusClient c) { return ocolEdges(c,"GraphBrowser"); }
 static long ocolEdges(JanusClient c, String name) {
   return ((Number)c.g().V().has("lbl","OCol").has("cls",name).outE("deepcontains").count().next()).longValue();
 }
 static void createTag(JanusClient c, String name) {
   c.g().addV("OCol").property("lbl","OCol").property("survey","ANY").property("classifier","TAG").property("flavor","").property("cls",name).iterate(); c.commit();
 }
 static long ocols(JanusClient c, String name) { return ((Number)c.g().V().has("lbl","OCol").has("cls",name).count().next()).longValue(); }
 public static void main(String[] args) throws Exception {
   try { run(args); System.out.println("TagObject JSP: LSST/ZTF routing, configured tag names, existing tag types only, classify() with value/author/annotation/ref, fail-closed configuration and request validation passed"); System.out.flush(); System.exit(0); }
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
   // 1b. Unreachable HBase fails fast with a clear message instead of hanging.
   File unreachable=new File(bundledDir,"unreachable.properties");
   try(FileWriter w=new FileWriter(unreachable)) { w.write("gremlin.graph=org.janusgraph.core.JanusGraphFactory\\nstorage.backend=hbase\\nstorage.hostname=127.0.0.1\\nstorage.port=1\\nstorage.read-only=false\\n"); }
   discard("LSST");
   System.setProperty("fink.tag.janus.LSST",unreachable.getPath());
   long started=System.currentTimeMillis();
   check(post(lsst)==503,"unreachable HBase must be 503");
   check(System.currentTimeMillis()-started<5000,"unreachable HBase must fail fast");
   check(lastBody.contains("HBase 127.0.0.1:1 is not reachable from this server"),"clear reason");
   System.clearProperty("fink.tag.janus.LSST"); discard("LSST");
   // 1c. HBase "reachable" at TCP level but never answering: the open is bounded instead of hanging.
   try (java.net.ServerSocket hole=new java.net.ServerSocket(0)) {
     Thread acceptor=new Thread(() -> { try { while (true) { java.net.Socket c=hole.accept(); } } catch (Exception e) { } }); acceptor.setDaemon(true); acceptor.start();
     File silent=new File(bundledDir,"silent.properties");
     try(FileWriter w=new FileWriter(silent)) { w.write("gremlin.graph=org.janusgraph.core.JanusGraphFactory\\nstorage.backend=hbase\\nstorage.hostname=127.0.0.1\\nstorage.port="+hole.getLocalPort()+"\\nstorage.read-only=false\\n"); }
     System.setProperty("fink.tag.janus.LSST",silent.getPath()); System.setProperty("fink.tag.open.timeout","4");
     long begun=System.currentTimeMillis();
     check(post(lsst)==503,"silent HBase must be 503");
     check(System.currentTimeMillis()-begun<9000,"silent HBase must be bounded");
     check(lastBody.contains("did not open within 4 s"),"clear timeout reason");
     long again=System.currentTimeMillis();
     check(post(lsst)==503 && System.currentTimeMillis()-again<9000,"a second request reuses the pending open");
     check(OPENING.size()==1,"only one open is ever pending");
     }
   OPENING.clear(); System.clearProperty("fink.tag.janus.LSST"); System.clearProperty("fink.tag.open.timeout"); discard("LSST");
   // 2. An object that is not in the survey graph is never created by Tag.
   check(post(lsst)==404,"unknown object must be 404");
   JanusClient lsstGraph=CLIENTS.get("LSST");
   check(lsstGraph!=null && ((Number)lsstGraph.g().V().count().next()).longValue()==0,"nothing was created");
   // 3. GET lists the configured tag names (no graph needed).
   Request names=new Request(""); names.method="GET";
   check(run(names)==200 && new JSONObject(lastBody).getJSONArray("tags").toList().equals(java.util.Arrays.asList("GraphBrowser","Temp")),"GET lists TagNames.txt");
   // 4. Existing LSST object: classify() writes one edge with value, author, annotation and ref.
   lsstGraph.g().addV("object").property("lbl","object").property("objectId",lsst).iterate(); lsstGraph.commit();
   // A tag type that does not exist is refused: the browser never creates OCols (nor through classify).
   check(tag(lsst,"GraphBrowser",0.5,null,null,null)==404 && lastBody.contains("Tag GraphBrowser does not exist in the LSST graph"),"missing tag type refused");
   check(ocols(lsstGraph,"GraphBrowser")==0 && ((Number)lsstGraph.g().E().count().next()).longValue()==0,"nothing created for a missing tag type");
   // An OCol of that name but not matching registerOCol's lookup (no survey, as TagClassifier.createTag makes) is refused too.
   lsstGraph.g().addV("OCol").property("lbl","OCol").property("classifier","TAG").property("flavor","").property("cls","GraphBrowser").iterate(); lsstGraph.commit();
   check(tag(lsst,"GraphBrowser",0.5,null,null,null)==404 && ocols(lsstGraph,"GraphBrowser")==1,"OCol without survey refused, no duplicate created");
   lsstGraph.g().V().has("lbl","OCol").has("cls","GraphBrowser").drop().iterate(); lsstGraph.commit();
   createTag(lsstGraph,"GraphBrowser");
   check(tag(lsst,"GraphBrowser",0.5,"Julius.Hrivnac@cern.ch","muj pokus","blablabla")==200,"tag existing object");
   JSONObject ok=new JSONObject(lastBody);
   check(ok.getBoolean("ok") && ok.getString("survey").equals("LSST") && ok.getString("tag").equals("GraphBrowser") &&
         ok.getLong("entries")==1 && ok.getDouble("value")==0.5 && !ok.has("ocolCreated"),"response shape");
   check(ocolEdges(lsstGraph)==1,"one deepcontains edge");
   Map<Object,Object> edge=lsstGraph.g().V().has("lbl","OCol").has("cls","GraphBrowser").outE("deepcontains").valueMap().next();
   check(String.valueOf(edge.get("weight")).equals("0.5") && "Julius.Hrivnac@cern.ch".equals(edge.get("author")) &&
         "muj pokus".equals(edge.get("annotation")) && "blablabla".equals(edge.get("ref")),"edge attributes: "+edge);
   Map<Object,Object> ocol=lsstGraph.g().V().has("lbl","OCol").has("cls","GraphBrowser").valueMap("classifier","survey","flavor").next();
   check(String.valueOf(ocol).contains("TAG") && String.valueOf(ocol).contains("ANY"),"TAG classifier, survey ANY: "+ocol);
   // A second tagging adds another entry (classify keeps earlier ones); no duplicate OCol; blank texts are not stored.
   check(tag(lsst,"GraphBrowser",2,"","  ",null)==200 && new JSONObject(lastBody).getLong("entries")==2,"second entry (duplicates are fine)");
   check(ocolEdges(lsstGraph)==2 && ocols(lsstGraph,"GraphBrowser")==1,"two entries, one OCol");
   check(lsstGraph.g().V().has("lbl","OCol").has("cls","GraphBrowser").outE("deepcontains").has("weight",2.0).hasNext() &&
         !lsstGraph.g().V().has("lbl","OCol").has("cls","GraphBrowser").outE("deepcontains").has("weight",2.0).has("author").hasNext() &&
         !lsstGraph.g().V().has("lbl","OCol").has("cls","GraphBrowser").outE("deepcontains").has("weight",2.0).has("annotation").hasNext(),"blank texts are not stored");
   // The other configured tag, once it exists, is a separate OCol.
   check(tag(lsst,"Temp",1.0,"me",null,"https://x.example/a?b=1")==404 && ocols(lsstGraph,"Temp")==0,"Temp not created");
   createTag(lsstGraph,"Temp");
   check(tag(lsst,"Temp",1.0,"me",null,"https://x.example/a?b=1")==200,"Temp tag");
   check(ocols(lsstGraph,"Temp")==1 && ocolEdges(lsstGraph,"Temp")==1 && ocolEdges(lsstGraph)==2,"Temp is separate");
   // Control characters are flattened; limits and types are enforced; nothing is written on refusal.
   check(tag(lsst,"Temp",1.0,"a\\u0001b\\nc",null,null)==200,"control characters accepted after flattening");
   check(lsstGraph.g().V().has("lbl","OCol").has("cls","Temp").outE("deepcontains").has("author","a b c").hasNext(),"flattened author");
   long tempBefore=ocolEdges(lsstGraph,"Temp");
   Object[][] refused={{"Unknown",1.0,null,null,null,"Unknown tag name"},{"",1.0,null,null,null,"Unknown tag name"},{"GraphBrowser') .drop()",1.0,null,null,null,"Unknown tag name"},
     {"Temp",-1.0,null,null,null,"finite number"},{"Temp","1.0",null,null,null,"must be a number"},{"Temp",null,null,null,null,"must be a number"},
     {"Temp",1.0,"x".repeat(201),null,null,"author is too long"},{"Temp",1.0,null,"x".repeat(2001),null,"annotation is too long"},{"Temp",1.0,null,null,"x".repeat(1001),"ref is too long"}};
   for (Object[] r : refused) check(tag(lsst,(String)r[0],r[1],(String)r[2],(String)r[3],(String)r[4])==400 && lastBody.contains((String)r[5]),"refused "+r[0]+" "+r[1]+" "+r[5]);
   check(run(new Request("{\\"objectId\\":\\""+lsst+"\\",\\"tag\\":\\"Temp\\",\\"value\\":1,\\"author\\":5}"))==400,"non-string author");
   check(run(new Request("{\\"objectId\\":\\""+lsst+"\\",\\"tag\\":\\"Temp\\",\\"value\\":1e999}"))==400,"infinite value");
   check(ocolEdges(lsstGraph,"Temp")==tempBefore && ocols(lsstGraph,"Unknown")==0,"refusals wrote nothing");
   // 5. A ZTF object is written to the ZTF graph only.
   check(post(ztf)==404,"unknown ZTF object");
   JanusClient ztfGraph=CLIENTS.get("ZTF");
   ztfGraph.g().addV("object").property("lbl","object").property("objectId",ztf).iterate(); ztfGraph.commit();
   createTag(ztfGraph,"GraphBrowser");
   check(post(ztf)==200 && new JSONObject(lastBody).getString("survey").equals("ZTF"),"tag ZTF object");
   check(ocolEdges(ztfGraph)==1,"ZTF graph tagged");
   check(ocolEdges(lsstGraph)==2 && ((Number)lsstGraph.g().V().has("objectId",ztf).count().next()).longValue()==0,"LSST graph untouched by the ZTF object");
   // 6. Request validation.
   Request put=new Request("{}"); put.method="PUT"; check(run(put)==405,"PUT refused");
   long lsstBefore=ocolEdges(lsstGraph);
   Request cross=new Request("{\\"objectId\\":\\""+lsst+"\\",\\"tag\\":\\"GraphBrowser\\",\\"value\\":1}"); cross.origin="https://evil.example"; check(run(cross)==403,"cross-origin refused");
   Request sibling=new Request("{\\"objectId\\":\\""+lsst+"\\",\\"tag\\":\\"GraphBrowser\\",\\"value\\":1}"); sibling.origin="http://fink.example.evil.com"; check(run(sibling)==403,"look-alike origin refused");
   Request same=new Request("{\\"objectId\\":\\""+lsst+"\\",\\"tag\\":\\"GraphBrowser\\",\\"value\\":1}"); same.origin="https://fink.example"; same.host="fink.example"; check(run(same)==200,"same origin accepted");
   Request samePort=new Request("{\\"objectId\\":\\""+lsst+"\\",\\"tag\\":\\"GraphBrowser\\",\\"value\\":1}"); samePort.origin="http://localhost:8888"; samePort.host="localhost:8888"; check(run(samePort)==200,"same origin with port accepted");
   Request noHeader=new Request("{\\"objectId\\":\\""+lsst+"\\",\\"tag\\":\\"GraphBrowser\\",\\"value\\":1}"); noHeader.tag=null; check(run(noHeader)==400,"custom header required");
   Request form=new Request("objectId="+lsst); form.contentType="application/x-www-form-urlencoded"; check(run(form)==400,"JSON content type required");
   for (String bad : new String[]{"", "ZTF", "12ab", "1');g.V().drop();//", "../../x", "ZTF1'x", "1".repeat(30), "313761043604045880 "}) check(post(bad)==400,"bad id "+bad);
   check(run(new Request("{broken"))==400,"malformed JSON");
   check(run(new Request("{\\"objectId\\":\\""+lsst+"\\",\\"tag\\":\\"Temp\\",\\"value\\":1,\\"annotation\\":\\""+"x".repeat(9000)+"\\"}"))==400,"oversized body");
   check(ocolEdges(lsstGraph)==lsstBefore+2 && ocolEdges(ztfGraph)==1,"only the two accepted same-origin requests wrote");
   }
}`;
  fs.writeFileSync(path.join(dir, 'TagObjectJspTest.java'), java);
  const res = path.join(dir, 'res/com/astrolabsoftware/FinkBrowser/Januser');
  fs.mkdirSync(res, {recursive: true});
  fs.copyFileSync(path.join(root, 'src/java/com/astrolabsoftware/FinkBrowser/Januser/TagNames.txt'), path.join(res, 'TagNames.txt'));
  const classpath = [lomikel, orgJson, path.join(janus, '*')].join(path.delimiter);
  for (const [cmd, args] of [['javac', ['-nowarn', '-cp', classpath, 'TagObjectJspTest.java']],
                             ['java', ['-Djava.io.tmpdir=' + dir, '-cp', `${dir}${path.delimiter}${path.join(dir, 'res')}${path.delimiter}${classpath}`, 'TagObjectJspTest', dir]]]) {
    const r = spawnSync(cmd, args, {cwd: dir, encoding: 'utf8', timeout: 180000});
    assert.equal(r.status, 0, `${cmd}: ${r.stdout}${r.stderr}`.slice(-4000));
    if (cmd === 'java') process.stdout.write(r.stdout.split('\n').filter(line => /^TagObject JSP/.test(line)).join('\n') + '\n');
  }
} finally { fs.rmSync(dir, {recursive: true, force: true}); }

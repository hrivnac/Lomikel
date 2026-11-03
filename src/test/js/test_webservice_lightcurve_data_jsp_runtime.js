'use strict';
// Runs the real LightCurveData.jsp scriptlet against loopback Fink/ES fixtures:
// DIA objects use /sources, SS objects reverse-resolve and use /sso (never /sources).
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'../../..');
const page=fs.readFileSync(path.join(root,'src/webservice/LightCurveData.jsp'),'utf8');
const declaration=page.match(/<%!([\s\S]*?)%>/)[1];
const body=page.slice(page.indexOf('%>',page.indexOf('<%!'))+2).match(/<%([\s\S]*?)%>/)[1];
const jar=path.join(root,'extlib/import/org.json.jar');
const dir=fs.mkdtempSync(path.join(process.env.TMPDIR||path.join(os.homedir(),'.hermes/cache/scratch'),'lightcurve-data-'));
try {
 const java=`import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.sun.net.httpserver.HttpServer;
import org.json.*;
public class LightCurveDataJspTest {
${declaration.replace('private final java.util.concurrent.Semaphore','private static final java.util.concurrent.Semaphore')}
 static final String SS="21163637480633927", DIA="313761043604045880";
 static class Request {
   byte[] bytes;
   Request(String id) { bytes=new JSONObject().put("objectId",id).toString().getBytes(StandardCharsets.UTF_8); }
   String getMethod() { return "POST"; }
   long getContentLengthLong() { return bytes.length; }
   InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
 }
 static class Response { int status=200; void setHeader(String k,String v) {} void setStatus(int s) {status=s;} }
 static void execute(Request request,Response response,PrintWriter out) throws Exception {
${body}
 }
 static JSONObject run(String id,int expected) throws Exception {
   Response response=new Response();
   StringWriter writer=new StringWriter(); PrintWriter out=new PrintWriter(writer);
   execute(new Request(id),response,out);
   out.flush();
   if (response.status!=expected) throw new AssertionError("HTTP "+response.status+" "+writer);
   return new JSONObject(writer.toString());
 }
 static void check(boolean okay,String msg){if(!okay)throw new AssertionError(msg);}
 static JSONObject row(String idKey,String id,String source,double mjd,String band) {
   return new JSONObject().put(idKey,idKey.equals("r:ssObjectId")?Long.parseLong(id):id)
     .put("r:diaSourceId",Long.parseLong(source)).put("r:midpointMjdTai",mjd).put("r:band",band)
     .put("r:scienceFlux",1000.0).put("r:scienceFluxErr",10.0);
 }
 public static void main(String[] args) throws Exception {
   HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
   ArrayList<String> paths=new ArrayList<>(), bodies=new ArrayList<>();
   int[] mode={0}; // 0 normal, 1 mixed SS rows, 2 ES broken, 3 resolver misses, 4 ES unavailable+empty DIA
   server.createContext("/",exchange->{
     String endpoint=exchange.getRequestURI().getPath();
     String sent=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
     paths.add(endpoint);bodies.add(sent);
     JSONObject request=new JSONObject(sent);
     String result; int status=200;
     if(endpoint.equals("/ss_radec/_mget")) {
       String id=request.getJSONArray("docs").getJSONObject(0).getString("_id");
       JSONObject doc=new JSONObject().put("_id",id);
       if(mode[0]==2) { status=500; result="{}"; }
       else { if(mode[0]!=5) doc.put("found",id.equals(SS)); result=new JSONObject().put("docs",new JSONArray().put(doc)).toString(); }
     } else if(endpoint.equals("/api/v1/resolver")) {
       result=mode[0]==3?"[]":new JSONArray().put(new JSONObject().put("r:ssObjectId",Long.parseLong(SS))
         .put("r:packed_primary_provisional_designation","K07V06G")).toString();
     } else if(endpoint.equals("/api/v1/sso")) {
       check("K07V06G".equals(request.getString("n_or_d")),"SSO must use the resolved packed designation");
       result=new JSONArray().put(row("r:ssObjectId",SS,"800000000000000002",61002.5,"g"))
         .put(row("r:ssObjectId",mode[0]==1?DIA:SS,"800000000000000001",61001.5,"r")).toString();
     } else if(endpoint.equals("/api/v1/sources")) {
       check(!request.has("ssObjectId") && request.getString("diaObjectId").equals(request.getString("diaObjectId")),"bad DIA request");
       result=request.getString("diaObjectId").equals(SS)?"[]":new JSONArray()
         .put(row("r:diaObjectId",DIA,"700000000000000001",61001.0,"g")).toString();
     } else throw new AssertionError("Unexpected upstream path: "+endpoint);
     byte[] bytes=result.getBytes(StandardCharsets.UTF_8);
     exchange.sendResponseHeaders(status,bytes.length);
     try(OutputStream output=exchange.getResponseBody()){output.write(bytes);}
   });
   server.start();
   String base="http://127.0.0.1:"+server.getAddress().getPort();
   System.setProperty("fink.lightcurve.esUrl",base);System.setProperty("fink.lightcurve.apiUrl",base);
   try {
     JSONObject dia=run(DIA,200);
     check("DIA".equals(dia.getString("objectType")) && dia.getJSONArray("points").length()==1,"DIA light curve changed");
     check(!paths.contains("/api/v1/resolver") && !paths.contains("/api/v1/sso"),"DIA must not use SS endpoints");
     paths.clear();
     JSONObject ss=run(SS,200);
     check("SS".equals(ss.getString("objectType")) && "LSST".equals(ss.getString("survey")) && SS.equals(ss.getString("objectId")),"SS identity wrong");
     JSONArray points=ss.getJSONArray("points");
     check(points.length()==2 && points.getJSONObject(0).getDouble("mjd")<points.getJSONObject(1).getDouble("mjd"),"SS points must be time-sorted");
     check(points.getJSONObject(0).getString("band").equals("r") && ss.getInt("excluded")==0,"SS bands/exclusions wrong");
     check(paths.equals(Arrays.asList("/ss_radec/_mget","/api/v1/resolver","/api/v1/sso")),"SS must reverse-resolve then use /sso, not /sources: "+paths);
     mode[0]=1; run(SS,502);   // rows of another object must fail closed
     mode[0]=3; run(SS,502);   // unresolved SS object must fail, not draw an empty curve
     mode[0]=2; paths.clear();
     run(SS,502);               // ES down: empty DIA lookup is not an empty light curve
     check(paths.contains("/api/v1/sources"),"ES outage must not block a DIA lookup");
     run(DIA,200);              // ES down but DIA data exists: still served
     mode[0]=5; run(SS,502);   // malformed ES identity answer is unknown, not DIA
     run("not-an-id",400);
     System.out.println("LightCurveData JSP: DIA, SS (resolver then /sso), contamination and outage cases passed");
   } finally {server.stop(0);}
 }
}`;
 fs.writeFileSync(path.join(dir,'LightCurveDataJspTest.java'),java);
 for(const [cmd,args] of [['javac',['-cp',jar,'LightCurveDataJspTest.java']],
                          ['java',['-cp',`${dir}${path.delimiter}${jar}`,'LightCurveDataJspTest']]]){
   const r=spawnSync(cmd,args,{cwd:dir,encoding:'utf8',timeout:60000});
   assert.equal(r.status,0,`${cmd}: ${r.stdout}${r.stderr}`);
   if(cmd==='java')process.stdout.write(r.stdout);
 }
} finally {fs.rmSync(dir,{recursive:true,force:true});}

'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'../../..');
const page=fs.readFileSync(path.join(root,'src/webservice/CutoutSource.jsp'),'utf8');
const declaration=page.match(/<%!([\s\S]*?)%>/)[1];
const body=page.slice(page.indexOf('%>',page.indexOf('<%!'))+2).match(/<%([\s\S]*?)%>/)[1];
const jar=path.join(root,'extlib/import/org.json.jar');
const dir=fs.mkdtempSync(path.join(process.env.TMPDIR||path.join(os.homedir(),'.hermes/cache/scratch'),'cutout-source-'));
try {
 const java=`import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.sun.net.httpserver.HttpServer;
import org.json.*;
public class CutoutSourceJspTest {
${declaration}
 static final String SS="21163615905265241", DIA="313761043604045880";
 static class Request {
   byte[] bytes;
   Request(String id) { bytes=new JSONObject().put("objectId",id).toString().getBytes(StandardCharsets.UTF_8); }
   String getMethod() { return "POST"; }
   InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
 }
 static class Response { int status=200; void setHeader(String k,String v) {} void setStatus(int s) {status=s;} }
 static void execute(Request request,Response response,PrintWriter out) throws Exception {
${body}
 }
 static JSONObject run(String id,int expected) throws Exception {
   Request request=new Request(id); Response response=new Response();
   StringWriter writer=new StringWriter(); PrintWriter out=new PrintWriter(writer);
   execute(request,response,out);
   out.flush();
   if (response.status!=expected) throw new AssertionError("HTTP "+response.status+" "+writer);
   return new JSONObject(writer.toString());
 }
 static void check(boolean okay,String msg){if(!okay)throw new AssertionError(msg);}
 public static void main(String[] args) throws Exception {
   HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
   ArrayList<String> paths=new ArrayList<>(), bodies=new ArrayList<>();
   int[] mode={0};
   server.createContext("/",exchange->{
     String endpoint=exchange.getRequestURI().getPath();
     String sent=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
     paths.add(endpoint);bodies.add(sent);
     JSONObject request=new JSONObject(sent);
     String result;
     if(endpoint.equals("/ss_radec/_mget")) {
       String id=request.getJSONArray("docs").getJSONObject(0).getString("_id");
       JSONObject doc=new JSONObject().put("_id",id);
       if(mode[0]!=4) doc.put("found",id.equals(SS));
       result=new JSONObject().put("docs",new JSONArray().put(doc)).toString();
     } else if(endpoint.equals("/api/v1/resolver")) {
       result=new JSONArray().put(new JSONObject().put("r:ssObjectId",SS)
         .put("r:packed_primary_provisional_designation","K02P26Y")).toString();
     } else if(endpoint.equals("/api/v1/sso")) {
       JSONArray rows=new JSONArray().put(new JSONObject().put("r:ssObjectId",SS)
         .put("r:diaSourceId","800000000000000001").put("r:midpointMjdTai",61003.0))
         .put(new JSONObject().put("r:ssObjectId",mode[0]==1?DIA:SS)
           .put("r:diaSourceId","800000000000000002").put("r:midpointMjdTai",61002.0));
       result=mode[0]==2?" ".repeat(4194305):mode[0]==3?"{broken":rows.toString();
     } else if(endpoint.equals("/api/v1/sources")) {
       result=new JSONArray().put(new JSONObject().put("r:diaSourceId","700000000000000002"))
         .put(new JSONObject().put("r:diaSourceId","700000000000000001")).toString();
     } else throw new AssertionError("Unexpected upstream path: "+endpoint);
     byte[] bytes=result.getBytes(StandardCharsets.UTF_8);
     exchange.sendResponseHeaders(200,bytes.length);
     try(OutputStream output=exchange.getResponseBody()){output.write(bytes);}
   });
   server.start();
   String base="http://127.0.0.1:"+server.getAddress().getPort();
   System.setProperty("fink.cutout.esUrl",base);System.setProperty("fink.cutout.apiUrl",base);
   try {
     JSONObject dia=run(DIA,200);
     check("700000000000000002".equals(dia.getString("diaSourceId")),"DIA newest source changed");
     check(dia.getJSONArray("finkRequests").length()==1 &&
       dia.getJSONArray("finkRequests").getJSONObject(0).getString("path").equals("/api/v1/sources"),
       "DIA lookup provenance wrong");
     check(paths.get(0).equals("/ss_radec/_mget") && paths.get(1).equals("/api/v1/sources"),
       "DIA must not hit the SS designation endpoint");
     JSONObject ss=run(SS,200);
     check("800000000000000001".equals(ss.getString("diaSourceId")),"latest SS source not selected by MJD");
     check(ss.getJSONArray("finkRequests").length()==2 &&
       ss.getJSONArray("finkRequests").getJSONObject(0).getString("path").equals("/api/v1/resolver") &&
       ss.getJSONArray("finkRequests").getJSONObject(1).getString("path").equals("/api/v1/sso"),
       "SS lookup must reverse-resolve and use paired source rows");
     check(paths.get(2).equals("/ss_radec/_mget") && paths.get(3).equals("/api/v1/resolver") &&
       paths.get(4).equals("/api/v1/sso"),"SS API dispatch wrong");
     mode[0]=1;
     JSONObject contaminated=run(SS,502);
     check(contaminated.has("error") && contaminated.getJSONArray("finkRequests").length()==2,
       "mixed-object SS rows must fail with executed request provenance");
     mode[0]=2;
     JSONObject oversized=run(SS,502);
     check(oversized.has("error") && oversized.getJSONArray("finkRequests").length()==2,
       "oversized upstream history is a gateway failure, not a malformed client request");
     mode[0]=3;
     JSONObject malformedUpstream=run(SS,502);
     check(malformedUpstream.getJSONArray("finkRequests").length()==2,
       "malformed upstream SSO JSON is not a client input error");
     mode[0]=4;
     JSONObject unknownIdentity=run(DIA,502);
     check(unknownIdentity.getJSONArray("finkRequests").length()==0,
       "missing ES found flag must not misclassify an SS object as DIA");
     JSONObject invalidClient=run("not-an-id",400);
     check(invalidClient.getJSONArray("esRequests").length()==0,
       "bad client input fails before any upstream lookup");
     System.out.println("CutoutSource JSP: DIA and most recent SS source, provenance and contamination passed");
   } finally {server.stop(0);}
 }
}`;
 fs.writeFileSync(path.join(dir,'CutoutSourceJspTest.java'),java);
 for(const [cmd,args] of [['javac',['-cp',jar,'CutoutSourceJspTest.java']],
                          ['java',['-cp',`${dir}${path.delimiter}${jar}`,'CutoutSourceJspTest']]]){
   const r=spawnSync(cmd,args,{cwd:dir,encoding:'utf8',timeout:60000});
   assert.equal(r.status,0,`${cmd}: ${r.stdout}${r.stderr}`);
   if(cmd==='java')process.stdout.write(r.stdout);
 }
} finally {fs.rmSync(dir,{recursive:true,force:true});}

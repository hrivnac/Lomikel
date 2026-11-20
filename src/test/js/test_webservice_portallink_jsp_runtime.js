'use strict';
// PortalLink.jsp: a numeric LSST object ID is redirected to its Fink Portal page:
// /<diaObjectId> for DIA objects, /<packed designation> for Solar System objects.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'../../..');
const page=fs.readFileSync(path.join(root,'src/webservice/PortalLink.jsp'),'utf8');
const declaration=page.match(/<%!([\s\S]*?)%>/)[1];
const body=page.slice(page.indexOf('%>',page.indexOf('<%!'))+2).match(/<%([\s\S]*?)%>/)[1];
assert.doesNotMatch(page,/getRemoteUser|Authorization|password/i,'the page needs no credentials');
const jar=path.join(root,'extlib/import/org.json.jar');
const dir=fs.mkdtempSync(path.join(process.env.TMPDIR||path.join(os.homedir(),'.hermes/cache/scratch'),'portal-link-'));
try {
 const java=`import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.sun.net.httpserver.HttpServer;
import org.json.*;
public class PortalLinkJspTest {
${declaration}
 static final String SS="20892028060578637", DIA="313761043604045880";
 static final String[] PORTAL_ARG={null};
 static class Request {
   String method, id, portal;
   Request(String method,String id){this(method,id,null);}
   Request(String method,String id,String portal){this.method=method;this.id=id;this.portal=portal;}
   String getMethod(){return method;}
   String getParameter(String name){return "objectId".equals(name)?id:"portal".equals(name)?portal:null;}
 }
 static class Response {
   int status=200; Map<String,String> headers=new HashMap<>();
   void setHeader(String k,String v){headers.put(k,v);} void setStatus(int s){status=s;}
 }
 static void execute(Request request,Response response,PrintWriter out) throws Exception {
${body}
 }
 static String[] run(String method,String id,int expected) throws Exception {
   Response response=new Response(); StringWriter writer=new StringWriter(); PrintWriter out=new PrintWriter(writer);
   execute(new Request(method,id,PORTAL_ARG[0]),response,out); out.flush();
   if(response.status!=expected) throw new AssertionError("HTTP "+response.status+" for "+id+": "+writer);
   return new String[]{response.headers.get("Location"),writer.toString(),response.headers.get("Cache-Control")};
 }
 static void check(boolean okay,String msg){if(!okay)throw new AssertionError(msg);}
 public static void main(String[] args) throws Exception {
   try {
   HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
   ArrayList<String> paths=new ArrayList<>(), bodies=new ArrayList<>();
   int[] mode={0};
   server.createContext("/",exchange->{
     String endpoint=exchange.getRequestURI().getPath();
     String sent=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
     paths.add(endpoint);bodies.add(sent);
     JSONObject request=new JSONObject(sent);
     String result; int status=200;
     if(endpoint.equals("/ss_radec/_mget")) {
       String id=request.getJSONArray("docs").getJSONObject(0).getString("_id");
       JSONObject doc=new JSONObject().put("_id",id);
       if(mode[0]!=1) doc.put("found",id.equals(SS));
       result=new JSONObject().put("docs",new JSONArray().put(doc)).toString();
     } else if(endpoint.equals("/api/v1/resolver")) {
       JSONObject row=new JSONObject().put("r:ssObjectId",mode[0]==2?"1":SS)
         .put("r:unpacked_primary_provisional_designation","1990 WM3");
       if(mode[0]!=3) row.put("r:packed_primary_provisional_designation",mode[0]==4?"J90<b>":"J90W03M");
       JSONArray rows=new JSONArray().put(row);
       if(mode[0]==5) rows.put(new JSONObject(row.toString()));
       result=mode[0]==6?"{broken":rows.toString();
       if(mode[0]==7) status=500;
     } else throw new AssertionError("Unexpected upstream path: "+endpoint);
     byte[] bytes=result.getBytes(StandardCharsets.UTF_8);
     exchange.sendResponseHeaders(status,bytes.length);
     try(OutputStream output=exchange.getResponseBody()){output.write(bytes);}
   });
   server.start();
   String base="http://127.0.0.1:"+server.getAddress().getPort();
   System.setProperty("fink.portal.esUrl",base);System.setProperty("fink.portal.apiUrl",base);
   try {
     String[] dia=run("GET",DIA,302);
     check(("https://lsst.fink-portal.org/"+DIA).equals(dia[0]),"DIA page is addressed by diaObjectId: "+dia[0]);
     check("no-store".equals(dia[2]),"redirect must not be cached");
     check(paths.equals(List.of("/ss_radec/_mget")),"DIA needs only the identity lookup: "+paths);
     check(new JSONObject(bodies.get(0)).getJSONArray("docs").getJSONObject(0).getBoolean("_source")==false,
       "identity-only lookup transfers no source document");
     String[] ss=run("GET",SS,302);
     check("https://lsst.fink-portal.org/J90W03M".equals(ss[0]),"SS page is addressed by packed designation: "+ss[0]);
     check(paths.subList(1,3).equals(List.of("/ss_radec/_mget","/api/v1/resolver")),"SS dispatch wrong: "+paths);
     JSONObject resolver=new JSONObject(bodies.get(2));
     check(SS.equals(resolver.getString("name_or_id")) && resolver.getBoolean("reverse") &&
       "ssodnet".equals(resolver.getString("resolver")),"exact reverse resolution of the ssObjectId");
     int before=paths.size();
     for(String bad:new String[]{null,"","0123","12345678901234567890","ZTF20abc","1')","1 2"}){
       String[] invalid=run("GET",bad,400);
       check(invalid[0]==null,"no redirect for invalid input "+bad);
     }
     check(paths.size()==before,"invalid input never reaches an upstream service");
     check(run("POST",DIA,405)[0]==null,"GET only");
     // Return to the Portal that sent the object: the public one or a loopback development Portal.
     for(String[] ok:new String[][]{{"http://127.0.0.1:24470","http://127.0.0.1:24470/"},
                                    {"http://localhost:24000","http://localhost:24000/"},
                                    {"http://[::1]:8050","http://[::1]:8050/"},
                                    {"https://lsst.fink-portal.org","https://lsst.fink-portal.org/"},
                                    {"","https://lsst.fink-portal.org/"}}){
       PORTAL_ARG[0]=ok[0];
       check((ok[1]+DIA).equals(run("GET",DIA,302)[0]),"DIA redirect to "+ok[0]);
       check((ok[1]+"J90W03M").equals(run("GET",SS,302)[0]),"SS redirect to "+ok[0]);
     }
     before=paths.size();
     for(String bad:new String[]{"https://evil.example","https://lsst.fink-portal.org.evil.example",
         "http://127.0.0.1:24470/path","http://user@127.0.0.1:24470","javascript:alert(1)",
         "http://127.0.0.1:99999x","//127.0.0.1:24470","http://10.0.0.1:24470"}){
       PORTAL_ARG[0]=bad;
       check(run("GET",DIA,400)[0]==null,"no redirect to an unexpected Portal "+bad);
     }
     check(paths.size()==before,"an unexpected Portal is refused before any lookup");
     PORTAL_ARG[0]=null;
     for(int m:new int[]{1,2,3,4,5,6,7}){
       mode[0]=m;
       String[] failed=run("GET",m==1?DIA:SS,502);
       check(failed[0]==null,"mode "+m+" must not redirect");
       check(!failed[1].contains("<b>"),"upstream text is never echoed as markup");
       check(failed[1].contains(m==1?DIA:SS),"the message names the object");
     }
     System.out.println("PortalLink JSP: DIA and SS redirects, invalid input and lookup failures passed");
   } finally {server.stop(0);}
   } catch(Throwable error){error.printStackTrace();System.exit(1);}
 }
}`;
 fs.writeFileSync(path.join(dir,'PortalLinkJspTest.java'),java);
 for(const [cmd,args] of [['javac',['-cp',jar,'PortalLinkJspTest.java']],
                          ['java',['-cp',`${dir}${path.delimiter}${jar}`,'PortalLinkJspTest']]]){
   const r=spawnSync(cmd,args,{cwd:dir,encoding:'utf8',timeout:60000});
   assert.equal(r.status,0,`${cmd}: ${r.stdout}${r.stderr}`);
   if(cmd==='java')process.stdout.write(r.stdout);
 }
} finally {fs.rmSync(dir,{recursive:true,force:true});}

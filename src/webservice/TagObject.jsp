<%@ page language="java"
         contentType="application/json; charset=UTF-8"
         pageEncoding="UTF-8"
         trimDirectiveWhitespaces="true" %>
<%@ page import="com.Lomikel.Januser.JanusClient" %>
<%@ page import="com.Lomikel.Utils.Init" %>
<%@ page import="com.astrolabsoftware.FinkBrowser.Januser.Classifier" %>
<%@ page import="com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG" %>
<%@ page import="org.json.JSONObject" %>
<%@ page import="java.io.ByteArrayOutputStream" %>
<%@ page import="java.io.FileInputStream" %>
<%@ page import="java.io.InputStream" %>
<%@ page import="java.io.OutputStream" %>
<%@ page import="java.nio.file.Files" %>
<%@ page import="java.nio.file.Path" %>
<%@ page import="java.nio.file.attribute.PosixFilePermissions" %>
<%@ page import="java.net.URI" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Properties" %>
<%@ page import="java.util.concurrent.TimeUnit" %>
<%@ page import="java.util.concurrent.locks.ReentrantLock" %>
<%@ page import="java.util.regex.Pattern" %>

<%!
  // "Tag" action of the Graph Browser: register one existing <em>object</em> in the TAG OCol
  // "GraphBrowser" of the JanusGraph database of its survey (LSST -> CC, ZTF -> IJCLab).
  // The Gremlin servers behind the browser are read-only, so the write goes through this
  // server-side client, configured with the same writable properties as the cron jobs.
  private static final String TAG = "GraphBrowser";
  private static final Pattern OBJECT_ID = Pattern.compile("(?:ZTF[A-Za-z0-9_-]{1,80}|[0-9]{1,24})");
  // The writable graph configurations are packaged inside the WAR (WEB-INF/janus/*RW.properties, taken from
  // src/sys/janusgraph/conf/gremlin-server), so the WAR does not depend on any local file.
  private static final String BUNDLED_LSST = "/WEB-INF/janus/CCRW.properties";
  private static final String BUNDLED_ZTF  = "/WEB-INF/janus/IJCLabRW.properties";
  // One writer at a time; clients are opened lazily and reused (a JanusGraph is expensive to open).
  private static final ReentrantLock WRITE_LOCK = new ReentrantLock();
  private static final Map<String, FinkGremlinRecipiesG> RECIPES = new HashMap<String, FinkGremlinRecipiesG>();
  private static final Map<String, JanusClient> CLIENTS = new HashMap<String, JanusClient>();

  private static byte[] readLimited(InputStream stream, int max) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    byte[] block = new byte[512];
    int count;
    while ((count = stream.read(block)) != -1) {
      if (bytes.size() + count > max) throw new IllegalArgumentException("Payload too large");
      bytes.write(block, 0, count);
      }
    return bytes.toByteArray();
    }

  // Browsers always send Origin on cross-site POSTs: accept only the page's own origin.
  private static boolean sameOrigin(String origin, String hostHeader) {
    if (origin == null) return true; // non-browser client; the custom header below still applies
    try {
      URI uri = new URI(origin);
      if (uri.getHost() == null || hostHeader == null) return false;
      String authority = uri.getPort() < 0 ? uri.getHost() : uri.getHost() + ":" + uri.getPort();
      return authority.equalsIgnoreCase(hostHeader.trim());
      }
    catch (Exception e) {
      return false;
      }
    }

  // Optional override (-Dfink.tag.janus.LSST=/path, -Dfink.tag.janus.ZTF=/path); normally the bundled copy is used.
  private static Properties load(String survey, InputStream bundled) throws Exception {
    Properties p = new Properties();
    String configured = System.getProperty("fink.tag.janus." + survey);
    if (configured != null && !configured.trim().isEmpty()) {
      try (FileInputStream stream = new FileInputStream(configured.trim())) { p.load(stream); }
      }
    else {
      if (bundled == null) throw new IllegalStateException("Bundled graph configuration is missing");
      try (InputStream stream = bundled) { p.load(stream); }
      }
    // Refuse a configuration that is not an explicitly writable one.
    if (p.getProperty("storage.backend") == null ||
        "true".equalsIgnoreCase(p.getProperty("storage.read-only", "false").trim())) {
      throw new IllegalStateException("Graph configuration is not writable");
      }
    return p;
    }

  // JanusClient opens a properties file, so the configuration goes through a private (owner-only) temporary
  // file that exists only while the graph is being opened.
  private static FinkGremlinRecipiesG recipes(String survey, InputStream bundled) throws Exception {
    FinkGremlinRecipiesG cached = RECIPES.get(survey);
    if (cached != null) return cached;
    Properties p = load(survey, bundled);
    Path file = Files.createTempFile("fink-tag-" + survey + "-", ".properties",
                                     PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    try {
      try (OutputStream stream = Files.newOutputStream(file)) { p.store(stream, null); }
      Init.initWS("TagObjectWS");
      JanusClient client = new JanusClient(file.toString());
      FinkGremlinRecipiesG gr = new FinkGremlinRecipiesG(client);
      CLIENTS.put(survey, client);
      RECIPES.put(survey, gr);
      return gr;
      }
    finally {
      Files.deleteIfExists(file);
      }
    }

  // A failed or interrupted operation may leave a broken graph/transaction: drop and reopen next time.
  private static void discard(String survey) {
    RECIPES.remove(survey);
    JanusClient client = CLIENTS.remove(survey);
    if (client != null) {
      try { client.close(); } catch (Exception ignored) { }
      }
    }

  private static long members(FinkGremlinRecipiesG gr, String objectId) {
    Object n = gr.g().V().has("lbl", "OCol").has("classifier", "TAG").has("cls", TAG)
                    .out("deepcontains").has("lbl", "object").has("objectId", objectId).count().next();
    return ((Number)n).longValue();
    }
%>

<%
  response.setHeader("Cache-Control", "no-store");
  response.setHeader("X-Content-Type-Options", "nosniff");
  if (!"POST".equals(request.getMethod())) {
    response.setStatus(405);
    out.print("{\"error\":\"POST required\"}");
    return;
    }
  String contentType = request.getContentType();
  if (!sameOrigin(request.getHeader("Origin"), request.getHeader("Host"))) {
    response.setStatus(403);
    out.print("{\"error\":\"Cross-origin request refused\"}");
    return;
    }
  if (!"1".equals(request.getHeader("X-Fink-Tag")) || contentType == null ||
      !contentType.toLowerCase().startsWith("application/json")) {
    response.setStatus(400);
    out.print("{\"error\":\"Invalid tag request\"}");
    return;
    }
  String objectId;
  try {
    if (request.getContentLengthLong() > 512) throw new IllegalArgumentException("Request too large");
    try (InputStream stream = request.getInputStream()) {
      objectId = new JSONObject(new String(readLimited(stream, 512), StandardCharsets.UTF_8)).getString("objectId");
      }
    if (!OBJECT_ID.matcher(objectId).matches()) throw new IllegalArgumentException("Invalid object ID");
    }
  catch (Exception invalid) {
    response.setStatus(400);
    out.print("{\"error\":\"Invalid object request\"}");
    return;
    }
  String survey = objectId.startsWith("ZTF") ? "ZTF" : "LSST";
  boolean locked = false;
  try {
    locked = WRITE_LOCK.tryLock(15, TimeUnit.SECONDS);
    if (!locked) {
      response.setStatus(503);
      out.print("{\"error\":\"Tag service busy\"}");
      return;
      }
    FinkGremlinRecipiesG gr;
    long opened = System.currentTimeMillis();
    try {
      gr = recipes(survey, application.getResourceAsStream("ZTF".equals(survey) ? BUNDLED_ZTF : BUNDLED_LSST));
      }
    catch (Exception unavailable) {
      application.log("TagObject: writable " + survey + " graph unavailable", unavailable);
      discard(survey);
      response.setStatus(503);
      out.print(new JSONObject().put("error", "Writable " + survey + " graph is not available (see the server log)").toString());
      return;
      }
    application.log("TagObject: " + survey + " graph ready (" + (System.currentTimeMillis() - opened) + " ms)");
    try {
      long t0 = System.currentTimeMillis();
      boolean exists = gr.g().V().has("lbl", "object").has("objectId", objectId).hasNext();
      if (!exists) {
        gr.commit(); // end the read transaction
        response.setStatus(404);
        out.print(new JSONObject().put("error", "Object is not in the " + survey + " graph").toString());
        return;
        }
      application.log("TagObject: " + objectId + " found in " + survey + " graph (" + (System.currentTimeMillis() - t0) + " ms)");
      boolean already = members(gr, objectId) > 0;
      gr.commit();
      application.log("TagObject: " + objectId + " membership checked (" + (System.currentTimeMillis() - t0) + " ms), registering");
      gr.registerOCol(Classifier.instance("TAG", survey, ""), TAG, objectId, 1.0, "", "");
      application.log("TagObject: " + objectId + " registered (" + (System.currentTimeMillis() - t0) + " ms), verifying");
      long verified = members(gr, objectId);
      gr.commit();
      application.log("TagObject: " + objectId + " verified, members=" + verified + " (" + (System.currentTimeMillis() - t0) + " ms)");
      if (verified != 1) {
        response.setStatus(502);
        out.print(new JSONObject().put("error", "Tag was written but could not be verified").toString());
        return;
        }
      out.print(new JSONObject().put("ok", true).put("objectId", objectId).put("tag", TAG)
        .put("survey", survey).put("alreadyTagged", already).put("members", verified).toString());
      }
    catch (Exception failure) {
      application.log("TagObject: tagging " + objectId + " failed", failure);
      discard(survey);
      response.setStatus(502);
      out.print("{\"error\":\"Tagging failed; the tag may or may not have been written\"}");
      }
    }
  catch (InterruptedException interrupted) {
    Thread.currentThread().interrupt();
    response.setStatus(503);
    out.print("{\"error\":\"Tag service interrupted\"}");
    }
  finally {
    if (locked) WRITE_LOCK.unlock();
    }
%>

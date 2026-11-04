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
  private static final String DEFAULT_LSST = "/opt/janusgraph-1/conf/gremlin-server/CCRW.properties";
  private static final String DEFAULT_ZTF  = "/opt/janusgraph-1/conf/gremlin-server/IJCLabRW.properties";
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

  private static String propertiesFile(String survey) {
    String normal = "ZTF".equals(survey) ? DEFAULT_ZTF : DEFAULT_LSST;
    String configured = System.getProperty("fink.tag.janus." + survey);
    return configured == null || configured.trim().isEmpty() ? normal : configured.trim();
    }

  // Refuse a configuration that is not an explicitly writable one.
  private static void requireWritable(String file) throws Exception {
    Properties p = new Properties();
    try (FileInputStream stream = new FileInputStream(file)) {
      p.load(stream);
      }
    if (p.getProperty("storage.backend") == null ||
        "true".equalsIgnoreCase(p.getProperty("storage.read-only", "false").trim())) {
      throw new IllegalStateException("Graph configuration is not writable");
      }
    }

  private static FinkGremlinRecipiesG recipes(String survey) throws Exception {
    FinkGremlinRecipiesG cached = RECIPES.get(survey);
    if (cached != null) return cached;
    String file = propertiesFile(survey);
    requireWritable(file);
    Init.initWS("TagObjectWS");
    JanusClient client = new JanusClient(file);
    FinkGremlinRecipiesG gr = new FinkGremlinRecipiesG(client);
    CLIENTS.put(survey, client);
    RECIPES.put(survey, gr);
    return gr;
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
    try {
      gr = recipes(survey);
      }
    catch (Exception unavailable) {
      discard(survey);
      response.setStatus(503);
      out.print(new JSONObject().put("error", "Writable " + survey + " graph is not available").toString());
      return;
      }
    try {
      boolean exists = gr.g().V().has("lbl", "object").has("objectId", objectId).hasNext();
      if (!exists) {
        gr.commit(); // end the read transaction
        response.setStatus(404);
        out.print(new JSONObject().put("error", "Object is not in the " + survey + " graph").toString());
        return;
        }
      boolean already = members(gr, objectId) > 0;
      gr.commit();
      gr.registerOCol(Classifier.instance("TAG", survey, ""), TAG, objectId, 1.0, "", "");
      long verified = members(gr, objectId);
      gr.commit();
      if (verified != 1) {
        response.setStatus(502);
        out.print(new JSONObject().put("error", "Tag was written but could not be verified").toString());
        return;
        }
      out.print(new JSONObject().put("ok", true).put("objectId", objectId).put("tag", TAG)
        .put("survey", survey).put("alreadyTagged", already).put("members", verified).toString());
      }
    catch (Exception failure) {
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

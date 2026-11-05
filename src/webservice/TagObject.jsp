<%@ page language="java"
         contentType="application/json; charset=UTF-8"
         pageEncoding="UTF-8"
         trimDirectiveWhitespaces="true" %>
<%@ page import="com.Lomikel.Januser.JanusClient" %>
<%@ page import="com.Lomikel.Utils.Init" %>
<%@ page import="com.astrolabsoftware.FinkBrowser.Januser.Classifier" %>
<%@ page import="com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG" %>
<%@ page import="com.astrolabsoftware.FinkBrowser.Januser.TagClassifier" %>
<%@ page import="org.json.JSONArray" %>
<%@ page import="org.json.JSONObject" %>
<%@ page import="java.io.ByteArrayOutputStream" %>
<%@ page import="java.io.FileInputStream" %>
<%@ page import="java.io.BufferedReader" %>
<%@ page import="java.io.InputStream" %>
<%@ page import="java.io.InputStreamReader" %>
<%@ page import="java.io.OutputStream" %>
<%@ page import="java.nio.file.Files" %>
<%@ page import="java.nio.file.Path" %>
<%@ page import="java.nio.file.attribute.PosixFilePermissions" %>
<%@ page import="java.net.InetSocketAddress" %>
<%@ page import="java.net.Socket" %>
<%@ page import="java.net.URI" %>
<%@ page import="java.net.http.HttpClient" %>
<%@ page import="java.net.http.HttpRequest" %>
<%@ page import="java.net.http.HttpResponse" %>
<%@ page import="java.net.http.HttpTimeoutException" %>
<%@ page import="java.time.Duration" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Properties" %>
<%@ page import="java.util.concurrent.Callable" %>
<%@ page import="java.util.concurrent.ExecutorService" %>
<%@ page import="java.util.concurrent.Executors" %>
<%@ page import="java.util.concurrent.Future" %>
<%@ page import="java.util.concurrent.TimeUnit" %>
<%@ page import="java.util.concurrent.TimeoutException" %>
<%@ page import="java.util.concurrent.ExecutionException" %>
<%@ page import="java.util.concurrent.locks.ReentrantLock" %>
<%@ page import="java.util.regex.Pattern" %>

<%!
  // "Tag" action of the Graph Browser: tag one existing <em>object</em> in a TAG OCol of the JanusGraph
  // database of its survey (LSST -> CC, ZTF -> IJCLab) with
  //   Classifier.instance('TAG', survey, '').classify(gr, oid, tag, value, author, annotation, ref)
  // GET answers the configured tag names; POST writes. The Gremlin servers behind the browser are read-only,
  // so the write goes through this server-side client, configured with writable properties.
  // Tag names come from com/astrolabsoftware/FinkBrowser/Januser/TagNames.txt (in the Lomikel jar).
  // Routing (WEB-INF/TagRouting.properties, filled by "ant war" from the build site): each survey has one owner
  // site whose WAR writes it into its local graph (LSST -> CC, ZTF -> IJCLab). Any other WAR forwards the
  // request server-to-server to the owner's TagObject.jsp and relays the answer; a forwarded request is never
  // forwarded again. System properties fink.tag.site, fink.tag.owner.<survey>, fink.tag.url.<site> override it.
  private static final String TAG_NAMES = "TagNames.txt";
  private static final Pattern TAG_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
  private static final int MAX_BODY = 8192;
  private static final int MAX_AUTHOR = 200;
  private static final int MAX_ANNOTATION = 2000;
  private static final int MAX_REF = 1000;
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

  // Opening a graph whose HBase or the index server cannot be reached blocks for minutes: probe them first.
  private static void requireReachable(Properties p) throws Exception {
    String[][] endpoints = {
      {"HBase", p.getProperty("storage.hostname"), p.getProperty("storage.port", "2181")},
      {"Index server", p.getProperty("index.search.hostname"), null}};
    if (!"hbase".equalsIgnoreCase(String.valueOf(p.getProperty("storage.backend")).trim())) endpoints[0][1] = null;
    for (String[] endpoint : endpoints) {
      if (endpoint[1] == null || endpoint[1].trim().isEmpty()) continue;
      String host = endpoint[1].split(",")[0].trim();
      String port = endpoint[2];
      int colon = host.lastIndexOf(':');
      if (colon > 0) { port = host.substring(colon + 1); host = host.substring(0, colon); }
      if (port == null) port = "9200";
      try (Socket socket = new Socket()) {
        socket.connect(new InetSocketAddress(host, Integer.parseInt(port.trim())), 3000);
        }
      catch (Exception e) {
        throw new IllegalStateException(endpoint[0] + " " + host + ":" + port + " is not reachable from this server", e);
        }
      }
    }

  // Opening a graph can block for minutes inside the HBase client (e.g. region servers unreachable although
  // ZooKeeper answers). The open therefore runs on a daemon thread and the request waits only a bounded time;
  // a still-running open is reused by later requests instead of starting another one.
  private static final ExecutorService OPENER = Executors.newCachedThreadPool(new java.util.concurrent.ThreadFactory() {
    public Thread newThread(Runnable r) { Thread t = new Thread(r, "TagObject-open"); t.setDaemon(true); return t; }
    });
  private static final Map<String, Future<Object[]>> OPENING = new HashMap<String, Future<Object[]>>();

  private static long openTimeoutSeconds() {
    try { return Long.parseLong(System.getProperty("fink.tag.open.timeout", "20")); }
    catch (NumberFormatException e) { return 20; }
    }

  // JanusClient opens a properties file, so the configuration goes through a private (owner-only) temporary
  // file that exists only while the graph is being opened.
  private static Object[] open(final String survey, final Properties p) throws Exception {
    Path file = Files.createTempFile("fink-tag-" + survey + "-", ".properties",
                                     PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    try {
      try (OutputStream stream = Files.newOutputStream(file)) { p.store(stream, null); }
      Init.initWS("TagObjectWS");
      JanusClient client = new JanusClient(file.toString());
      return new Object[] {client, new FinkGremlinRecipiesG(client)};
      }
    finally {
      Files.deleteIfExists(file);
      }
    }

  private static FinkGremlinRecipiesG recipes(final String survey, InputStream bundled) throws Exception {
    FinkGremlinRecipiesG cached = RECIPES.get(survey);
    if (cached != null) return cached;
    Future<Object[]> opening = OPENING.get(survey);
    if (opening == null) {
      final Properties p = load(survey, bundled);
      requireReachable(p);
      opening = OPENER.submit(new Callable<Object[]>() { public Object[] call() throws Exception { return open(survey, p); } });
      OPENING.put(survey, opening);
      }
    Object[] opened;
    try {
      opened = opening.get(openTimeoutSeconds(), TimeUnit.SECONDS);
      }
    catch (TimeoutException slow) {
      throw new IllegalStateException("The " + survey + " graph did not open within " + openTimeoutSeconds() +
                                      " s, HBase is probably not reachable from this server", slow);
      }
    catch (ExecutionException failed) {
      OPENING.remove(survey);
      throw new IllegalStateException("The " + survey + " graph could not be opened", failed.getCause());
      }
    OPENING.remove(survey);
    CLIENTS.put(survey, (JanusClient)opened[0]);
    RECIPES.put(survey, (FinkGremlinRecipiesG)opened[1]);
    return (FinkGremlinRecipiesG)opened[1];
    }

  // A failed or interrupted operation may leave a broken graph/transaction: drop and reopen next time.
  private static void discard(String survey) {
    RECIPES.remove(survey);
    JanusClient client = CLIENTS.remove(survey);
    if (client != null) {
      try { client.close(); } catch (Exception ignored) { }
      }
    }

  // Configured tag names, in file order; invalid lines are ignored.
  private static List<String> tagNames() throws Exception {
    List<String> names = new ArrayList<String>();
    InputStream stream = TagClassifier.class.getResourceAsStream(TAG_NAMES);
    if (stream == null) throw new IllegalStateException("Tag name configuration " + TAG_NAMES + " is missing");
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        int hash = line.indexOf('#');
        String name = (hash < 0 ? line : line.substring(0, hash)).trim();
        if (TAG_NAME.matcher(name).matches() && !names.contains(name)) names.add(name);
        }
      }
    return names;
    }

  // Optional text field: null when absent or blank (the attribute is then not set), single-line, bounded.
  private static String text(JSONObject body, String key, int max) {
    if (!body.has(key) || body.isNull(key)) return null;
    Object raw = body.get(key);
    if (!(raw instanceof String)) throw new IllegalArgumentException("Invalid " + key);
    String value = ((String)raw).replaceAll("[\\p{Cntrl}\\u2028\\u2029]", " ").trim();
    if (value.length() > max) throw new IllegalArgumentException(key + " is too long");
    return value.isEmpty() ? null : value;
    }

  private static final String ROUTING = "/WEB-INF/TagRouting.properties";
  private static final String FORWARDED = "X-Fink-Tag-Forwarded";
  private static final Pattern SITE = Pattern.compile("[A-Za-z0-9_.-]{1,40}");
  private static final HttpClient FORWARDER = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                                                        .followRedirects(HttpClient.Redirect.NEVER).build();

  private static Properties routing(InputStream bundled) {
    Properties p = new Properties();
    if (bundled != null) {
      try (InputStream stream = bundled) { p.load(stream); }
      catch (Exception ignored) { }
      }
    for (String key : new String[] {"site", "owner.LSST", "owner.ZTF", "url.CC", "url.IJCLab"}) {
      String configured = System.getProperty("fink.tag." + key);
      if (configured != null && !configured.trim().isEmpty()) p.setProperty(key, configured.trim());
      }
    for (String name : System.getProperties().stringPropertyNames()) {
      if (name.startsWith("fink.tag.url.")) p.setProperty(name.substring("fink.tag.".length()), System.getProperty(name).trim());
      }
    return p;
    }

  // This WAR's site, or "" when unset/unusual (then it writes no survey itself).
  private static String site(Properties p) {
    String site = p.getProperty("site", "").trim();
    return SITE.matcher(site).matches() ? site : "";
    }

  // The owner's TagObject.jsp, only as a plain http(s) URL.
  private static URI ownerUrl(Properties p, String owner) {
    String url = p.getProperty("url." + owner, "").trim();
    try {
      URI uri = new URI(url);
      if (("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null &&
          uri.getUserInfo() == null && uri.getPath() != null && uri.getPath().endsWith("/TagObject.jsp")) return uri;
      }
    catch (Exception ignored) { }
    return null;
    }

  private static long forwardTimeoutSeconds() {
    try { return Long.parseLong(System.getProperty("fink.tag.forward.timeout", "80")); }
    catch (NumberFormatException e) { return 80; }
    }

  // Number of registrations (deepcontains edges) of the object in the TAG OCol.
  private static long entries(FinkGremlinRecipiesG gr, String tag, String objectId) {
    Object n = gr.g().V().has("lbl", "OCol").has("classifier", "TAG").has("flavor", "").has("cls", tag)
                    .outE("deepcontains").where(org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.inV()
                    .has("lbl", "object").has("objectId", objectId)).count().next();
    return ((Number)n).longValue();
    }

  // TagClassifier.classify only writes into an existing OCol. Create a configured one on its first use,
  // exactly as FinkGremlinRecipies.registerOCol looks it up (TAG classifier, survey ANY, empty flavor).
  private static boolean ensureOCol(FinkGremlinRecipiesG gr, String tag) {
    if (gr.g().V().has("lbl", "OCol").has("survey", "ANY").has("classifier", "TAG").has("flavor", "")
                  .has("cls", tag).hasNext()) return false;
    gr.g().addV("OCol").property("lbl", "OCol").property("survey", "ANY").property("classifier", "TAG")
                       .property("flavor", "").property("cls", tag).iterate();
    gr.commit();
    return true;
    }
%>

<%
  response.setHeader("Cache-Control", "no-store");
  response.setHeader("X-Content-Type-Options", "nosniff");
  if ("GET".equals(request.getMethod())) {
    try {
      out.print(new JSONObject().put("tags", new JSONArray(tagNames())).toString());
      }
    catch (Exception missing) {
      application.log("TagObject: tag names unavailable", missing);
      response.setStatus(503);
      out.print("{\"error\":\"Tag names are not available\"}");
      }
    return;
    }
  if (!"POST".equals(request.getMethod())) {
    response.setStatus(405);
    out.print("{\"error\":\"GET or POST required\"}");
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
  String objectId, tag, author, annotation, ref;
  double value;
  try {
    if (request.getContentLengthLong() > MAX_BODY) throw new IllegalArgumentException("Request too large");
    JSONObject body;
    try (InputStream stream = request.getInputStream()) {
      body = new JSONObject(new String(readLimited(stream, MAX_BODY), StandardCharsets.UTF_8));
      }
    objectId = body.getString("objectId");
    if (!OBJECT_ID.matcher(objectId).matches()) throw new IllegalArgumentException("Invalid object ID");
    tag = body.optString("tag", "");
    if (!TAG_NAME.matcher(tag).matches() || !tagNames().contains(tag)) throw new IllegalArgumentException("Unknown tag name");
    Object rawValue = body.opt("value");
    if (!(rawValue instanceof Number)) throw new IllegalArgumentException("Tag value must be a number");
    value = ((Number)rawValue).doubleValue();
    if (!Double.isFinite(value) || value < 0.0) throw new IllegalArgumentException("Tag value must be a finite number >= 0");
    author     = text(body, "author",     MAX_AUTHOR);
    annotation = text(body, "annotation", MAX_ANNOTATION);
    ref        = text(body, "ref",        MAX_REF);
    }
  catch (Exception invalid) {
    String reason = invalid instanceof IllegalArgumentException && invalid.getMessage() != null &&
                    invalid.getMessage().matches("[A-Za-z0-9 >=.,_-]{1,80}") ? invalid.getMessage() : "Invalid tag request";
    response.setStatus(400);
    out.print(new JSONObject().put("error", reason).toString());
    return;
    }
  String survey = objectId.startsWith("ZTF") ? "ZTF" : "LSST";
  Properties route = routing(application.getResourceAsStream(ROUTING));
  String here = site(route);
  String owner = route.getProperty("owner." + survey, "").trim();
  if (!SITE.matcher(owner).matches()) {
    response.setStatus(503);
    out.print(new JSONObject().put("error", "No " + survey + " tagging service is configured").toString());
    return;
    }
  if (!owner.equals(here)) {
    if (request.getHeader(FORWARDED) != null) {
      // Never forward twice: a misconfigured owner must not create a loop.
      response.setStatus(421);
      out.print(new JSONObject().put("error", "This server (" + (here.isEmpty() ? "no site" : here) + ") does not write " + survey + " tags").toString());
      return;
      }
    URI target = ownerUrl(route, owner);
    if (target == null) {
      response.setStatus(503);
      out.print(new JSONObject().put("error", "No URL is configured for the " + survey + " tagging service at " + owner).toString());
      return;
      }
    JSONObject forward = new JSONObject().put("objectId", objectId).put("tag", tag).put("value", value);
    if (author != null) forward.put("author", author);
    if (annotation != null) forward.put("annotation", annotation);
    if (ref != null) forward.put("ref", ref);
    HttpResponse<byte[]> answer;
    try {
      answer = FORWARDER.send(HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(forwardTimeoutSeconds()))
                                         .header("Content-Type", "application/json").header("Accept", "application/json")
                                         .header("X-Fink-Tag", "1").header(FORWARDED, here.isEmpty() ? "unknown" : here)
                                         .POST(HttpRequest.BodyPublishers.ofString(forward.toString(), StandardCharsets.UTF_8)).build(),
                              HttpResponse.BodyHandlers.ofByteArray());
      }
    catch (HttpTimeoutException slow) {
      application.log("TagObject: " + survey + " tagging service at " + owner + " did not answer in time", slow);
      response.setStatus(504);
      out.print(new JSONObject().put("error", "The " + survey + " tagging service at " + owner +
                                     " did not answer in time").toString());
      return;
      }
    catch (Exception unreachable) {
      application.log("TagObject: " + survey + " tagging service at " + owner + " is not reachable", unreachable);
      response.setStatus(503);
      out.print(new JSONObject().put("error", "The " + survey + " tagging service at " + owner + " (" + target.getHost() +
                                     ") is not reachable from this server").toString());
      return;
      }
    JSONObject relayed;
    try {
      if (answer.body().length > 65536) throw new IllegalArgumentException("Answer too large");
      relayed = new JSONObject(new String(answer.body(), StandardCharsets.UTF_8));
      }
    catch (Exception garbled) {
      response.setStatus(502);
      out.print(new JSONObject().put("error", "Unexpected answer from the " + survey + " tagging service at " + owner).toString());
      return;
      }
    int status = answer.statusCode();
    response.setStatus(status >= 200 && status < 600 ? status : 502);
    if (!relayed.has("site") && status == 200) relayed.put("site", owner);
    out.print(relayed.toString());
    return;
    }
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
      String why = (String.valueOf(unavailable.getMessage()).contains("is not reachable from this server") ||
                    String.valueOf(unavailable.getMessage()).contains("did not open within")) ?
                   ": " + unavailable.getMessage().replaceAll("[^A-Za-z0-9 .:_-]", "") : " (see the server log)";
      out.print(new JSONObject().put("error", "Writable " + survey + " graph is not available" + why).toString());
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
      boolean created = ensureOCol(gr, tag);
      long before = entries(gr, tag, objectId);
      gr.commit();
      application.log("TagObject: " + objectId + " has " + before + " " + tag + " entries" + (created ? " (OCol created)" : "") +
                      " (" + (System.currentTimeMillis() - t0) + " ms), tagging");
      ((TagClassifier)Classifier.instance("TAG", survey, "")).classify(gr, objectId, tag, value, author, annotation, ref);
      application.log("TagObject: " + objectId + " tagged (" + (System.currentTimeMillis() - t0) + " ms), verifying");
      long after = entries(gr, tag, objectId);
      gr.commit();
      application.log("TagObject: " + objectId + " verified, entries=" + after + " (" + (System.currentTimeMillis() - t0) + " ms)");
      if (after != before + 1) {
        response.setStatus(502);
        out.print(new JSONObject().put("error", "Tag could not be verified").toString());
        return;
        }
      out.print(new JSONObject().put("ok", true).put("objectId", objectId).put("tag", tag).put("survey", survey)
        .put("value", value).put("entries", after).put("ocolCreated", created).put("site", here).toString());
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

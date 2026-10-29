<%@ page language="java"
         contentType="application/json; charset=UTF-8"
         pageEncoding="UTF-8"
         trimDirectiveWhitespaces="true" %>
<%@ page import="org.json.JSONArray" %>
<%@ page import="org.json.JSONObject" %>
<%@ page import="java.io.ByteArrayOutputStream" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.io.InputStream" %>
<%@ page import="java.io.OutputStream" %>
<%@ page import="java.net.HttpURLConnection" %>
<%@ page import="java.net.URL" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collections,java.util.Comparator" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="java.util.Set" %>
<%@ page import="java.util.concurrent.Semaphore" %>

<%!
  // Fixed read-only Elasticsearch discovery and HTTPS observation-level source.
  // ES's separately deduplicated mjd[] and location[] MUST NOT be paired by index.
  private static final String ES_BASE = "http://134.158.243.139:24499";
  private static final String API_BASE = "https://api.lsst.fink-portal.org";
  private static final String SS_MJD_SEARCH = "/ss_mjd/_search";
  private static final String SS_MJD_MGET = "/ss_mjd/_mget";
  private static final String SS_RADEC_MGET = "/ss_radec/_mget";
  private static final String COLUMNS = "r:diaSourceId,r:ssObjectId,r:midpointMjdTai,r:ra,r:dec";

  private static final int MAX_RESPONSE_BYTES = 4000000;
  private static final int MAX_SOURCES = 4000;
  private static final Semaphore HISTORY_SLOTS = new Semaphore(6, true);
  private static String fixtureBase(String property, String normal) {
    String fixture = System.getProperty(property);
    if (fixture == null) return normal;
    if (!fixture.matches("http://(?:127\\.0\\.0\\.1|localhost):[0-9]{1,5}"))
      throw new IllegalArgumentException("Fixture URL must be loopback");
    return fixture;
    }

  private static byte[] bounded(InputStream stream) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    byte[] chunk = new byte[4096];
    int n;
    while ((n = stream.read(chunk)) != -1) {
      if (bytes.size() + n > MAX_RESPONSE_BYTES) throw new IOException("Upstream response too large");
      bytes.write(chunk, 0, n);
      }
    return bytes.toByteArray();
    }

  private static String postText(String base, String path, JSONObject payload, JSONArray requests) throws Exception {
    byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
    HttpURLConnection connection = (HttpURLConnection)new URL(base + path).openConnection();
    connection.setInstanceFollowRedirects(false);
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(15000);
    connection.setReadTimeout(path.startsWith("/api/") ? 90000 : 30000);
    connection.setDoOutput(true);
    connection.setRequestProperty("Accept", "application/json");
    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
    connection.setFixedLengthStreamingMode(body.length);
    try {
      try (OutputStream output = connection.getOutputStream()) { output.write(body); }
      requests.put(new JSONObject().put("path", path).put("body", new String(body, StandardCharsets.UTF_8)));
      int status = connection.getResponseCode();
      if (status < 200 || status >= 300) throw new IOException("Upstream HTTP " + status);
      try (InputStream stream = connection.getInputStream()) {
        return new String(bounded(stream), StandardCharsets.UTF_8);
        }
      }
    finally { connection.disconnect(); }
    }

  private static JSONObject postJson(String path, JSONObject payload, JSONArray requests) throws Exception {
    return new JSONObject(postText(fixtureBase("fink.sstrajectory.esUrl", ES_BASE), path, payload, requests));
    }

  private static JSONArray asArray(Object value) {
    if (value instanceof JSONArray) return (JSONArray)value;
    JSONArray result = new JSONArray();
    if (value != null && value != JSONObject.NULL) result.put(value);
    return result;
    }

  private static String exactId(Object value) {
    if (value == null || value == JSONObject.NULL) throw new IllegalArgumentException("Missing source ID");
    String id = String.valueOf(value);
    if (!id.matches("\\d{1,64}")) throw new IllegalArgumentException("Invalid source ID");
    return id;
    }

  private static boolean coversEsHistory(JSONArray mjds, JSONArray locations, JSONArray sources) {
    if (locations.length() == 0 || sources.length() == 0) return false;
    // Independently deduplicated ES arrays cannot prove source-row count.
    // Bound the comparison work as well as response bytes; on uncertainty the
    // client refuses to present a track as a verified history.
    if (mjds.length() > MAX_SOURCES || locations.length() > MAX_SOURCES ||
        (long)(mjds.length() + locations.length()) * sources.length() > 1000000L) return false;
    for (int i = 0; i < mjds.length(); i++) {
      double mjd = mjds.optDouble(i, Double.NaN);
      if (!Double.isFinite(mjd)) return false;
      boolean found = false;
      for (int j = 0; j < sources.length(); j++) {
        if (Math.abs(sources.getJSONObject(j).getDouble("mjd") - mjd) <= 0.00001) {
          found = true;
          break;
          }
        }
      if (!found) return false;
      }
    for (int i = 0; i < locations.length(); i++) {
      JSONObject location = locations.optJSONObject(i);
      if (location == null) return false;
      double ra = (location.optDouble("lon", Double.NaN) + 180.0 + 360.0) % 360.0;
      double dec = location.optDouble("lat", Double.NaN);
      if (!Double.isFinite(ra) || !Double.isFinite(dec)) return false;
      boolean found = false;
      for (int j = 0; j < sources.length(); j++) {
        JSONObject source = sources.getJSONObject(j);
        double difference = Math.abs(source.getDouble("ra") % 360.0 - ra);
        if (Math.min(difference, 360.0 - difference) <= 0.00001 &&
            Math.abs(source.getDouble("dec") - dec) <= 0.00001) {
          found = true;
          break;
          }
        }
      if (!found) return false;
      }
    return true;
    }
%>
<%
  response.setHeader("Cache-Control", "no-store");
  JSONArray esRequests = new JSONArray();
  JSONArray finkRequests = new JSONArray();
  if ("1".equals(request.getParameter("list"))) {
    int limit = 10;
    try { limit = Integer.parseInt(request.getParameter("n")); }
    catch (Exception ignored) { }
    limit = Math.min(100, Math.max(1, limit));
    int scanLimit = Math.min(1000, Math.max(100, limit * 10));
    try {
      JSONObject query = new JSONObject()
        .put("size", scanLimit)
        .put("_source", new JSONArray().put("mjd"))
        .put("sort", new JSONArray().put(new JSONObject().put(
          "mjd", new JSONObject().put("order", "desc").put("mode", "max"))));
      JSONArray hits = postJson(SS_MJD_SEARCH, query, esRequests).getJSONObject("hits").getJSONArray("hits");
      JSONArray ids = new JSONArray();
      for (int i = 0; i < hits.length() && ids.length() < limit; i++) {
        JSONObject hit = hits.getJSONObject(i);
        JSONArray times = asArray(hit.getJSONObject("_source").opt("mjd"));
        if (times.length() < 2) continue;
        String id = hit.getString("_id");
        if (id.matches("\\d{1,64}")) ids.put(id);
        }
      out.print(new JSONObject().put("ids", ids).put("esRequests", esRequests)
        .put("finkRequests", finkRequests).toString());
      }
    catch (Exception error) {
      response.setStatus(502);
      out.print(new JSONObject().put("error", "Cannot list SS objects")
        .put("esRequests", esRequests).put("finkRequests", finkRequests).toString());
      }
    return;
    }

  String id = request.getParameter("id");
  if (id == null || !id.matches("\\d{1,64}")) {
    response.setStatus(400);
    out.print(new JSONObject().put("error", "Invalid SS object ID")
      .put("esRequests", esRequests).put("finkRequests", finkRequests).toString());
    return;
    }
  if (!HISTORY_SLOTS.tryAcquire()) {
    response.setStatus(503);
    out.print(new JSONObject().put("error", "SS trajectory service busy")
      .put("esRequests", esRequests).put("finkRequests", finkRequests).toString());
    return;
    }
  try {
    JSONObject ids = new JSONObject().put("ids", new JSONArray().put(id));
    JSONObject mjdDocument = postJson(SS_MJD_MGET, ids, esRequests).getJSONArray("docs").getJSONObject(0);
    JSONObject radecDocument = postJson(SS_RADEC_MGET, ids, esRequests).getJSONArray("docs").getJSONObject(0);
    if (!radecDocument.optBoolean("found") ||
        !id.equals(mjdDocument.optString("_id")) || !id.equals(radecDocument.optString("_id"))) {
      response.setStatus(404);
      out.print(new JSONObject().put("error", "SS object not found in position index")
        .put("finkRequests", finkRequests).put("esRequests", esRequests).toString());
      return;
      }
    boolean mjdAvailable = mjdDocument.optBoolean("found");
    JSONArray mjds = mjdAvailable ? asArray(mjdDocument.getJSONObject("_source").opt("mjd")) : new JSONArray();
    JSONArray locations = asArray(radecDocument.getJSONObject("_source").opt("location"));

    JSONArray resolver = new JSONArray(postText(fixtureBase("fink.sstrajectory.apiUrl", API_BASE), "/api/v1/resolver",
      new JSONObject().put("resolver", "ssodnet").put("name_or_id", id)
        .put("reverse", true).put("nmax", 10).put("output-format", "json"), finkRequests));
    if (resolver.length() > MAX_SOURCES) throw new IllegalArgumentException("Too many resolver rows");
    JSONObject exactMatch = null;
    for (int i = 0; i < resolver.length(); i++) {
      JSONObject row = resolver.getJSONObject(i);
      if (!id.equals(String.valueOf(row.opt("r:ssObjectId")))) continue;
      if (exactMatch != null) throw new IllegalArgumentException("Ambiguous SS resolver result");
      exactMatch = row;
      }
    if (exactMatch == null) throw new IllegalArgumentException("SS object cannot be resolved");
    String designation = exactMatch.optString("r:packed_primary_provisional_designation", "").trim();
    if (designation.isEmpty()) designation = exactMatch.optString("r:unpacked_primary_provisional_designation", "").trim();
    if (designation.isEmpty()) throw new IllegalArgumentException("SS designation unavailable");

    JSONArray rawSources = new JSONArray(postText(fixtureBase("fink.sstrajectory.apiUrl", API_BASE), "/api/v1/sso",
      new JSONObject().put("n_or_d", designation).put("columns", COLUMNS)
        .put("output-format", "json"), finkRequests));
    if (rawSources.length() == 0) throw new IllegalArgumentException("SS source history unavailable");
    if (rawSources.length() > MAX_SOURCES) throw new IllegalArgumentException("Too many SS sources");
    ArrayList<JSONObject> ordered = new ArrayList<JSONObject>();
    Set<String> seenSources = new HashSet<String>();
    for (int i = 0; i < rawSources.length(); i++) {
      JSONObject row = rawSources.getJSONObject(i);
      if (!id.equals(exactId(row.opt("r:ssObjectId")))) throw new IllegalArgumentException("Unexpected SS object");
      String sourceId = exactId(row.opt("r:diaSourceId"));
      if (!seenSources.add(sourceId)) throw new IllegalArgumentException("Duplicate SS source ID");
      double mjd = row.getDouble("r:midpointMjdTai");
      double ra = row.getDouble("r:ra");
      double dec = row.getDouble("r:dec");
      if (!Double.isFinite(mjd) || mjd <= 0 || !Double.isFinite(ra) || ra < 0 || ra > 360 ||
          !Double.isFinite(dec) || dec < -90 || dec > 90) {
        throw new IllegalArgumentException("Invalid SS source coordinates or MJD");
        }
      ordered.add(new JSONObject().put("sourceId", sourceId)
        .put("mjd", mjd).put("ra", ra).put("dec", dec));
      }
    Collections.sort(ordered, new Comparator<JSONObject>() {
      public int compare(JSONObject a, JSONObject b) {
        int time = Double.compare(a.getDouble("mjd"), b.getDouble("mjd"));
        return time != 0 ? time : a.getString("sourceId").compareTo(b.getString("sourceId"));
      }
    });
    JSONArray sources = new JSONArray(ordered);
    out.print(new JSONObject().put("objectId", id).put("sources", sources)
      .put("esMjdCount", mjds.length()).put("esPositionCount", locations.length())
      .put("esMjdAvailable", mjdAvailable)
      .put("esCoverageComplete", coversEsHistory(mjds, locations, sources))
      .put("finkRequests", finkRequests).put("esRequests", esRequests).toString());
    }
  catch (Exception error) {
    response.setStatus(502);
    out.print(new JSONObject().put("error", "Cannot load SS object history")
      .put("finkRequests", finkRequests).put("esRequests", esRequests).toString());
    }
  finally { HISTORY_SLOTS.release(); }
%>

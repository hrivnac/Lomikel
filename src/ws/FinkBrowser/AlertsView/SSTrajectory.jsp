<%@ page language="java"
         contentType="application/json; charset=UTF-8"
         pageEncoding="UTF-8"
         trimDirectiveWhitespaces="true" %>
<%@ page import="org.json.JSONArray" %>
<%@ page import="org.json.JSONObject" %>
<%@ page import="java.io.BufferedReader" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.io.InputStream" %>
<%@ page import="java.io.InputStreamReader" %>
<%@ page import="java.io.OutputStream" %>
<%@ page import="java.net.HttpURLConnection" %>
<%@ page import="java.net.URL" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="java.util.Set" %>

<%!
  // Fixed read-only Elasticsearch discovery and HTTPS observation-level source.
  // ES's separately deduplicated mjd[] and location[] MUST NOT be paired by index.
  private static final String ES_BASE = "http://134.158.243.139:24499";
  private static final String API_BASE = "https://api.lsst.fink-portal.org";
  private static final String SS_MJD_SEARCH = "/ss_mjd/_search";
  private static final String SS_MJD_MGET = "/ss_mjd/_mget";
  private static final String SS_RADEC_MGET = "/ss_radec/_mget";
  private static final String COLUMNS = "r:diaSourceId,r:ssObjectId,r:midpointMjdTai,r:ra,r:dec";

  private static String postText(String base, String path, JSONObject payload) throws Exception {
    HttpURLConnection connection = (HttpURLConnection)new URL(base + path).openConnection();
    connection.setInstanceFollowRedirects(false);
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(15000);
    connection.setReadTimeout(base.equals(API_BASE) ? 90000 : 30000);
    connection.setDoOutput(true);
    connection.setRequestProperty("Accept", "application/json");
    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
    byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
    connection.setFixedLengthStreamingMode(body.length);
    try {
      try (OutputStream output = connection.getOutputStream()) {
        output.write(body);
        }
      int status = connection.getResponseCode();
      InputStream stream = status >= 200 && status < 300
        ? connection.getInputStream() : connection.getErrorStream();
      StringBuilder responseBody = new StringBuilder();
      if (stream != null) {
        try (BufferedReader reader = new BufferedReader(
               new InputStreamReader(stream, StandardCharsets.UTF_8))) {
          String line;
          while ((line = reader.readLine()) != null) responseBody.append(line);
          }
        }
      if (status < 200 || status >= 300) throw new IOException("Upstream HTTP " + status);
      return responseBody.toString();
      }
    finally {
      connection.disconnect();
      }
    }

  private static JSONObject postJson(String path, JSONObject payload) throws Exception {
    return new JSONObject(postText(ES_BASE, path, payload));
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
    if (mjds.length() == 0 || locations.length() == 0 || sources.length() == 0) return false;
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
      JSONArray hits = postJson(SS_MJD_SEARCH, query).getJSONObject("hits").getJSONArray("hits");
      JSONArray ids = new JSONArray();
      for (int i = 0; i < hits.length() && ids.length() < limit; i++) {
        JSONObject hit = hits.getJSONObject(i);
        JSONArray times = asArray(hit.getJSONObject("_source").opt("mjd"));
        if (times.length() < 2) continue;
        String id = hit.getString("_id");
        if (id.matches("\\d{1,64}")) ids.put(id);
        }
      out.print(new JSONObject().put("ids", ids).toString());
      }
    catch (Exception error) {
      response.setStatus(502);
      out.print(new JSONObject().put("error", "Cannot list SS objects").toString());
      }
    return;
    }

  String id = request.getParameter("id");
  if (id == null || !id.matches("\\d{1,64}")) {
    response.setStatus(400);
    out.print(new JSONObject().put("error", "Invalid SS object ID").toString());
    return;
    }
  try {
    JSONObject ids = new JSONObject().put("ids", new JSONArray().put(id));
    JSONObject mjdDocument = postJson(SS_MJD_MGET, ids).getJSONArray("docs").getJSONObject(0);
    JSONObject radecDocument = postJson(SS_RADEC_MGET, ids).getJSONArray("docs").getJSONObject(0);
    if (!mjdDocument.optBoolean("found") || !radecDocument.optBoolean("found") ||
        !id.equals(mjdDocument.optString("_id")) || !id.equals(radecDocument.optString("_id"))) {
      response.setStatus(404);
      out.print(new JSONObject().put("error", "SS object not found in both indexes").toString());
      return;
      }
    JSONArray mjds = asArray(mjdDocument.getJSONObject("_source").opt("mjd"));
    JSONArray locations = asArray(radecDocument.getJSONObject("_source").opt("location"));

    JSONArray resolver = new JSONArray(postText(API_BASE, "/api/v1/resolver",
      new JSONObject().put("resolver", "ssodnet").put("name_or_id", id)
        .put("reverse", true).put("nmax", 10).put("output-format", "json")));
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

    JSONArray rawSources = new JSONArray(postText(API_BASE, "/api/v1/sso",
      new JSONObject().put("n_or_d", designation).put("columns", COLUMNS)
        .put("output-format", "json")));
    JSONArray sources = new JSONArray();
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
      sources.put(new JSONObject().put("sourceId", sourceId)
        .put("mjd", mjd).put("ra", ra).put("dec", dec));
      }
    out.print(new JSONObject().put("objectId", id).put("sources", sources)
      .put("esMjdCount", mjds.length()).put("esPositionCount", locations.length())
      .put("esCoverageComplete", coversEsHistory(mjds, locations, sources)).toString());
    }
  catch (Exception error) {
    response.setStatus(502);
    out.print(new JSONObject().put("error", "Cannot load SS object history").toString());
    }
%>

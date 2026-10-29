<%@ page language="java" contentType="application/json; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ page import="java.net.HttpURLConnection,java.net.URL" %>
<%@ page import="java.io.ByteArrayOutputStream,java.io.InputStream,java.io.OutputStream,java.io.IOException" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.HashSet,java.util.Set" %>
<%@ page import="org.json.JSONObject,org.json.JSONArray,org.json.JSONException" %>
<%!
  private static final String ES_BASE = "http://134.158.243.139:24499";
  private static final String API_BASE = "https://api.lsst.fink-portal.org";
  private static final String SSO_COLUMNS = "r:diaSourceId,r:ssObjectId,r:midpointMjdTai";
  private static final int MAX_SS_SOURCES = 4000;
  private static byte[] readLimited(InputStream input, int max) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    byte[] block = new byte[4096];
    int count;
    while ((count = input.read(block)) != -1) {
      if (bytes.size() + count > max) throw new IllegalArgumentException("Payload too large");
      bytes.write(block, 0, count);
    }
    return bytes.toByteArray();
  }
  private static String upstreamBase(String property, String normal) {
    String fixture = System.getProperty(property);
    if (fixture == null) return normal;
    if (!fixture.matches("http://(?:127\\.0\\.0\\.1|localhost):[0-9]{1,5}"))
      throw new IllegalArgumentException("Fixture must be loopback");
    return fixture;
  }
  private static String post(String base, String path, JSONObject data, int max,
                             JSONArray requests) throws Exception {
    byte[] body = data.toString().getBytes(StandardCharsets.UTF_8);
    HttpURLConnection connection = (HttpURLConnection)new URL(base + path).openConnection();
    connection.setInstanceFollowRedirects(false);
    connection.setConnectTimeout(8000);
    connection.setReadTimeout(path.equals("/api/v1/sso") ? 90000 : 15000);
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
    connection.setRequestProperty("Accept", "application/json");
    connection.setFixedLengthStreamingMode(body.length);
    try {
      try (OutputStream output = connection.getOutputStream()) { output.write(body); }
      requests.put(new JSONObject().put("path", path)
        .put("body", new String(body, StandardCharsets.UTF_8)));
      if (connection.getResponseCode() != 200) throw new IllegalStateException("Lookup failed");
      try (InputStream input = connection.getInputStream()) {
        try { return new String(readLimited(input, max), StandardCharsets.UTF_8); }
        catch (IllegalArgumentException oversized) { throw new IOException("Upstream response too large", oversized); }
      }
    } finally { connection.disconnect(); }
  }
  private static String sourceId(Object raw) {
    if (raw == null || raw == JSONObject.NULL) throw new IllegalStateException("Missing source ID");
    String id = String.valueOf(raw);
    if (!id.matches("[0-9]{1,24}")) throw new IllegalStateException("Invalid source ID");
    return id;
  }
  private static String latestSS(String id, String api, JSONArray requests) throws Exception {
    JSONObject resolverBody = new JSONObject().put("resolver", "ssodnet")
      .put("name_or_id", id).put("reverse", true).put("nmax", 10).put("output-format", "json");
    JSONArray resolved = new JSONArray(post(api, "/api/v1/resolver", resolverBody, 131072, requests));
    if (resolved.length() > 10) throw new IllegalStateException("Too many resolver rows");
    JSONObject match = null;
    for (int i = 0; i < resolved.length(); i++) {
      JSONObject row = resolved.getJSONObject(i);
      if (!id.equals(String.valueOf(row.opt("r:ssObjectId")))) continue;
      if (match != null) throw new IllegalStateException("Ambiguous SS object");
      match = row;
    }
    if (match == null) throw new IllegalStateException("SS object not resolved");
    String designation = match.optString("r:packed_primary_provisional_designation", "").trim();
    if (designation.isEmpty()) designation = match.optString("r:unpacked_primary_provisional_designation", "").trim();
    if (designation.isEmpty() || designation.length() > 128)
      throw new IllegalStateException("SS designation unavailable");
    JSONObject ssoBody = new JSONObject().put("n_or_d", designation)
      .put("columns", SSO_COLUMNS).put("output-format", "json");
    JSONArray sources = new JSONArray(post(api, "/api/v1/sso", ssoBody, 4194304, requests));
    if (sources.length() == 0 || sources.length() > MAX_SS_SOURCES)
      throw new IllegalStateException("SS source history unavailable or too large");
    Set<String> seen = new HashSet<String>();
    String newest = "";
    double latestTime = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < sources.length(); i++) {
      JSONObject row = sources.getJSONObject(i);
      if (!id.equals(String.valueOf(row.opt("r:ssObjectId"))))
        throw new IllegalStateException("Unexpected SS object");
      String source = sourceId(row.opt("r:diaSourceId"));
      if (!seen.add(source)) throw new IllegalStateException("Duplicate SS source ID");
      double mjd = row.getDouble("r:midpointMjdTai");
      if (!Double.isFinite(mjd) || mjd <= 0) throw new IllegalStateException("Invalid SS source time");
      if (mjd > latestTime || (mjd == latestTime && source.compareTo(newest) > 0)) {
        latestTime = mjd;
        newest = source;
      }
    }
    return newest;
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
  JSONArray esRequests = new JSONArray();
  JSONArray finkRequests = new JSONArray();
  String id;
  try {
    String input;
    try (InputStream stream = request.getInputStream()) {
      input = new String(readLimited(stream, 512), StandardCharsets.UTF_8);
    }
    id = new JSONObject(input).getString("objectId");
    if (!id.matches("[0-9]{1,24}")) throw new IllegalArgumentException("Invalid object ID");
  } catch (Exception invalidInput) {
    response.setStatus(400);
    out.print(new JSONObject().put("error", "Invalid object request")
      .put("esRequests", esRequests).put("finkRequests", finkRequests).toString());
    return;
  }
  try {
    JSONObject identity = new JSONObject().put("docs", new JSONArray().put(
      new JSONObject().put("_id", id).put("_source", false)));
    JSONObject doc = new JSONObject(post(upstreamBase("fink.cutout.esUrl", ES_BASE),
      "/ss_radec/_mget", identity, 16384, esRequests)).getJSONArray("docs").getJSONObject(0);
    if (!id.equals(doc.optString("_id")) || !(doc.opt("found") instanceof Boolean))
      throw new IllegalStateException("Invalid SS identity response");
    String api = upstreamBase("fink.cutout.apiUrl", API_BASE);
    String newest;
    if (doc.optBoolean("found")) newest = latestSS(id, api, finkRequests);
    else {
      JSONObject body = new JSONObject().put("diaObjectId", id)
        .put("columns", "r:diaSourceId").put("output-format", "json");
      JSONArray sources = new JSONArray(post(api, "/api/v1/sources", body, 524288, finkRequests));
      // Fink's DIA sources endpoint sorts newest first; retain the existing DIA behavior.
      newest = sources.length() > 0 ? sourceId(sources.getJSONObject(0).opt("r:diaSourceId")) : "";
    }
    out.print(new JSONObject().put("diaSourceId", newest)
      .put("esRequests", esRequests).put("finkRequests", finkRequests).toString());
  } catch (Exception error) {
    response.setStatus(502);
    out.print(new JSONObject().put("error", "Source lookup failed")
      .put("esRequests", esRequests).put("finkRequests", finkRequests).toString());
  }
%>
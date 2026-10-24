<%@ page language="java" contentType="application/json; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ page import="org.json.JSONArray,org.json.JSONObject,org.json.JSONException" %>
<%@ page import="java.io.ByteArrayOutputStream,java.io.InputStream,java.io.OutputStream,java.io.IOException" %>
<%@ page import="java.net.HttpURLConnection,java.net.URL" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.ArrayList,java.util.LinkedHashSet,java.util.Set" %>
<%!
  private static final int MAX_OBJECTS = 500;
  private static final int MAX_POINTS = 4000;
  private static final int MAX_REQUEST_BYTES = 48000;
  private static final int MAX_RESPONSE_BYTES = 4000000;
  private static String esBase(String survey) {
    // JVM properties allow an isolated loopback fixture; callers cannot choose an ES host.
    return System.getProperty("fink.skyview." + survey + "EsUrl",
      survey.equals("ztf") ? "http://157.136.253.253:24499" : "http://134.158.243.139:24499");
  }
  private static byte[] bounded(InputStream stream, int limit) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    byte[] chunk = new byte[4096];
    int n;
    while ((n = stream.read(chunk)) != -1) {
      if (bytes.size() + n > limit) throw new IOException("payload too large");
      bytes.write(chunk, 0, n);
    }
    return bytes.toByteArray();
  }
  private static JSONArray mget(String survey, String index, ArrayList<String> ids) throws Exception {
    if (ids.isEmpty()) return new JSONArray();
    JSONArray docs = new JSONArray();
    for (String id : ids) docs.put(new JSONObject().put("_id", id)
      .put("_source", new JSONArray().put("location")));
    byte[] body = new JSONObject().put("docs", docs).toString().getBytes(StandardCharsets.UTF_8);
    HttpURLConnection connection = (HttpURLConnection)new URL(esBase(survey) + "/" + index + "/_mget").openConnection();
    connection.setInstanceFollowRedirects(false);
    connection.setRequestMethod("POST"); // _mget is read-only
    connection.setConnectTimeout(8000);
    connection.setReadTimeout(12000);
    connection.setDoOutput(true);
    connection.setRequestProperty("Accept", "application/json");
    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
    connection.setFixedLengthStreamingMode(body.length);
    try {
      try (OutputStream output = connection.getOutputStream()) { output.write(body); }
      int status = connection.getResponseCode();
      if (status < 200 || status >= 300) throw new IOException("Elasticsearch HTTP " + status);
      try (InputStream input = connection.getInputStream()) {
        return new JSONObject(new String(bounded(input, MAX_RESPONSE_BYTES), StandardCharsets.UTF_8)).getJSONArray("docs");
      }
    } finally { connection.disconnect(); }
  }
  private static void addPositions(JSONArray docs, Set<String> requested, Set<String> found,
                                   JSONArray points) throws Exception {
    for (int i = 0; i < docs.length(); i++) {
      JSONObject doc = docs.optJSONObject(i);
      if (doc == null) continue;
      String id = doc.optString("_id", "");
      if (!requested.contains(id) || !doc.optBoolean("found")) continue;
      JSONObject source = doc.optJSONObject("_source");
      if (source == null) continue;
      Object raw = source.opt("location");
      JSONArray locations = raw instanceof JSONArray ? (JSONArray)raw : new JSONArray().put(raw);
      for (int j = 0; j < locations.length(); j++) {
        JSONObject position = locations.optJSONObject(j);
        if (position == null) continue;
        double lon = position.optDouble("lon", Double.NaN);
        double dec = position.optDouble("lat", Double.NaN);
        if (!Double.isFinite(lon) || lon < -180 || lon > 180 ||
            !Double.isFinite(dec) || dec < -90 || dec > 90) continue;
        if (points.length() >= MAX_POINTS) throw new IllegalArgumentException("Too many sky positions");
        double ra = ((lon + 180) % 360 + 360) % 360;
        points.put(new JSONObject().put("objectId", id).put("ra", ra).put("dec", dec));
        found.add(id);
      }
    }
  }
%>
<%
  response.setHeader("Cache-Control", "no-store");
  response.setHeader("X-Content-Type-Options", "nosniff");
  if (!"POST".equals(request.getMethod())) {
    response.setStatus(405);
    out.print(new JSONObject().put("error", "POST required"));
    return;
  }
  try {
    String text;
    try (InputStream input = request.getInputStream()) {
      try { text = new String(bounded(input, MAX_REQUEST_BYTES), StandardCharsets.UTF_8); }
      catch (IOException oversized) { throw new IllegalArgumentException("Request too large"); }
    }
    JSONArray input;
    try { input = new JSONObject(text).getJSONArray("ids"); }
    catch (JSONException malformed) { throw new IllegalArgumentException("Expected an ids array"); }
    if (input.length() > MAX_OBJECTS || input.length() == 0) throw new IllegalArgumentException("Expected 1–500 object IDs");
    LinkedHashSet<String> requested = new LinkedHashSet<String>();
    ArrayList<String> ztf = new ArrayList<String>();
    ArrayList<String> lsst = new ArrayList<String>();
    for (int i = 0; i < input.length(); i++) {
      if (!(input.get(i) instanceof String)) throw new IllegalArgumentException("Invalid object ID");
      String id = input.getString(i);
      if (!id.matches("(?:ZTF[A-Za-z0-9_-]{1,80}|[0-9]{1,24})")) throw new IllegalArgumentException("Invalid object ID");
      if (requested.add(id)) {
        if (id.startsWith("ZTF")) ztf.add(id); else lsst.add(id);
      }
    }
    JSONArray points = new JSONArray();
    LinkedHashSet<String> found = new LinkedHashSet<String>();
    addPositions(mget("ztf", "radec", ztf), requested, found, points);
    addPositions(mget("lsst", "dia_radec", lsst), requested, found, points);
    ArrayList<String> solar = new ArrayList<String>();
    for (String id : lsst) if (!found.contains(id)) solar.add(id);
    addPositions(mget("lsst", "ss_radec", solar), requested, found, points);
    JSONArray missing = new JSONArray();
    for (String id : requested) if (!found.contains(id)) missing.put(id);
    out.print(new JSONObject().put("points", points).put("missing", missing).toString());
  } catch (IllegalArgumentException error) {
    response.setStatus(400);
    out.print(new JSONObject().put("error", error.getMessage()).toString());
  } catch (Exception error) {
    response.setStatus(502);
    out.print(new JSONObject().put("error", "Sky coordinate lookup failed").toString());
  }
%>

<%@ page language="java" contentType="application/json; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ page import="java.net.HttpURLConnection,java.net.URL" %>
<%@ page import="java.io.ByteArrayOutputStream,java.io.InputStream,java.io.OutputStream" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="org.json.JSONObject,org.json.JSONArray,org.json.JSONException" %>
<%!
  private static byte[] limitedLightCurveRead(InputStream stream, int max) throws Exception {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int n;
    while ((n = stream.read(buffer)) != -1) {
      if (result.size() + n > max) throw new IllegalStateException("Response too large");
      result.write(buffer, 0, n);
    }
    return result.toByteArray();
  }
  private static double finiteLightCurveNumber(JSONObject row, String key) {
    Object value = row.opt(key);
    if (!(value instanceof Number)) return Double.NaN;
    double number = ((Number)value).doubleValue();
    return Double.isFinite(number) ? number : Double.NaN;
  }
  private static final String ES_BASE = "http://134.158.243.139:24499";
  private static final String LSST_API_BASE = "https://api.lsst.fink-portal.org";
  private static final String ZTF_API_BASE = "https://api.ztf.fink-portal.org";
  // Test fixtures may replace an upstream only with a loopback URL.
  private static String lightCurveBase(String property, String normal) {
    String fixture = System.getProperty(property);
    if (fixture == null) return normal;
    if (!fixture.matches("http://(?:127\\.0\\.0\\.1|localhost):[0-9]{1,5}"))
      throw new IllegalArgumentException("Fixture must be loopback");
    return fixture;
  }
  private static String lightCurvePost(String base, String path, JSONObject data, int max, int readTimeout) throws Exception {
    byte[] body = data.toString().getBytes(StandardCharsets.UTF_8);
    HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
    connection.setInstanceFollowRedirects(false);
    connection.setConnectTimeout(5000);
    connection.setReadTimeout(readTimeout);
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
    connection.setRequestProperty("Accept", "application/json");
    connection.setFixedLengthStreamingMode(body.length);
    try {
      try (OutputStream output = connection.getOutputStream()) { output.write(body); }
      if (connection.getResponseCode() != 200) throw new IllegalStateException("Fink source request failed");
      try (InputStream stream = connection.getInputStream()) {
        return new String(limitedLightCurveRead(stream, max), StandardCharsets.UTF_8);
      }
    } finally { connection.disconnect(); }
  }
  // Rubin SS and DIA object IDs share one numeric format; ss_radec membership decides which one this is.
  // Returns null when the index cannot answer, so the caller never guesses silently.
  private static Boolean isSolarSystemObject(String id) {
    try {
      JSONObject request = new JSONObject().put("docs", new JSONArray().put(
        new JSONObject().put("_id", id).put("_source", false)));
      JSONObject doc = new JSONObject(lightCurvePost(lightCurveBase("fink.lightcurve.esUrl", ES_BASE),
        "/ss_radec/_mget", request, 16384, 8000)).getJSONArray("docs").getJSONObject(0);
      if (!id.equals(doc.optString("_id")) || !(doc.opt("found") instanceof Boolean)) return null;
      return doc.getBoolean("found");
    } catch (Exception unavailable) { return null; }
  }
  // /sso accepts a designation, never a Rubin ssObjectId: reverse-resolve the exact ID first.
  private static JSONArray solarSystemSources(String id, String api) throws Exception {
    JSONObject resolverBody = new JSONObject().put("resolver", "ssodnet")
      .put("name_or_id", id).put("reverse", true).put("nmax", 10).put("output-format", "json");
    JSONArray resolved = new JSONArray(lightCurvePost(api, "/api/v1/resolver", resolverBody, 131072, 15000));
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
    if (designation.isEmpty() || designation.length() > 128) throw new IllegalStateException("SS designation unavailable");
    JSONObject body = new JSONObject().put("n_or_d", designation)
      .put("columns", "r:ssObjectId,r:diaSourceId,r:midpointMjdTai,r:band,r:scienceFlux,r:scienceFluxErr").put("output-format", "json");
    return new JSONArray(lightCurvePost(api, "/api/v1/sso", body, 4194304, 90000));
  }
  // Bound concurrent upstream requests across all sessions of this JSP.
  private final java.util.concurrent.Semaphore lightCurveSlots = new java.util.concurrent.Semaphore(4);
%>
<%
  response.setHeader("Cache-Control", "no-store");
  response.setHeader("X-Content-Type-Options", "nosniff");
  if (!"POST".equals(request.getMethod())) {
    response.setStatus(405); out.print("{\"error\":\"POST required\"}"); return;
  }
  if (!lightCurveSlots.tryAcquire()) {
    response.setStatus(503); response.setHeader("Retry-After", "5");
    out.print("{\"error\":\"Light curve service busy\"}"); return;
  }
  try {
    if (request.getContentLengthLong() > 512) throw new IllegalArgumentException("Request too large");
    String objectId;
    try (InputStream stream = request.getInputStream()) {
      objectId = new JSONObject(new String(limitedLightCurveRead(stream, 512), StandardCharsets.UTF_8)).getString("objectId");
    }
    boolean ztf = objectId.matches("ZTF[A-Za-z0-9_-]{1,80}");
    boolean lsst = objectId.matches("[0-9]{1,24}");
    if (!ztf && !lsst) throw new IllegalArgumentException("Invalid object ID");
    boolean solar = false;
    JSONArray sources;
    if (ztf) {
      JSONObject body = new JSONObject().put("objectId", objectId).put("output-format", "json")
        .put("columns", "i:objectId,i:candid,i:jd,i:fid,i:magpsf,i:sigmapsf");
      sources = new JSONArray(lightCurvePost(lightCurveBase("fink.lightcurve.ztfUrl", ZTF_API_BASE),
        "/api/v1/objects", body, 4194304, 20000));
    } else {
      String api = lightCurveBase("fink.lightcurve.apiUrl", LSST_API_BASE);
      Boolean known = isSolarSystemObject(objectId);
      solar = Boolean.TRUE.equals(known);
      if (solar) sources = solarSystemSources(objectId, api);
      else {
        JSONObject body = new JSONObject().put("diaObjectId", objectId).put("output-format", "json")
          .put("columns", "r:diaObjectId,r:diaSourceId,r:midpointMjdTai,r:band,r:scienceFlux,r:scienceFluxErr");
        sources = new JSONArray(lightCurvePost(api, "/api/v1/sources", body, 4194304, 20000));
        // Without the index answer, an empty DIA reply must not look like a genuinely empty light curve.
        if (known == null && sources.length() == 0) throw new IllegalStateException("Object type unknown");
      }
    }
    if (sources.length() > 10000) throw new IllegalStateException("Too many sources");
    JSONArray points = new JSONArray();
    int excluded = 0;
    for (int i = 0; i < sources.length(); i++) {
      JSONObject source = sources.optJSONObject(i);
      if (source == null) { excluded++; continue; }
      Object returned = source.opt(ztf ? "i:objectId" : solar ? "r:ssObjectId" : "r:diaObjectId");
      if (returned == null || JSONObject.NULL.equals(returned)) { excluded++; continue; }
      String returnedId = returned.toString();
      if (!objectId.equals(returnedId)) throw new IllegalStateException("Mismatched object ID");
      double mjd = finiteLightCurveNumber(source, ztf ? "i:jd" : "r:midpointMjdTai");
      String band;
      double magnitude, uncertainty;
      Object sourceValue = source.opt(ztf ? "i:candid" : "r:diaSourceId");
      String sourceId = sourceValue == null || JSONObject.NULL.equals(sourceValue) ? "" : sourceValue.toString();
      if (!sourceId.matches("[0-9]{1,24}")) { excluded++; continue; }
      if (ztf) {
        double fid = finiteLightCurveNumber(source, "i:fid");
        band = fid == 1 ? "g" : fid == 2 ? "r" : "";
        mjd -= 2400000.5; // ZTF uses JD; the plot uses MJD.
        magnitude = finiteLightCurveNumber(source, "i:magpsf");
        uncertainty = finiteLightCurveNumber(source, "i:sigmapsf");
      } else {
        band = source.optString("r:band", "");
        // Forced photometry on the science image, not difference-image PSF flux.
        double flux = finiteLightCurveNumber(source, "r:scienceFlux");
        double error = finiteLightCurveNumber(source, "r:scienceFluxErr");
        magnitude = flux > 0 ? 31.4 - 2.5 * Math.log10(flux) : Double.NaN;
        uncertainty = flux > 0 ? (2.5 / Math.log(10)) * error / flux : Double.NaN;
      }
      if (!(ztf ? "g".equals(band) || "r".equals(band) : "ugrizy".contains(band) && band.length() == 1) ||
          !Double.isFinite(mjd) || !Double.isFinite(magnitude) ||
          !Double.isFinite(uncertainty) || uncertainty < 0) { excluded++; continue; }
      points.put(new JSONObject().put("sourceId", sourceId).put("band", band)
        .put("mjd", mjd).put("magnitude", magnitude).put("magnitudeError", uncertainty));
    }
    // Stable time order without taking a truncated prefix from the upstream API.
    java.util.List<JSONObject> ordered = new java.util.ArrayList<JSONObject>();
    for (int i = 0; i < points.length(); i++) ordered.add(points.getJSONObject(i));
    java.util.Collections.sort(ordered, new java.util.Comparator<JSONObject>() {
      public int compare(JSONObject a, JSONObject b) {
        return Double.compare(a.getDouble("mjd"), b.getDouble("mjd"));
      }
    });
    JSONArray sorted = new JSONArray();
    for (JSONObject point : ordered) sorted.put(point);
    out.print(new JSONObject().put("survey", ztf ? "ZTF" : "LSST")
      .put("objectId", objectId).put("objectType", ztf ? "ZTF" : solar ? "SS" : "DIA").put("timeUnit", "MJD")
      .put("points", sorted).put("totalRows", sources.length()).put("excluded", excluded).toString());
  } catch (IllegalArgumentException | JSONException error) {
    response.setStatus(400); out.print("{\"error\":\"Invalid object request\"}");
  } catch (Exception error) {
    response.setStatus(502); out.print("{\"error\":\"Light curve lookup failed\"}");
  } finally { lightCurveSlots.release(); }
%>

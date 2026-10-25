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
    URL endpoint = new URL(ztf ? "https://api.ztf.fink-portal.org/api/v1/objects" :
      "https://api.lsst.fink-portal.org/api/v1/sources");
    String columns = ztf ? "i:objectId,i:candid,i:jd,i:fid,i:magpsf,i:sigmapsf" :
      "r:diaObjectId,r:diaSourceId,r:midpointMjdTai,r:band,r:psfFlux,r:psfFluxErr";
    byte[] body = new JSONObject().put(ztf ? "objectId" : "diaObjectId", objectId)
      .put("columns", columns).put("output-format", "json").toString().getBytes(StandardCharsets.UTF_8);
    HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
    connection.setInstanceFollowRedirects(false);
    connection.setConnectTimeout(5000);
    connection.setReadTimeout(20000);
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
    connection.setRequestProperty("Accept", "application/json");
    connection.setFixedLengthStreamingMode(body.length);
    JSONArray sources;
    try {
      try (OutputStream output = connection.getOutputStream()) { output.write(body); }
      if (connection.getResponseCode() != 200) throw new IllegalStateException("Fink source request failed");
      try (InputStream stream = connection.getInputStream()) {
        sources = new JSONArray(new String(limitedLightCurveRead(stream, 4194304), StandardCharsets.UTF_8));
      }
    } finally { connection.disconnect(); }
    if (sources.length() > 10000) throw new IllegalStateException("Too many sources");
    JSONArray points = new JSONArray();
    int excluded = 0;
    for (int i = 0; i < sources.length(); i++) {
      JSONObject source = sources.optJSONObject(i);
      if (source == null) { excluded++; continue; }
      Object returned = source.opt(ztf ? "i:objectId" : "r:diaObjectId");
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
        double flux = finiteLightCurveNumber(source, "r:psfFlux");
        double error = finiteLightCurveNumber(source, "r:psfFluxErr");
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
      .put("objectId", objectId).put("timeUnit", "MJD")
      .put("points", sorted).put("totalRows", sources.length()).put("excluded", excluded).toString());
  } catch (IllegalArgumentException | JSONException error) {
    response.setStatus(400); out.print("{\"error\":\"Invalid object request\"}");
  } catch (Exception error) {
    response.setStatus(502); out.print("{\"error\":\"Light curve lookup failed\"}");
  } finally { lightCurveSlots.release(); }
%>

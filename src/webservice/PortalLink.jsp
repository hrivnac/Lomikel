<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ page import="java.net.HttpURLConnection,java.net.URL" %>
<%@ page import="java.io.ByteArrayOutputStream,java.io.InputStream,java.io.OutputStream" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="org.json.JSONObject,org.json.JSONArray" %>
<%!
  // Redirects a numeric LSST object ID to its Fink Portal page. DIA and Solar System
  // object IDs look alike: the Portal addresses DIA objects by diaObjectId, but Solar
  // System objects only by their packed provisional designation (an ssObjectId page
  // reports "not found"). The SS identity comes from an identity-only ss_radec lookup,
  // the designation from Fink's exact reverse resolver, as in CutoutSource.jsp.
  private static final String ES_BASE = "http://134.158.243.139:24499";
  private static final String API_BASE = "https://api.lsst.fink-portal.org";
  private static final String PORTAL = "https://lsst.fink-portal.org/";
  private static byte[] readLimited(InputStream input, int max) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    byte[] block = new byte[4096];
    int count;
    while ((count = input.read(block)) != -1) {
      if (bytes.size() + count > max) throw new IllegalStateException("Upstream response too large");
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
  private static String post(String base, String path, JSONObject data, int max) throws Exception {
    byte[] body = data.toString().getBytes(StandardCharsets.UTF_8);
    HttpURLConnection connection = (HttpURLConnection)new URL(base + path).openConnection();
    connection.setInstanceFollowRedirects(false);
    connection.setConnectTimeout(8000);
    connection.setReadTimeout(20000);
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
    connection.setRequestProperty("Accept", "application/json");
    connection.setFixedLengthStreamingMode(body.length);
    try {
      try (OutputStream output = connection.getOutputStream()) { output.write(body); }
      if (connection.getResponseCode() != 200) throw new IllegalStateException("Lookup failed");
      try (InputStream input = connection.getInputStream()) {
        return new String(readLimited(input, max), StandardCharsets.UTF_8);
      }
    } finally { connection.disconnect(); }
  }
  // Path segment of the Portal page: the diaObjectId itself, or the SS designation.
  private static String portalPath(String id, String es, String api) throws Exception {
    JSONObject identity = new JSONObject().put("docs", new JSONArray().put(
      new JSONObject().put("_id", id).put("_source", false)));
    JSONObject doc = new JSONObject(post(es, "/ss_radec/_mget", identity, 16384))
      .getJSONArray("docs").getJSONObject(0);
    if (!id.equals(doc.optString("_id")) || !(doc.opt("found") instanceof Boolean))
      throw new IllegalStateException("Invalid SS identity response");
    if (!doc.getBoolean("found")) return id;
    JSONObject resolverBody = new JSONObject().put("resolver", "ssodnet")
      .put("name_or_id", id).put("reverse", true).put("nmax", 10).put("output-format", "json");
    JSONArray resolved = new JSONArray(post(api, "/api/v1/resolver", resolverBody, 131072));
    if (resolved.length() > 10) throw new IllegalStateException("Too many resolver rows");
    JSONObject match = null;
    for (int i = 0; i < resolved.length(); i++) {
      JSONObject row = resolved.getJSONObject(i);
      if (!id.equals(String.valueOf(row.opt("r:ssObjectId")))) continue;
      if (match != null) throw new IllegalStateException("Ambiguous SS object");
      match = row;
    }
    if (match == null) throw new IllegalStateException("SS object not resolved");
    // The Portal routes Solar System pages by the packed designation only.
    String designation = match.optString("r:packed_primary_provisional_designation", "").trim();
    if (!designation.matches("[A-Za-z0-9]{3,16}"))
      throw new IllegalStateException("SS designation unavailable");
    return designation;
  }
%>
<%
  response.setHeader("Cache-Control", "no-store");
  response.setHeader("X-Content-Type-Options", "nosniff");
  if (!"GET".equals(request.getMethod())) {
    response.setStatus(405);
    out.print("<!DOCTYPE html><title>Fink Portal link</title><p>GET required.</p>");
    return;
  }
  String id = request.getParameter("objectId");
  if (id == null || !id.matches("[1-9][0-9]{0,18}")) {
    response.setStatus(400);
    out.print("<!DOCTYPE html><title>Fink Portal link</title><p>Invalid LSST object ID.</p>");
    return;
  }
  try {
    String target = portalPath(id, upstreamBase("fink.portal.esUrl", ES_BASE),
                               upstreamBase("fink.portal.apiUrl", API_BASE));
    response.setStatus(302);
    response.setHeader("Location", PORTAL + target);
  } catch (Exception error) {
    // The validated ID is digits only; no upstream text is echoed.
    response.setStatus(502);
    out.print("<!DOCTYPE html><title>Fink Portal link</title><p>The Fink Portal page of LSST object " + id +
              " could not be determined: the Solar System identity or designation lookup failed." +
              " Please try again later.</p>");
  }
%>

<%@ page language="java" contentType="application/json; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ page import="java.net.HttpURLConnection,java.net.URL" %>
<%@ page import="java.io.ByteArrayOutputStream,java.io.InputStream,java.io.OutputStream" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="org.json.JSONObject,org.json.JSONArray,org.json.JSONException" %>
<%!
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
%>
<%
  response.setHeader("Cache-Control", "no-store");
  response.setHeader("X-Content-Type-Options", "nosniff");
  if (!"POST".equals(request.getMethod())) {
    response.setStatus(405);
    out.print("{\"error\":\"POST required\"}");
    return;
  }
  try {
    String input;
    try (InputStream stream = request.getInputStream()) {
      input = new String(readLimited(stream, 512), StandardCharsets.UTF_8);
    }
    String diaObjectId = new JSONObject(input).getString("diaObjectId");
    if (!diaObjectId.matches("[0-9]{1,24}")) throw new IllegalArgumentException("Invalid diaObjectId");
    // The endpoint is fixed; neither a browser parameter nor an object ID chooses a host.
    URL endpoint = new URL("https://api.lsst.fink-portal.org/api/v1/sources");
    HttpURLConnection connection = (HttpURLConnection)endpoint.openConnection();
    connection.setInstanceFollowRedirects(false);
    connection.setConnectTimeout(5000);
    connection.setReadTimeout(12000);
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
    connection.setRequestProperty("Accept", "application/json");
    byte[] body = new JSONObject().put("diaObjectId", diaObjectId)
      .put("columns", "r:diaSourceId").put("output-format", "json")
      .toString().getBytes(StandardCharsets.UTF_8);
    connection.setFixedLengthStreamingMode(body.length);
    JSONArray sources;
    try {
      try (OutputStream output = connection.getOutputStream()) { output.write(body); }
      if (connection.getResponseCode() != 200) throw new IllegalStateException("Source lookup failed");
      try (InputStream stream = connection.getInputStream()) {
        sources = new JSONArray(new String(readLimited(stream, 524288), StandardCharsets.UTF_8));
      }
    } finally { connection.disconnect(); }
    // The Fink sources API sorts newest first. A cutout belongs to a source,
    // not to a diaObjectId; use the most recent source for all three stamps.
    String diaSourceId = sources.length() > 0 ? sources.getJSONObject(0).get("r:diaSourceId").toString() : "";
    if (!diaSourceId.isEmpty() && !diaSourceId.matches("[0-9]{1,24}"))
      throw new IllegalStateException("Invalid source lookup response");
    out.print(new JSONObject().put("diaSourceId", diaSourceId).toString());
  } catch (IllegalArgumentException | JSONException error) {
    response.setStatus(400);
    out.print("{\"error\":\"Invalid object request\"}");
  } catch (Exception error) {
    response.setStatus(502);
    out.print("{\"error\":\"Source lookup failed\"}");
  }
%>

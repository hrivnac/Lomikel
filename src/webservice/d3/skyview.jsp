<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="org.json.JSONArray" %>
<%@ page import="java.io.ByteArrayOutputStream,java.io.InputStream,java.io.IOException" %>
<%@ page import="java.net.URLDecoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.HashMap,java.util.Map" %>
<%!
  private static final int MAX_REQUEST_BYTES = 1048576;
  private static byte[] readBounded(InputStream input) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    byte[] chunk = new byte[4096];
    int n;
    while ((n = input.read(chunk, 0, Math.min(chunk.length, MAX_REQUEST_BYTES + 1 - bytes.size()))) != -1) {
      if (bytes.size() + n > MAX_REQUEST_BYTES) throw new IllegalArgumentException("Request too large");
      bytes.write(chunk, 0, n);
    }
    return bytes.toByteArray();
  }
%>
<%
  JSONArray points = new JSONArray();
  int requested = 0, missing = 0;
  String errorMessage = null;
  try {
    if (!"POST".equals(request.getMethod())) throw new IllegalArgumentException("POST required");
    if (request.getContentLengthLong() > MAX_REQUEST_BYTES) throw new IllegalArgumentException("Request too large");
    byte[] body;
    try (InputStream input = request.getInputStream()) { body = readBounded(input); }
    Map<String, String> fields = new HashMap<String, String>();
    String form = new String(body, StandardCharsets.UTF_8);
    for (String pair : form.split("&")) {
      int equals = pair.indexOf('=');
      if (equals < 0) throw new IllegalArgumentException("Invalid form data");
      fields.put(URLDecoder.decode(pair.substring(0, equals), "UTF-8"),
        URLDecoder.decode(pair.substring(equals + 1), "UTF-8"));
    }
    points = new JSONArray(fields.get("points"));
    if (points.length() > 4000) throw new IllegalArgumentException("Too many positions");
    requested = Integer.parseInt(fields.get("requested"));
    missing = Integer.parseInt(fields.get("missing"));
    if (requested < 0 || requested > 500 || missing < 0 || missing > requested) throw new IllegalArgumentException("Invalid counts");
  } catch (Exception error) {
    points = new JSONArray();
    requested = missing = 0;
    errorMessage = "Request too large".equals(error.getMessage()) ? "Request too large" : "Invalid SkyView request";
    response.setStatus("Request too large".equals(errorMessage) ? 413 : 400);
  }
  String safeJson = points.toString().replace("<", "\\u003c")
    .replace(">", "\\u003e").replace("&", "\\u0026")
    .replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
%>
<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Objects · SkyView</title>
<link rel="stylesheet" href="../../d3-celestial-0.7.32/celestial.css">
<link rel="stylesheet" href="skyview.css?v=skyview-3">
</head><body>
<header><h1>Objects on the sky</h1><p id="sky-summary" role="status"><%=errorMessage == null ? "" : errorMessage%></p><span id="sky-legend" aria-label="Object colors"></span><% if (errorMessage == null) { %><button type="button" id="sky-settings-toggle" aria-controls="celestial-form" aria-expanded="false">Map settings</button><% } %></header>
<div id="celestial-map"></div><div id="celestial-form"></div>
<details id="sky-coordinates"><summary>Plotted object coordinates</summary><div id="sky-list"></div></details>
<script type="application/json" id="sky-points"><%=safeJson%></script>
<script>window.skyRequested = <%=requested%>; window.skyMissing = <%=missing%>;</script>
<script src="../../d3-celestial-0.7.32/lib/d3.min.js"></script>
<script src="../../d3-celestial-0.7.32/lib/d3.geo.projection.min.js"></script>
<script src="../../d3-celestial-0.7.32/celestial.min.js"></script>
<% if (errorMessage == null) { %><script src="skyview.js?v=skyview-5"></script><% } %>
</body></html>

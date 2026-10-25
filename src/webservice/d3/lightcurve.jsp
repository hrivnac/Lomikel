<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.io.ByteArrayOutputStream,java.io.InputStream" %>
<%@ page import="java.net.URLDecoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="org.json.JSONObject" %>
<%!
  private static String lightCurveObjectId(InputStream input) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    byte[] buffer = new byte[256];
    int n;
    while ((n = input.read(buffer)) != -1) {
      if (bytes.size() + n > 512) throw new IllegalArgumentException("Request too large");
      bytes.write(buffer, 0, n);
    }
    String form = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    if (!form.startsWith("objectId=") || form.contains("&")) throw new IllegalArgumentException("Invalid form");
    return URLDecoder.decode(form.substring("objectId=".length()), "UTF-8");
  }
%>
<%
  response.setHeader("Cache-Control", "no-store");
  String objectId = "";
  try {
    if (!"POST".equals(request.getMethod()) || request.getContentLengthLong() > 512)
      throw new IllegalArgumentException("POST required");
    try (InputStream input = request.getInputStream()) { objectId = lightCurveObjectId(input); }
    if (!objectId.matches("(?:ZTF[A-Za-z0-9_-]{1,80}|[0-9]{1,24})"))
      throw new IllegalArgumentException("Invalid object ID");
  } catch (Exception error) { response.setStatus(400); }
  String safeId = JSONObject.quote(objectId).replace("<", "\\u003c");
%>
<!DOCTYPE html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Object light curve</title>
<link rel="stylesheet" href="lightcurve.css?v=lightcurve-fit-1">
</head><body>
<main id="lightcurve-view"><h1>Object light curve</h1><p id="lightcurve-status" role="status"><%=objectId.isEmpty() ? "Invalid object request." : "Loading observations…"%></p>
<div id="lightcurve-legend" aria-label="Available photometric bands"></div>
<div id="lightcurve-chart" role="region" aria-label="Light curve magnitude versus calendar date"></div>
<p id="lightcurve-note"></p></main>
<% if (!objectId.isEmpty()) { %>
<script src="../../d3-v6.0.0/d3.js"></script>
<script src="lightcurve.js?v=science-date-1"></script>
<script>window.lightCurveLoad(<%=safeId%>);</script>
<% } %>
</body></html>

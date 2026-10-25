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

<%!
  private static final String ES_BASE = "http://134.158.243.139:24499";
  private static final String DIA_MJD_SEARCH = "/dia_mjd/_search";
  private static final String DIA_RADEC_MGET = "/dia_radec/_mget";
  private static final int ES_TIMEOUT_MS = 10000;

  private static JSONObject postJson(String path, JSONObject payload) throws Exception {
    HttpURLConnection connection = (HttpURLConnection)new URL(ES_BASE + path).openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(ES_TIMEOUT_MS);
    connection.setReadTimeout(ES_TIMEOUT_MS);
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
        ? connection.getInputStream()
        : connection.getErrorStream();
      StringBuilder responseBody = new StringBuilder();
      if (stream != null) {
        try (BufferedReader reader = new BufferedReader(
               new InputStreamReader(stream, StandardCharsets.UTF_8))) {
          String line;
          while ((line = reader.readLine()) != null) responseBody.append(line);
          }
        }
      if (status < 200 || status >= 300) throw new IOException("Elasticsearch HTTP " + status);
      return new JSONObject(responseBody.toString());
      }
    finally {
      connection.disconnect();
      }
    }
%>

<%
  response.setHeader("Cache-Control", "no-store");
  if ("1".equals(request.getParameter("probe"))) {
    out.print(new JSONObject().put("latestAlerts", true).toString());
    return;
    }
  int limit = 10;
  try {
    limit = Integer.parseInt(request.getParameter("n"));
    }
  catch (Exception ignored) {
    }
  limit = Math.min(100, Math.max(1, limit));

  try {
    JSONObject query = new JSONObject()
      .put("size", limit)
      .put("_source", new JSONArray().put("mjd"))
      .put("sort", new JSONArray().put(
        new JSONObject().put("mjd", new JSONObject().put("order", "desc"))));
    JSONArray hits = postJson(DIA_MJD_SEARCH, query)
      .getJSONObject("hits")
      .getJSONArray("hits");
    JSONArray ids = new JSONArray();
    for (int i = 0; i < hits.length(); i++) ids.put(hits.getJSONObject(i).getString("_id"));
    JSONArray documents = ids.length() == 0
      ? new JSONArray()
      : postJson(DIA_RADEC_MGET, new JSONObject().put("ids", ids)).getJSONArray("docs");
    out.print(new JSONObject().put("mjdHits", hits).put("radecDocs", documents).toString());
    }
  catch (Exception error) {
    response.setStatus(502);
    out.print(new JSONObject().put("error", "Cannot load latest LSST alerts").toString());
    }
%>

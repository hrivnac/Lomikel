<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel Scatter Plot -->
<!-- @author Julius.Hrivnac@cern.ch -->

<%@ page import="com.Lomikel.WebService.PropertiesProcessor" %>
<%@ page import="org.json.JSONArray" %>
<%@ page import="org.json.JSONObject" %>

<%@ page errorPage="../../ExceptionHandler.jsp" %>

<link href="scatterplot.css?v=details-1" rel="stylesheet" type="text/css"/>
<script src="../../d3-v6.0.0/d3.js" type="text/javascript"></script>

<div id="scatter_area"></div>

<%
  String name  = request.getParameter("name");
  String url   = request.getParameter("url");
  String x     = request.getParameter("x");
  String y     = request.getParameter("y");
  String z     = request.getParameter("z");
  String s     = request.getParameter("s");
  String tdata = request.getParameter("tdata");
  if (name == null) name = "";
  if (url == null) url = "";
  if (x == null) x = "";
  if (y == null) y = "";
  if (z == null) z = "";
  if (s == null) s = "";
  %>
<%@include file="../../PropertiesProcessor.jsp"%>
<%
  JSONArray points = new JSONArray(tdata == null ? "[]" : tdata);
  for (int i = 0; i < points.length(); i++) {
    JSONObject point = points.getJSONObject(i);
    if (point.has("t") && !point.isNull("t")) {
      point.put("t", pp.getTimestamp(point.get("t").toString()));
      }
    }
  %>

<script src="../../d3/actions.js?v=table-1" type="text/javascript"></script>
<script src="scatterplot.js?v=details-1" type="text/javascript"></script>

<script type="text/javascript">
  showScatterPlot(<%=points.toString().replace("<", "\\u003c")%>,
                  <%=JSONObject.quote(name).replace("<", "\\u003c")%>,
                  <%=JSONObject.quote(x).replace("<", "\\u003c")%>,
                  <%=JSONObject.quote(y).replace("<", "\\u003c")%>,
                  <%=JSONObject.quote(z).replace("<", "\\u003c")%>,
                  <%=JSONObject.quote(s).replace("<", "\\u003c")%>,
                  <%=JSONObject.quote(url).replace("<", "\\u003c")%>);
  </script>

<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="org.json.JSONArray" %>
<%@ page errorPage="../../ExceptionHandler.jsp" %>
<%
  String tdata = request.getParameter("tdata");
  JSONArray points = new JSONArray(tdata == null ? "[]" : tdata);
%>
<link href="correlogram.css?v=corr-1" rel="stylesheet" type="text/css"/>
<div id="corr_area"></div>
<script src="../../d3-v6.0.0/d3.js" type="text/javascript"></script>
<script src="../../venn-v.0.2.14/venn.js" type="text/javascript"></script>
<script src="../../d3/venn.js?v=vertical-2" type="text/javascript"></script>
<script src="correlogram.js?v=corr-1" type="text/javascript"></script>
<script type="text/javascript">
  showCorrelogram(<%=points.toString().replace("<", "\\u003c")%>, <%= "1".equals(request.getParameter("invalid")) %>);
</script>

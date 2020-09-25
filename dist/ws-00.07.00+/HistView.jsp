<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel Hist View -->
<!-- @author Julius.Hrivnac@cern.ch -->

<%@ page import="com.Lomikel.WebService.HBaseColumnsProcessor" %>

<%@ page import="java.util.Map" %>

<!--%@ page errorPage="ExceptionHandler.jsp" %-->

<link href="HistView.css" rel="stylesheet" type="text/css"/>
<!-- TBD: it should be loaded from index.jsp -->
<link href="vis-timeline-7.3.9/styles/vis-timeline-graph2d.min.css" rel="stylesheet" type="text/css" />
<script type="text/javascript" src="vis-timeline-7.3.9/standalone/umd/vis-timeline-graph2d.min.js"></script> 
<script type="text/javascript" src="OptionsDefault.js"></script>
<script type="text/javascript" src="Options.js"></script>

<jsp:useBean id="h2table" class="com.Lomikel.WebService.HBase2Table" scope="session"/>

<div id="vis"></div>
  
<%
  String y = request.getParameter("y");
  String[] ys = y.split(" ");
  Map<String, Map<String, String>> table = h2table.table();
  String xVal;
  String yVal;
  Map<String, String> entry;
  String items = "";
  boolean first = true;
  %>
<%@include file="HBaseColumnsProcessor.jsp" %>
<%
  for (Map.Entry<String, Map<String, String>> entry0 : table.entrySet()) {
    xVal = null;
    yVal = null;
    if (!entry0.getKey().startsWith("schema")) {
      xVal = processor.getXDate(entry0);
      entry = entry0.getValue();
      for (String yi : ys) {
        yVal = null;
        for (String k : entry.keySet()) {
          if (k.equals(yi)) {
            yVal = entry.get(k);
            }
          }
        if (xVal != null && yVal != null) {
          if (first) {
            first = false;
            }
          else {
            items += ",";
            }
          items += "{\"x\":\"" + xVal + "\",\"y\":" + yVal + ",\"group\":\"" + yi.split(":")[1] + "\"}";
          }
        }
      }
    }
  items = "["  + items + "]";
  y = "";
  for (String yi : ys) {
    y += yi.split(":")[1] + " ";
    }
  y = y.trim();
  %>
  
<script type="text/javascript" src="HistView.js"></script>
  
<script>
  showHist('<%=items%>', '<%=y%>');
  </script>

 
 
 

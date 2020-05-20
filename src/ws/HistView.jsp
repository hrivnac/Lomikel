<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- JHTools Hist View -->
<!-- @author Julius.Hrivnac@cern.ch -->

<%@ page import="java.util.Map" %>

<!--%@ page errorPage="ExceptionHandler.jsp" %-->

<link href="GraphView.css" rel="stylesheet" type="text/css"/>
<!-- TBD: it should be loaded from index.jsp -->
<link href="vis-timeline-7.6.8/styles/vis-timeline-graph2d.min.css" rel="stylesheet" type="text/css" />
<script type="text/javascript" src="vis-timeline-7.6.8/standalone/umd/vis-timeline-graph2d.min.js"></script> 
<script type="text/javascript" src="OptionsDefault.js"></script>
<script type="text/javascript" src="Options.js"></script>

<jsp:useBean id="h2table" class="com.JHTools.WebService.HBase2Table"           scope="session" />
<jsp:useBean id="hcp"     class="com.JHTools.WebService.HBaseColumnsProcessor" scope="session" />

<div id="graph">
  <script>
    div = document.createElement("div");
    div.style.width = "100%";
    div.style.height = (window.innerHeight * 1.0) + "px";
    div.id = "visnetwork";
    document.getElementById("graph").appendChild(div);
    </script>
  </div>
  
<%
  String y = request.getParameter("y");
  String[] ys = y.split(" ");
  Map<String, Map<String, String>> table = h2table.table();
  String xVal;
  String yVal;
  Map<String, String> entry;
  String items = "";
  boolean first = true;
  for (Map.Entry<String, Map<String, String>> entry0 : table.entrySet()) {
    xVal = null;
    yVal = null;
    if (!entry0.getKey().startsWith("schema")) {
      xVal = hcp.getXDate(entry0);
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
          items += "{\"x\":\"" + xVal + "\",\"y\":" + yVal + ",\"group\":\"" + yi + "\"}";
          }
        }
      }
    }
  items = "["  + items + "]"; 
  %>
  
<script type="text/javascript" src="HistView.js"></script>
  
<script>
  showHist('<%=items%>', '<%=y%>');
  </script>

 
 
 

<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel Scatter Plot -->
<!-- @author Julius.Hrivnac@cern.ch -->

<%@ page import="com.Lomikel.WebService.PropertiesProcessor" %>

<%@ page import="org.apache.logging.log4j.Logger" %>
<%@ page import="org.apache.logging.log4j.LogManager" %>

<%@ page errorPage="../ExceptionHandler.jsp" %>

<%! static Logger log = LogManager.getLogger(org.apache.jsp.d3.neighborhood_jsp.class); %>

<jsp:useBean id="repository" class="com.Lomikel.WebService.StringRepository" scope="session"/>
   
<link href="neighborhood.css" rel="stylesheet" type="text/css"/>
<script src="../d3-v6.0.0/d3.js" type="text/javascript"></script>
 
<div id="viz"></div>
<div id="tooltip"</div>

<%
//  String name  = request.getParameter("name");
//  String url   = request.getParameter("url");
//  String x     = request.getParameter("x");
//  String y     = request.getParameter("y");
//  String z     = request.getParameter("z");
//  String s     = request.getParameter("s");
//  String tdata = request.getParameter("tdata");
  %>
<%@include file="../PropertiesProcessor.jsp"%>
<%
//  String[] dd;
//  String ts;
//  String[] tdatas = tdata.split(",");
//  for (int i = 0; i < tdatas.length; i++) { // TBD: should use JSON
//    dd = tdatas[i].split(":");
//    if (dd[0].equals("\"t\"")) {
//      ts = dd[1].replaceAll("\"", "");
//      ts = pp.getTimestamp(ts);
//      tdatas[i] = "\"t\":\"" + ts + "\"";
//      }
//    }
//  tdata = String.join(",", tdatas);
  String sourceId = "ZTF23abdlxeb";
  String sourceClassification = "{\"YSO_Candidate\": 0.8333, \"SN candidate\": 0.1667}";
  %>

<script src="actions.js"     type="text/javascript"></script>
<script src="neighborhood.js" type="text/javascript"></script>

<script type="text/javascript">
  showNeighbors("a", "<%=sourceId%>", "<%=sourceClassification%>");
  </script>

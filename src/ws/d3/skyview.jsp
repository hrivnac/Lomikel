<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel Sky View -->
<!-- @author Julius.Hrivnac@cern.ch -->

<%@ page import="com.Lomikel.WebService.PropertiesProcessor" %>

<%@ page import="org.apache.log4j.Logger" %>

<%@ page errorPage="../ExceptionHandler.jsp" %>

<%! static Logger log = Logger.getLogger(org.apache.jsp.d3.skyview_jsp.class); %>

<jsp:useBean id="repository" class="com.Lomikel.WebService.StringRepository" scope="session"/>
   
<script src="../d3-celestial-0.7.32/lib/d3.min.js"                type="text/javascript"></script>
<script src="../d3-celestial-0.7.32/lib/d3.geo.projection.min.js" type="text/javascript"></script>
<script src="../d3-celestial-0.7.32/celestial.min.js"             type="text/javascript"></script>
<link href="../d3/skyview.css"                    rel="stylesheet" type="text/css"/>
<link href="../d3-celestial-0.7.32/celestial.css" rel="stylesheet" type="text/css">

<div style="overflow:hidden;margin:0 auto;">
  <div id="celestial-map"></div>
  </div>
<div id="celestial-form"></div>

<%
  String name  = request.getParameter("name");
  String url   = request.getParameter("url");
  String tdata = request.getParameter("tdata");
  %>
<%@include file="../PropertiesProcessor.jsp"%>
  
<script src="actions.js" type="text/javascript"></script>
<script src="skyview.js" type="text/javascript"></script>
  
<script type="text/javascript">
  showSkyView(<%=tdata%>, "<%=name%>", "<%=url%>");
  </script>


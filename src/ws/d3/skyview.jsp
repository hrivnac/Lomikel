<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel Sky View -->
<!-- @author Julius.Hrivnac@cern.ch -->

<%@ page import="org.apache.log4j.Logger" %>

<%@ page errorPage="../ExceptionHandler.jsp" %>

<%! static Logger log = Logger.getLogger(org.apache.jsp.d3.skyview_jsp.class); %>

<jsp:useBean id="repository" class="com.Lomikel.WebService.DataRepository" scope="session"/>
   
<script src="d3-celestial-0.7.32/lib/d3.min.js"                type="text/javascript"></script>
<script src="d3-celestial-0.7.32/lib/d3.geo.projection.min.js" type="text/javascript"></script>
<script src="d3-celestial-0.7.32/celestial.min.js"             type="text/javascript"></script>
<link href="d3/skyview.css"                    rel="stylesheet" type="text/css"/>
<link href="d3-celestial-0.7.32/celestial.css" rel="stylesheet" type="text/css">

<div style="overflow:hidden;margin:0 auto;">
  <div id="celestial-map"></div>
  </div>
<div id="celestial-form"></div>

<%
  String ra       = request.getParameter("ra");
  String dec      = request.getParameter("dec");
  String data     = request.getParameter("data");
  String name     = request.getParameter("name");
  String z        = request.getParameter("z");
  String s        = request.getParameter("s");
  // data supplied as ra-dec
  if (ra  != null && !ra.trim( ).equals("") &&
      dec != null && !dec.trim().equals("")) {
    data = "[{'x':" + ra + ", 'y':" + dec + ", 'z':0, 'k':'x', 'g':0}]";
    }
  // data supplied as JSON string
  else if (data != null && !data.trim().equals("")) {
    }
  // data supplied via DataRepository
  else if (name != null && !name.trim().equals("")) {
    data = repository.get(name);
    }
  // no data found, use demo data
  if (data == null || data.trim().equals("") || data.trim().equals("[]")) {
    data = "[{'x':10, 'y':-20, 'z':5, 'k':'k1', 'g':0}, {'x':60, 'y':90, 'z':6, 'k':'k2', 'g':0}, {'x':80, 'y':50, 'z':7, 'k':'k3', 'g':1}, {'x':60, 'y':30, 'k':'k4', 'g':1}]";
    }
  // Variable names
  String url = "TBD";
  if (z == null) {
    z = "";
    }
  if (s == null) {
    s = "";
    }
  if (name == null) {
    name = "";
    }
  if (z != "") {
    name += " (z: " + z + ")";
    }
  if (s != "") {
    name += " (col: " + s + ")";
    }
  %>

<script src="d3/actions.js" type="text/javascript"></script>
<script src="d3/skyview.js" type="text/javascript"></script>

<script type="text/javascript">
  showSkyView("<%=data%>", "<%=name%>", "<%=z%>", "<%=s%>", "<%=url%>");
  </script>


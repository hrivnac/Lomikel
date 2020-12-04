<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel Sky View -->
<!-- @author Julius.Hrivnac@cern.ch -->

<%@ page import="org.apache.log4j.Logger" %>

<%@ page errorPage="../ExceptionHandler.jsp" %>

<%! static Logger log = Logger.getLogger(org.apache.jsp.d3.skyview_jsp.class); %>

<jsp:useBean id="h2table"    class="com.Lomikel.WebService.HBase2Table"    scope="session"/>
<jsp:useBean id="repository" class="com.Lomikel.WebService.DataRepository" scope="session"/>
   
<script src="../d3-celestial-0.7.32/lib/d3.min.js"                type="text/javascript"></script>
<script src="../d3-celestial-0.7.32/lib/d3.geo.projection.min.js" type="text/javascript"></script>
<script src="../d3-celestial-0.7.32/celestial.min.js"             type="text/javascript"></script>
<link href="skyview.css"                          rel="stylesheet" type="text/css"/>
<link href="../d3-celestial-0.7.32/celestial.css" rel="stylesheet" type="text/css">

<div style="overflow:hidden;margin:0 auto;">
  <div id="celestial-map"></div>
  </div>
<div id="celestial-form"></div>

<%
  String hbase    = request.getParameter("hbase");
  String htable   = request.getParameter("htable");
  String schema   = request.getParameter("schema");
  String group    = request.getParameter("group");
  String data     = request.getParameter("data");
  String dataName = request.getParameter("dataName");
  String name     = request.getParameter("name");
  String z        = request.getParameter("z");
  String s        = request.getParameter("s");
  String gMap = "";
  // data supplied as JSON string
  if (data != null && !data.trim().equals("")) {
    }
  // data supplied via DataRepository
  else if (dataName != null && !dataName.trim().equals("")) {
    data = repository.get(dataName);
    }
  // data supplied via HBase2Table
  else {
   String[] result = h2table.polar(z, s, true);
   data = result[0];
   gMap = result[1];
   }
  // no data found, use demo data
  if (data == null || data.trim().equals("") || data.trim().equals("[]")) {
    data = "[{'x':10, 'y':-20, 'z':5, 'k':'k1', 'g':0}, {'x':60, 'y':90, 'z':6, 'k':'k2', 'g':0}, {'x':80, 'y':50, 'z':7, 'k':'k3', 'g':1}, {'x':60, 'y':30, 'k':'k4', 'g':1}]";
    gMap = "[{'g':0, 's':'aaa'}, {'g':1, 's':'bbb'}]";
    }
  // Variable names
  String url = "HBaseTable.jsp?hbase=" + hbase + "&htable=" + htable + "&schema=" + schema + "&group=" + group + "&selects=*";
  if (z == null) {
    z = "";
    }
  if (s == null) {
    s = "";
    }
  if (name == null) {
    if (dataName != null) {
      name = dataName;
      }
    else {
      name = "";
      }
    }
  if (z != "") {
    name += " (z: " + z + ")";
    }
  if (s != "") {
    name += " (col: " + s + ")";
    }
  %>

<script src="actions.js" type="text/javascript"></script>
<script src="skyview.js" type="text/javascript"></script>

<script type="text/javascript">
  showSkyView("<%=data%>", "<%=gMap%>", "<%=name%>", "<%=z%>", "<%=s%>", "<%=url%>");
  </script>


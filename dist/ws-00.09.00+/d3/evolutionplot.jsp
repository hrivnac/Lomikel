<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel Evolution Plot -->
<!-- @author Julius.Hrivnac@cern.ch -->

<%@ page import="org.apache.log4j.Logger" %>

<%@ page errorPage="../ExceptionHandler.jsp" %>

<%! static Logger log = Logger.getLogger(org.apache.jsp.d3.evolutionplot_jsp.class); %>
   
<link href="evolutionplot.css" rel="stylesheet" type="text/css"/>
<script src="../d3-v6.0.0/d3.js"></script>

<jsp:useBean id="h2table"    class="com.Lomikel.WebService.HBase2Table"    scope="session"/>
<jsp:useBean id="repository" class="com.Lomikel.WebService.DataRepository" scope="session"/>

<div id="evolution_area"></div>

<%
  String data     = request.getParameter("data");
  String dataName = request.getParameter("dataName");
  String name     = request.getParameter("name");
  String y        = request.getParameter("y");
  String z        = request.getParameter("z");
  String s        = request.getParameter("s");
  // data supplied as JSON string
  if (data != null && !data.trim().equals("")) {
    }
  // data supplied via DataRepository
  else if (dataName != null && !dataName.trim().equals("")) {
    data = repository.get(dataName);
    }
  // data supplied via HBase2Table
  else {
   data = h2table.xyz(null, y, z, s, true);
   }
  // no data found, use demo data
  if (data == null || data.trim().equals("") || data.trim().equals("[]")) {
    data = "[{'t':1598256123629, 'y':-20, 'g':0}, {'t':1598256123829, 'y':90, 'g':0}, {'t':1598256123729, 'y':50, 'g':1}, {'t':1598256123929, 'y':30, 'g':1}]";
    }
  // Variable names
  if (y == null) {
    y = "y";
    }
  if (z == null) {
    z = "z";
    }
  if (name == null) {
    if (dataName != null) {
      name = dataName;
      }
    else {
      name = "";
      }
    }
  if (s != null) {
    name += "(colors from " + s + ")";
    }
  %>

<script type="text/javascript" src="evolutionplot.js"></script>
  
<script>
  showEvolutionPlot("<%=data%>", "<%=name%>", "<%=y%>", "<%=z%>");
  </script>

  
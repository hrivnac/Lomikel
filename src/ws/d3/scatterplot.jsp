<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel Scatter Plot -->
<!-- @author Julius.Hrivnac@cern.ch -->

<%@ page import="org.apache.log4j.Logger" %>

<%@ page errorPage="../ExceptionHandler.jsp" %>

<%! static Logger log = Logger.getLogger(org.apache.jsp.d3.scatterplot_jsp.class); %>
   
<link href="scatterplot.css" rel="stylesheet" type="text/css"/>
<script src="../d3-v6.0.0/d3.js"></script>

<jsp:useBean id="h2table"    class="com.Lomikel.WebService.HBase2Table"    scope="session"/>
<jsp:useBean id="repository" class="com.Lomikel.WebService.DataRepository" scope="session"/>

<div id="scatter_area"></div>

<%
  String data     = request.getParameter("data");
  String dataName = request.getParameter("dataName");
  String name     = request.getParameter("name");
  String x        = request.getParameter("x");
  String y        = request.getParameter("y");
  String z        = request.getParameter("z");
  // data supplied as JSON string
  if (data != null && !data.trim().equals("")) {
    }
  // data supplied via DataRepository
  else if (dataName != null && !dataName.trim().equals("")) {
    data = repository.get(dataName);
    }
  // data supplied via HBase2Table
  else {
   data = h2table.xyz(x, y, z);
   }
  // no data found, use demo data
  if (data == null || data.trim().equals("") || data.trim().equals("[]")) {
    data = "[{'x':10, 'y':-20, 'z':5, 'g':0}, {'x':60, 'y':90, 'z':6, 'g':0}, {'x':80, 'y':50, 'z':7,'g':1}, {'x':60, 'y':30, 'g':1}]";
    }
   log.info(data);
  // Variable names
  if (x == null) {
    x = "x";
    }
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
  name += " (" + x + " * " + y + " * " + z + ")";
  x += " →";
  y =  "↑ " + y;
  %>

<script type="text/javascript" src="scatterplot.js"></script>
  
<script>
  showEvolutionPlot("<%=data%>", "<%=name%>", "<%=x%>", "<%=y%>", "<%=z%>");
  </script>

  
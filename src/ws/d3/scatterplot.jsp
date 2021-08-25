<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel Scatter Plot -->
<!-- @author Julius.Hrivnac@cern.ch -->

<%@ page import="org.apache.log4j.Logger" %>

<%@ page errorPage="../ExceptionHandler.jsp" %>

<%! static Logger log = Logger.getLogger(org.apache.jsp.d3.scatterplot_jsp.class); %>

<jsp:useBean id="repository" class="com.Lomikel.WebService.StringRepository" scope="session"/>
   
<link href="scatterplot.css" rel="stylesheet" type="text/css"/>
<script src="../d3-v6.0.0/d3.js" type="text/javascript"></script>

<div id="scatter_area"></div>

<%
  String name  = request.getParameter("name");
  String url   = request.getParameter("url");
  String x     = request.getParameter("x");
  String y     = request.getParameter("y");
  String z     = request.getParameter("z");
  String s     = request.getParameter("s");
  String data  = request.getParameter("data");
  %>
  
<script src="actions.js"     type="text/javascript"></script>
<script src="scatterplot.js" type="text/javascript"></script>
  
<script type="text/javascript">
  showScatterPlot(<%=data%>, "<%=name%>", "<%=x%>", "<%=y%>", "<%=z%>", "<%=s%>", "<%=url%>");
  </script>

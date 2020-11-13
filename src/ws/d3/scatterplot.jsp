<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel Scatter Plot -->
<!-- @author Julius.Hrivnac@cern.ch -->

<%@ page errorPage="ExceptionHandler.jsp" %>

<script src="../d3-v6.0.0/d3.js"></script>

<jsp:useBean id="repository" class="com.Lomikel.WebService.DataRepository" scope="session"/>

<div id="scatter_area"></div>

<%
  String name = request.getParameter("data");
  String x    = request.getParameter("x");
  String y    = request.getParameter("y");
  String data = null;
  if (name != null && !name.trim().equals("")) {
    data = repository.get(name);
    }
  if (data == null) {
    data = "[{'x':10, 'y':20, 'z':5}, {'x':40, 'y':90, 'z':6}, {'x':80, 'y':50, 'z':7}]";
    }
  if (x == null) {
    x = "x";
    }
  if (y == null) {
    y = "y";
    }
  %>

<script type="text/javascript" src="scatterplot.js"></script>
  
<script>
  showScatterPlot("<%=data%>", "<%=name%>", "<%=x%>", "<%=y%>");
  </script>

  
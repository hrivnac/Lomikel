<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel Correlogram -->
<!-- @author Julius.Hrivnac@cern.ch -->

<%@ page import="org.apache.log4j.Logger" %>

<%@ page errorPage="../ExceptionHandler.jsp" %>

<%! static Logger log = Logger.getLogger(org.apache.jsp.d3.correlogram_jsp.class); %>

<link href="correlogram.css" rel="stylesheet" type="text/css"/>
<script src="../d3-v6.0.0/d3.js" type="text/javascript"></script>

<div id="corr_area"></div>

<%
  String tdata = request.getParameter("tdata");
  %>
  
<script src="actions.js"     type="text/javascript"></script>
<script src="correlogram.js" type="text/javascript"></script>
  
<script type="text/javascript">
  showCorrelogram(<%=tdata%>);
  </script>
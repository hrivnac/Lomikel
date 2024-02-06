<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel Tabs -->
<!-- @author Julius.Hrivnac@cern.ch -->

<%@ page errorPage="ExceptionHandler.jsp" %>

<%@include file="Result.jsp" %>
<%@include file="Table.jsp" %>
<%@include file="Plot.jsp" %>
<%@include file="Image.jsp" %>
<%@include file="SkyView.jsp" %>

<script type="text/javascript">
  loadPane("feedback", "Feedback.jsp");
  loadPane("top",      "Top.jsp");
  loadPane("graph",    "GraphView.jsp");
  loadPane("image",    "Image.jsp");
  loadPane("plot",     "Plot.jsp");
  loadPane("result",   "Result.jsp");
  showTab('result');
  </script>

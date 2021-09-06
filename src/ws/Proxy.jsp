<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel Proxy -->
<!-- @author Julius.Hrivnac@cern.ch  -->
<!-- TBD: parametrise -->
<!-- TBD: notify -->

<%@ page trimDirectiveWhitespaces="true"  %>
<%@ page errorPage="ExceptionHandler.jsp" %>

<%@ page import="com.Lomikel.Utils.SmallHttpClient" %>
<%@ page import="org.apache.log4j.Logger" %>

<%! static Logger log = Logger.getLogger(Proxy_jsp.class); %>

<%
  String query = request.getQueryString();
  String server = request.getParameter("server");
  String answer = SmallHttpClient.get(server + "?" + query);
  answer = answer.trim();
  out.print(answer);
  %>

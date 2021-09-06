<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<%@ page trimDirectiveWhitespaces="true"  %>
<%@ page errorPage="ExceptionHandler.jsp" %>

<%@ page import="java.net.URLDecoder" %>
<%@ page import="com.Lomikel.Utils.SmallHttpClient" %>
<%@ page import="org.apache.log4j.Logger" %>

<%! static Logger log = Logger.getLogger(Proxy_jsp.class); %>

<%
  String query = request.getQueryString();
  String server = request.getParameter("server");
  String answer = SmallHttpClient.get(server + "?" + query.replaceAll("%22", "'").replaceAll("%27", "\"").replaceAll("%20", " "));
  answer = answer.trim();
  out.print(answer);
  %>

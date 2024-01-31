<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<%@ page trimDirectiveWhitespaces="true"  %>
<%@ page errorPage="ExceptionHandler.jsp" %>

<%@ page import="com.Lomikel.Utils.SmallHttpClient" %>
<%@ page import="org.apache.log4j.Logger" %>

<%! static Logger log = Logger.getLogger(FinkPortalProxy_jsp.class); %>

<%
  String query = request.getQueryString();
  String server = "http://fink-portal.org";
  String answer = SmallHttpClient.get(server + "?" + query);
  answer = answer.trim();
  out.print(answer);
  %>

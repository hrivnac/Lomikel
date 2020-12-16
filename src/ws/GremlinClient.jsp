<%@ page language="java" contentType="application/json; charset=UTF-8" pageEncoding="UTF-8"%>

<%@ page import="com.Lomikel.Januser.GremlinClient" %>

<%@ page import="org.apache.log4j.Logger" %>

<%
  String host = request.getParameter("host");
  String port = request.getParameter("port");
  String req  = request.getParameter("request");
  GremlinClient gc = new GremlinClient(host, new Integer(port));
  String output = gc.toJSON(gc.interpret(req));
  %>
<%=output%>
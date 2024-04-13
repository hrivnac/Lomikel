<%@ page language="java" contentType="application/json; charset=UTF-8" pageEncoding="UTF-8"%>

<%@ page import="com.Lomikel.Januser.StringGremlinClient" %>
<%@ page import="java.net.URLDecoder" %>

<%@ page import="org.apache.logging.log4j.Logger" %>
<%@ page import="org.apache.logging.log4j.LogManager" %>

<%! static Logger log = LogManager.getLogger(GremlinClient_jsp.class); %>

<%
  String host = request.getParameter("host");
  String port = request.getParameter("port");
  String req  = request.getParameter("request");
  StringGremlinClient gc = new StringGremlinClient(host, new Integer(port));
  req = URLDecoder.decode(req, "UTF-8");
  String output = gc.interpret2JSON(req);
  log.info("Interpreting: " + req + " => " + output);
  gc.close();
  %>
<%=output%>



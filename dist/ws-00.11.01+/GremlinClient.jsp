<%@ page language="java" contentType="application/json; charset=UTF-8" pageEncoding="UTF-8"%>

<%@ page import="com.Lomikel.Januser.GremlinClient" %>

<%@ page import="java.net.URLDecoder" %>

<%@ page import="org.apache.log4j.Logger" %>

<%! static Logger log = Logger.getLogger(GremlinClient_jsp.class); %>

<%
  String host = request.getParameter("host");
  String port = request.getParameter("port");
  String req  = request.getParameter("request");
  GremlinClient gc = new GremlinClient(host, new Integer(port));
  req = URLDecoder.decode(req, "UTF-8");
  String output = gc.interpret2JSON(req);
  log.info("Interpreting: " + req + " => " + output);
  %>
<%=output%>



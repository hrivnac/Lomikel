<%@ page language="java" contentType="text/plain"%>

<%
  response.setContentType("application/json");
  response.setHeader("Content-Disposition", "inline");
  %>

<%@ page trimDirectiveWhitespaces="true" %>

<%@ page import="com.Lomikel.Utils.Init" %>
<%@ page import="com.Lomikel.Januser.JanusClient" %>
<%@ page import="com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG" %>

<%@ page import="org.apache.logging.log4j.Logger" %>
<%@ page import="org.apache.logging.log4j.LogManager" %>
   
<%
  String classifier = request.getParameter("classifier");
  if (classifier == null || classifier.isEmpty()) {
    classifier = "";
    }
        
  Init.initWS("OverlapsWS");

  JanusClient jc = new JanusClient("157.136.250.219", 2183, "janusgraph");
  FinkGremlinRecipiesG gr = new FinkGremlinRecipiesG(jc);
  String data = gr.overlaps(classifier);
  out.print(data);
  %>

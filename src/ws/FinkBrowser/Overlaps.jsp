<%@ page language="java" contentType="application/json"%>

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
  String survey     = request.getParameter("survey");
  String janusip = "";
  if (classifier == null || classifier.isEmpty()) {
    classifier = "";
    }
  if (survey == null || survey.isEmpty()) {
    survey = "ZTF";
    janusip = "157.136.250.219";
    }
  else if (survey.equals("LLST")) {
    janusip = "134.158.243.144";
    }
        
  Init.initWS("OverlapsWS");

  JanusClient jc = new JanusClient(janusip, 2183, "janusgraph1");
  FinkGremlinRecipiesG gr = new FinkGremlinRecipiesG(jc);
  String data = gr.overlaps2JSON(classifier);
  out.print(data);
  %>

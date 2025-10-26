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
  String objectId   = request.getParameter("objectId");
  String classifier = request.getParameter("classifier");
  String alg        = request.getParameter("alg");
  String nmax       = request.getParameter("nmax");
  String climit     = request.getParameter("climit");
  if (objectId == null || objectId.isEmpty()) {
    objectId = "ZTF20aachcvz"; // demo
    }
  if (classifier == null || classifier.isEmpty()) {
    classifier = "FINK";
    }
  if (alg == null || alg.isEmpty()) {
    alg = "JensenShannon";
    }
  if (nmax == null || nmax.isEmpty()) {
    nmax = "5";
    }
   if (climit == null || climit.isEmpty()) {
    climit = "0.0";
    }
        
  Init.initWS("NeighborhoodWS");

  JanusClient jc = new JanusClient("157.136.250.219", 2183, "janusgraph");
  FinkGremlinRecipiesG gr = new FinkGremlinRecipiesG(jc);
  String data = gr.objectNeighborhood2JSON(objectId, classifier, alg, nmax, climit);
  out.print(data);
  %>

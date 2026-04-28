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
  String survey       = request.getParameter("survey");
  String objectId     = request.getParameter("objectId");
  String classifier   = request.getParameter("classifier");
  String reclassifier = request.getParameter("reclassifier");
  String metric       = request.getParameter("metric");
  String nmax         = request.getParameter("nmax");
  String climit       = request.getParameter("climit");
  String janusip = "";
  // TBD: check for impossible combinations (survey and classifier,...)
  if (survey == null || survey.isEmpty() || survey.equals("LSST")) {
    survey = "LSST";
    janusip = "134.158.243.144";
    }
  else if (survey.equals("ZTF")) {
    janusip = "157.136.250.219";
    }
  else { // TBD: make demo LSST
    survey = "ZTF"; // demo
    janusip = "157.136.250.219";
    }
  if (objectId == null || objectId.isEmpty()) {
    objectId = "ZTF20aachcvz"; // demo
    }
  if (classifier == null || classifier.isEmpty()) {
    classifier = "FINK";
    }
  if (reclassifier == null || reclassifier.isEmpty() || reclassifier.equals("none")) {
    reclassifier = null;
    }
  if (metric == null || metric.isEmpty()) {
    metric = "JensenShannon";
    }
  if (nmax == null || nmax.isEmpty()) {
    nmax = "5";
    }
   if (climit == null || climit.isEmpty()) {
    climit = "0.0";
    }
        
  Init.initWS("NeighborhoodWS");

  JanusClient jc = new JanusClient(janusip, 2183, "janusgraph1");
  FinkGremlinRecipiesG gr = new FinkGremlinRecipiesG(jc);
  String data = gr.objectNeighborhood2JSON(objectId, classifier, reclassifier, Double.parseDouble(nmax), metric, Double.parseDouble(climit));
  out.print(data);
  %>

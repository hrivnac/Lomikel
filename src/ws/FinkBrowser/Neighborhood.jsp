<%@ page language="java" contentType="application/json"%>

<%
  response.setContentType("application/json");
  response.setHeader("Content-Disposition", "inline");
  %>

<%@ page trimDirectiveWhitespaces="true" %>

<%@ page import="com.Lomikel.WebService.PropertiesProcessor" %>
<%@ page import="com.Lomikel.Januser.JanusClient" %>
<%@ page import="com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG" %>

<%@ page import="org.json.JSONObject" %>
<%@ page import="org.json.JSONArray" %>

<%@ page import="java.util.Map" %>

<%@ page import="org.apache.logging.log4j.Logger" %>
<%@ page import="org.apache.logging.log4j.LogManager" %>

<%! static Logger log = LogManager.getLogger(org.apache.jsp.Neighborhood_jsp.class); %>

<jsp:useBean id="repository" class="com.Lomikel.WebService.StringRepository" scope="session"/>
   
 
<%
  String objectId   = request.getParameter("objectId");
  String classifier = request.getParameter("classifier");
  String alg        = request.getParameter("alg");
  String nmax       = request.getParameter("nmax");
  String climit     = request.getParameter("climit");
  // TBD: allClasses
  %>
<%@include file="../PropertiesProcessor.jsp"%>
<%
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
   
  JanusClient jc = new JanusClient("157.136.250.219", 2183, "janusgraph");
  FinkGremlinRecipiesG gr = new FinkGremlinRecipiesG(jc);

  JSONObject objectClassification = new JSONObject();
  for (Map<String, String> m : gr.classification(objectId, classifier)) {
    objectClassification.put(m.get("class"), m.get("weight"));
    }
    
  JSONObject data = new JSONObject();
  JSONObject neighbor;
  JSONObject classes;
  String noid;
  for (Map.Entry<Map.Entry<String, Double>, Map<String, Double>> m : gr.objectNeighborhood(objectId,
                                                                                           classifier,
                                                                                           Double.parseDouble(nmax),
                                                                                           alg,
                                                                                           Double.parseDouble(climit)).entrySet()) {
    classes = new JSONObject();
    for (Map.Entry<String, Double> e : m.getValue().entrySet()) {
      classes.put(e.getKey(), e.getValue());
      }
    neighbor = new JSONObject();
    noid = m.getKey().getKey();
    neighbor.put("distance", m.getKey().getValue());
    neighbor.put("classes", classes);
    data.put(noid, neighbor);
    }
  out.print(data);
  %>

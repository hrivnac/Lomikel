<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel Scatter Plot -->
<!-- @author Julius.Hrivnac@cern.ch -->

<%@ page import="com.Lomikel.WebService.PropertiesProcessor" %>
<%@ page import="com.Lomikel.Januser.JanusClient" %>
<%@ page import="com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG" %>

<%@ page import="org.json.JSONObject" %>
<%@ page import="org.json.JSONArray" %>

<%@ page import="java.util.Map" %>

<%@ page import="org.apache.logging.log4j.Logger" %>
<%@ page import="org.apache.logging.log4j.LogManager" %>

<%@ page errorPage="../ExceptionHandler.jsp" %>

<%! static Logger log = LogManager.getLogger(org.apache.jsp.d3.neighborhood_jsp.class); %>

<jsp:useBean id="repository" class="com.Lomikel.WebService.StringRepository" scope="session"/>
   
<link href="neighborhood.css" rel="stylesheet" type="text/css"/>
<script src="../d3-v6.0.0/d3.js" type="text/javascript"></script>
<!--script src="https://d3js.org/d3.v6.min.js"></script-->
 
<div id="viz"></div>
<div id="tooltip"</div>
<button onclick="resetZoom()">Reset Zoom</button>

<%
  String sourceId   = request.getParameter("sourceId");
  String classifier = request.getParameter("classifier");
  String alg        = request.getParameter("alg");
  String nmax       = request.getParameter("nmax");
  String climit     = request.getParameter("climit");
  %>
<%@include file="../PropertiesProcessor.jsp"%>
<%
  if (classifier == null || classifier.isEmpty()) {
    classifier = "FINK_PORTAL";
    }
  if (alg == null || alg.isEmpty()) {
    alg = "JensenShannon";
    }
  if (nmax == null || nmax.isEmpty()) {
    nmax = "5";
    }
   if (climit == null || climit.isEmpty()) {
    climit = "0.2";
    }
   
  JanusClient jc = new JanusClient("157.136.250.219", 2183, "janusgraph");
  FinkGremlinRecipiesG gr = new FinkGremlinRecipiesG(jc);

  JSONObject sourceClassification = new JSONObject();
  for (Map<String, String> m : gr.classification(sourceId, classifier)) {
    sourceClassification.put(m.get("class"), m.get("weight"));
    }
    
  JSONObject data = new JSONObject();
  JSONObject neighbor;
  JSONObject classes;
  String noid;
  for (Map.Entry<Map.Entry<String, Double>, Map<String, Double>> m : gr.sourceNeighborhood(sourceId,
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

  request.setAttribute("sourceClassJson", sourceClassification.toString());
  request.setAttribute("data", data.toString());
  %>

<script src="actions.js"     type="text/javascript"></script>
<script src="neighborhood.js" type="text/javascript"></script>

<script type="text/javascript">
  showNeighbors(<%=data%>, "<%=sourceId%>", <%=sourceClassification%>);
  </script>

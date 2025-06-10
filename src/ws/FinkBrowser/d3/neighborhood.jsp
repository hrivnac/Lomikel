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
 
<div id="viz"></div>
<div id="tooltip"</div>

<%
  String sourceId   = request.getParameter("sourceId");
  String classifier = request.getParameter("classifier");
  String alg        = request.getParameter("alg");
  String nmax       = request.getParameter("nmax");
  %>
<%@include file="../PropertiesProcessor.jsp"%>
<%
  if (classifier == null || classifier.isEmpty()) {
    classifier = "FINK_PORTAL";
    }
  if (alg == null || alg.isEmpty()) {
    alg = "1";
    }
  if (nmax == null || nmax.isEmpty()) {
    nmax = "5";
    }
    
  JanusClient jc = new JanusClient("157.136.250.219", 2183, "janusgraph");
  FinkGremlinRecipiesG gr = new FinkGremlinRecipiesG(jc);

  JSONObject data = new JSONObject();

  JSONObject neighbor1 = new JSONObject();
  neighbor1.put("distance", 0.0022675736961451087);

  JSONObject classes1 = new JSONObject();
  classes1.put("YSO_Candidate", 0.8571);
  classes1.put("SN candidate", 0.1429);
  neighbor1.put("classes", classes1);

  data.put("ZTF19actbknb", neighbor1);

  JSONObject neighbor2 = new JSONObject();
  neighbor2.put("distance", 0.03628117913832199);

  JSONObject classes2 = new JSONObject();
  classes2.put("Radio", 0.4707);
  classes2.put("YSO_Candidate", 0.0608);
  neighbor2.put("classes", classes2);

  data.put("ZTF19actfogx", neighbor2);

  //String sourceId = "ZTF23abdlxeb";
  
  JSONObject sourceClassification = new JSONObject();
  
  for (Map<String, String> m : gr.classification(sourceId, classifier)) {
    sourceClassification.put(m.get("class"), m.get("weight"));
    }

  //sourceClassification.put("YSO_Candidate", 0.8333);
  //sourceClassification.put("SN candidate", 0.1667);

  request.setAttribute("data", data.toString());
  //request.setAttribute("sourceId", sourceId);
  request.setAttribute("sourceClassJson", sourceClassification.toString());
  %>

<script src="actions.js"     type="text/javascript"></script>
<script src="neighborhood.js" type="text/javascript"></script>

<script type="text/javascript">
  showNeighbors(<%=data%>, "<%=sourceId%>", <%=sourceClassification%>);
  </script>

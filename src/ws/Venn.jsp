<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel Venn -->
<!-- @author Julius.Hrivnac@cern.ch  -->

<%@ page import="com.Lomikel.WebService.PropertiesProcessor" %>

<%@ page import="org.json.JSONObject" %>

<%@ page import="java.util.Set" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Calendar" %>
<%@ page import="java.util.GregorianCalendar" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.SimpleDateFormat" %>

<%@ page import="org.apache.logging.log4j.Logger" %>
<%@ page import="org.apache.logging.log4j.LogManager" %>

<!--%@ page errorPage="ExceptionHandler.jsp" %-->

<%! static Logger log = LogManager.getLogger(Venn_jsp.class); %>

<link href="d3/venn.css" rel="stylesheet" type="text/css"/>
<script src="d3-v6.0.0/d3.js"       type="text/javascript"></script>
<script src="venn-v.0.2.14/venn.js" type="text/javascript"></script>

<%
  String id = request.getParameter("id");
  %>
  
<table>
  <tr>
    <td width="40%">
      <div id="venntext">
        </div>
      </td>
    <td>   
      <div id="venn"></div>
      </td>
    </tr>
  </table>
  
<script src="d3/venn.js" type="text/javascript"></script>
  
<script type="text/javascript">
  id = "<%=id%>";
  edge = findObjectByKey(edges, 'id', id);
  n1  = callGremlinValues(gr + ".E('" + id + "').values('sizeIn').next().toString()")[0];
  n2  = callGremlinValues(gr + ".E('" + id + "').values('sizeOut').next().toString()")[0];
  n12 = callGremlinValues(gr + ".E('" + id + "').values('intersection').next().toString()")[0];
  m1  = callGremlinValues(gr + ".E('" + id + "').outV().values('lbl','cls').toList().toArray().join(':')")[0];
  m2  = callGremlinValues(gr + ".E('" + id + "').inV().values('lbl','cls').toList().toArray().join(':')")[0];
  info = showVenn(n1, n2, n12, m1, m2);
  document.getElementById("venntext").innerHTML = info;  
  </script>

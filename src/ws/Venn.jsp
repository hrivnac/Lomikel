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

<%@ page import="org.apache.log4j.Logger" %>

<!--%@ page errorPage="ExceptionHandler.jsp" %-->
<%! static Logger log = Logger.getLogger(Venn_jsp.class); %>

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
  var edge = findObjectByKey(edges, 'id', id);
  var n12 = edge.value[0];
  vFrom  = callGremlinValues(gr + ".E('" + id + "').outV().values('sourceType').next().toString()" )[0];
  vTo    = callGremlinValues(gr + ".E('" + id + "').inV().values('sourceType').next().toString()" )[0];
  sizeIn   = callGremlinValues(gr + ".E('" + id + "').values('sizeIn').next().toString()" )[0];
  console.log(sizeIn);
  </script>
  <!--
[[id:42ckrd-fyg-4pw5-cso, label:overlaps, IN:[id:16584, label:SourcesOfInterest], OUT:[id:20680, label:SourcesOfInterest], lbl:overlaps,
sizeIn:339360.0, sizeOut:1271089.0, intersection:14842.0]]  
-->
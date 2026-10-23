<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel Venn -->
<!-- @author Julius.Hrivnac@cern.ch  -->

<%@ page import="org.apache.logging.log4j.Logger" %>
<%@ page import="org.apache.logging.log4j.LogManager" %>
<%@ page import="org.json.JSONObject" %>

<%@ page errorPage="ExceptionHandler.jsp" %>

<%! static Logger log = LogManager.getLogger(org.apache.jsp.d3.venn_jsp.class); %>

<link href="<%=request.getContextPath() %>/d3/venn.css?v=vertical-2" rel="stylesheet" type="text/css"/>
<script src="<%=request.getContextPath() %>/d3-v6.0.0/d3.js"       type="text/javascript"></script>
<script src="<%=request.getContextPath() %>/venn-v.0.2.14/venn.js" type="text/javascript"></script>

<%
  long   n1  = Long.valueOf(request.getParameter("n1"));
  long   n2  = Long.valueOf(request.getParameter("n2"));
  long   n12 = Long.valueOf(request.getParameter("n12"));
  String m1  =              request.getParameter("m1");
  String m2  =              request.getParameter("m2");
  if (m1 == null) m1 = "A";
  if (m2 == null) m2 = "B";
  if (m1.equals(m2)) {
    m1 = m1 + "_IN";
    m2 = m2 + "_OUT";
    }
  %>
  
<section class="venn-view" aria-label="Overlap analysis">
  <figure class="venn-diagram">
    <div id="venn"></div>
    <figcaption>Area represents the number of objects. The overlap is shared by both sets.</figcaption>
  </figure>
  <div id="venntext"></div>
</section>
  
<script src="<%=request.getContextPath() %>/d3/venn.js?v=vertical-2" type="text/javascript"></script>

<script type="text/javascript">
  info = showVenn(<%=n1%>, <%=n2%>, <%=n12%>, <%=JSONObject.quote(m1).replace("<", "\\u003c")%>, <%=JSONObject.quote(m2).replace("<", "\\u003c")%>);
  document.getElementById("venntext").innerHTML = info;
  </script>

 

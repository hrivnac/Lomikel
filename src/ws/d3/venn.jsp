<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel Venn -->
<!-- @author Julius.Hrivnac@cern.ch  -->

<%@ page import="org.apache.logging.log4j.Logger" %>
<%@ page import="org.apache.logging.log4j.LogManager" %>

<%@ page errorPage="ExceptionHandler.jsp" %>

<%! static Logger log = LogManager.getLogger(org.apache.jsp.d3.venn_jsp.class); %>

<link href="d3/venn.css" rel="stylesheet" type="text/css"/>
<script src="d3-v6.0.0/d3.js"       type="text/javascript"></script>
<script src="venn-v.0.2.14/venn.js" type="text/javascript"></script>

<%
  long   n1  = Long.valueOf(request.getParameter("n1"));
  long   n2  = Long.valueOf(request.getParameter("n2"));
  long   n12 = Long.valueOf(request.getParameter("n12"));
  String m1  =              request.getParameter("m1");
  String m2  =              request.getParameter("m2");
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
  info = showVenn(<%=n1%>, <%=n2%>, <%=n12%>, <%=m1%>, <%=m2%>);
  document.getElementById("venntext").innerHTML = info;
  </script>

 

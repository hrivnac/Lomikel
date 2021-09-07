<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel Venn -->
<!-- @author Julius.Hrivnac@cern.ch  -->

<%@ page import="org.apache.log4j.Logger" %>

<%@ page errorPage="ExceptionHandler.jsp" %>

<%! static Logger log = Logger.getLogger(org.apache.jsp.d3.venn_jsp.class); %>

<link href="d3/venn.css" rel="stylesheet" type="text/css"/>
<script src="d3-v6.0.0/d3.js"       type="text/javascript"></script>
<script src="venn-v.0.2.14/venn.js" type="text/javascript"></script>

<%
  // TBD: add datasets names
  long n1    = Long.valueOf(request.getParameter("n1"));
  long n2    = Long.valueOf(request.getParameter("n2"));
  long n12   = Long.valueOf(request.getParameter("n12"));
  String p1  = String.format("%2.2f", 100.0 * ((double)n1  / ((double)n1 + (double)n2 - (double)n12)));
  String p2  = String.format("%2.2f", 100.0 * ((double)n2  / ((double)n1 + (double)n2 - (double)n12)));
  String p12 = String.format("%2.2f", 100.0 * ((double)n12 / ((double)n1 + (double)n2 - (double)n12)));
  String q1  = String.format("%2.2f", 100.0 * (((double)n1 -(double)n12) / ((double)n1 + (double)n2 - (double)n12)));
  String q2  = String.format("%2.2f", 100.0 * (((double)n2 -(double)n12) / ((double)n1 + (double)n2 - (double)n12)));
  %>
  
<table>
  <tr>
    <td width="40%">
      <div id="text">
        <b>A =&gt; B</b><hr/>
        <table>
          <tr><td><b>A</b>    </td><td><%=n1%>        </td><td>(<%=p1%>%) </td></tr>
          <tr><td><b>B</b>    </td><td><%=n2%>        </td><td>(<%=p2%>%) </td></tr>
          <tr><td><b>A^B</b>  </td><td><%=n12%>       </td><td>(<%=p12%>%)</td></tr>
          <tr><td><b>A-A^B</b></td><td><%=n1-n12%>    </td><td>(<%=q1%>%) </td></tr>
          <tr><td><b>B-A^B</b></td><td><%=n2-n12%>    </td><td>(<%=q2%>%) </td></tr>
          <tr><td><b>AvB</b>  </td><td><%=n1+n2-n12%> </td><td>           </td></tr>
          </table>
        </div>
      </td>
    <td>   
      <div id="venn"></div>
      </td>
    </tr>
  </table>
  
<script src="d3/venn.js" type="text/javascript"></script>

<script type="text/javascript">
  showVenn(<%=n1%>, <%=n2%>, <%=n12%>);
  </script>

 

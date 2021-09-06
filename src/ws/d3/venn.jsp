<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Atlas Event Index Venn -->
<!-- @author Julius.Hrivnac@cern.ch  -->

<!--%@ page errorPage="ExceptionHandler.jsp" %-->

<head>
  <style type="text/css">
    ul {
      list-style-type: none;
      }
    </style>
  </head>

<body bgcolor="#ffffff">
  <%
    // TBD: add datasets names
    String[] n = request.getParameter("overlaps").split(",");
    long n1    = Long.valueOf(n[0]);
    long n2    = Long.valueOf(n[1]);
    long n12   = Long.valueOf(n[2]);
    String s1  = n[3];
    String s2  = n[4];
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
          <b>A</b>&nbsp;=&nbsp;<%=s1%><br/>
          <b>B</b>&nbsp;=&nbsp;<%=s2%><hr/>
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
  </body>
<script type="text/javascript" src="d3.5.5.0/d3.min.js"></script>
<script type="text/javascript" src="venn.js-master/venn.js"></script>
<script type="text/javascript">
  var sets = [{sets:['A'],      size:<%=n1%>},
              {sets:['B'],      size:<%=n2%>},
              {sets:['A', 'B'], size:<%=n12%>}];
  var chart = venn.VennDiagram();
  d3.select("#venn").datum(sets).call(chart);
  </script>

 

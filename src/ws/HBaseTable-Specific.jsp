<%  
  String limitS1    = request.getParameter("limit");
  int limit1 = (limitS1 == null || limitS1.trim().equals("")) ? 0 : Integer.parseInt(limitS1);
  %>

<table>
  <tr><td><b>Limit:</b></td>
      <td><input type="number" id="limit1" size="5" value="<%=limit1%>"></td>
      <td><small>int</small></td></tr>
  <tr><td colspan="3"><button onclick="searchSpecific()">Specific Search</button></td></tr>
  </table>

<%
  filterSpecific = null;
  msg = "showing only <b>" + limit1 + "</b> rows<br/>";
  %>
  
<script>
  async function searchSpecific() {
  var hbase   = document.getElementById("hbase"  ).value;
  var htable  = document.getElementById("htable" ).value;
  var limit1  = document.getElementById("limit1" ).value;
  var query = "hbase=" + hbase + "&htable=" + htable + "&limit=" + limit1;
  $("#hbasetable").load("HBaseTable.jsp?" + query);
  }
  </script>
  

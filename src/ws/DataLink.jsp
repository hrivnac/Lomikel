<!-- TBD: refactor with GraphView.js:describeNode(...) -->
<%
  String id = request.getParameter("id");
  %>
<script type="text/javascript">
  id  = <%=id%>;
  txt = callGremlinValues("LomikelServer.getDataLink(" + gr + ".V('" + id + "').next())");
  document.getElementById("result").innerHTML += "<pre>" + JSON.stringify(txt, null, "\t") + "</pre>";
  </script>

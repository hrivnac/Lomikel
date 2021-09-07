<!-- TBD: refactor with GraphView.js:describeNode(...) -->
<%
  String id = request.getParameter("id");
  %>
<script type="text/javascript">
  id = "<%=id%>";
  var edge = findObjectByKey(edges, 'id', id);
  txt = "<b>" + edge.title + "</b>" +
	      "<hr/><pre>" +
	      callGremlinValues(gr + ".E('" + id + "').valueMap().toList().toString().replace(', ', '<br/>').replace(']', '').replace('[', '')") +
	      "</pre>";
  document.getElementById("result").innerHTML += txt;
  </script>

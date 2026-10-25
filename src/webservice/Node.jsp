<!-- TBD: refactor with GraphView.js:describeNode(...) -->
<%
  String id = request.getParameter("id");
  %>
<script type="text/javascript">
  id = <%=id%>;
  var node = findObjectByKey(nodes, 'id', id);
  txt = "<b>" + node.title + "</b>" +
	      "<hr/><pre>" +
	      callGremlinValues(gr + ".V('" + id + "').valueMap().toList().toString().replace(', ', '<br/>').replace(']', '').replace('[', '')") +
	      "</pre>";
  document.getElementById("result").innerHTML += txt;
  </script>

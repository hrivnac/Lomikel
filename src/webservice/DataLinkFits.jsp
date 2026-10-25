<!-- TBD: refactor with GraphView.js:describeNode(...) -->
<%
  String id = request.getParameter("id");
  %>
<script type="text/javascript">
  id = <%=id%>;
  txt = callGremlinValues("LomikelServer.getDataLink(" + gr + ".V('" + id + "').next())").toString().trim();
  document.getElementById("result").innerHTML += "<small>" + txt + "</small><hr/>" 
                                              + "<a href='#' onclick='loadPane(\"image\", \"FITSView.jsp?data=" + encodeURIComponent(txt) + "\", true, \"" + visheight + "px\")'>Show using JS9</a> - "
                                              + "<a target='popup' href='FITSView.jsp?data=" + encodeURIComponent(txt) + "'>Show in JS9</a>";
  </script>

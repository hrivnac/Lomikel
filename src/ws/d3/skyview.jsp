<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel Sky View -->
<!-- @author Julius.Hrivnac@cern.ch -->

<%@ page import="org.apache.log4j.Logger" %>

<%@ page errorPage="../ExceptionHandler.jsp" %>

<%! static Logger log = Logger.getLogger(org.apache.jsp.d3.skyview_jsp.class); %>

<jsp:useBean id="repository" class="com.Lomikel.WebService.StringRepository" scope="session"/>
   
<script src="d3-celestial-0.7.32/lib/d3.min.js"                type="text/javascript"></script>
<script src="d3-celestial-0.7.32/lib/d3.geo.projection.min.js" type="text/javascript"></script>
<script src="d3-celestial-0.7.32/celestial.min.js"             type="text/javascript"></script>
<link href="d3/skyview.css"                    rel="stylesheet" type="text/css"/>
<link href="d3-celestial-0.7.32/celestial.css" rel="stylesheet" type="text/css">

<div style="overflow:hidden;margin:0 auto;">
  <div id="celestial-map"></div>
  </div>
<div id="celestial-form"></div>

<%
  String id = request.getParameter("id");
  %>
  
<script src="d3/actions.js" type="text/javascript"></script>
<script src="d3/skyview.js" type="text/javascript"></script>
  
<script type="text/javascript">
  id = <%=id%>;
  var node = findObjectByKey(nodes, 'id', id);
  var tit = node.title.split(':')[0];
  var data  = "[";
  var firstrow = true;
  var firstval;
  for (var i = 0; i < nodes.length; i++) {
    node1 = nodes[i];
    id1 = node1.id;
    if (node1.title.split(':')[0] == tit) {
	    txt = callGremlinValues(gr + ".V('" + id1 + "').valueMap('ra', 'dec').toList().toString().replace(']', '').replace('[', '')")[0];
      if (!firstrow) {
        data += ",";
        }
      else {
        firstrow = false;
        }
	    data += "{";
	    firstval = true;
      for (t of txt.split(",")) {
        [column, value] = t.trim().split(":");
        if (!firstval) {
          data += ",";
          }
        else {
          firstval = false;
          }
        if (column == "ra") {
          data += "\"x\":\"" + value + "\"";
          }
        else if (column == "dec") {
          data += "\"y\":\"" + value + "\"";
          }
        }
      data += ", \"z\":0, \"k\":\"x\", \"g\":0}";
      }
    }
  data += "]";
  data = JSON.parse(data);
  showSkyView(data, "", "", "", "");
  </script>


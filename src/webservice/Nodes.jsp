<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel Nodes -->
<!-- @author Julius.Hrivnac@cern.ch  -->

<%@ page import="com.Lomikel.WebService.PropertiesProcessor" %>

<%@ page import="org.json.JSONObject" %>

<%@ page import="java.util.Set" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Calendar" %>
<%@ page import="java.util.GregorianCalendar" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.SimpleDateFormat" %>

<%@ page import="org.apache.logging.log4j.Logger" %>
<%@ page import="org.apache.logging.log4j.LogManager" %>

<!--%@ page errorPage="ExceptionHandler.jsp" %-->

<link href="Nodes.css?v=table-1" rel="stylesheet" type="text/css"/>
<script src="TableView.js?v=table-1" type="text/javascript"></script>

<%! static Logger log = LogManager.getLogger(Nodes_jsp.class); %>

<%@include file="Params.jsp" %>

<%
  String id  = request.getParameter("id");
  %>
  
<script type="text/javascript">
  // BUG: cannot have ':' inside values
  id  = <%=id%>;
  var node = findObjectByKey(nodes, 'id', id);
  var tit = node.title.split(':')[0];
  var columns = [];
  var tdata  = "[";
  var firstrow = true;
  var firstval;
  for (var i = 0; i < nodes.length; i++) {
    node1 = nodes[i];
    id1 = node1.id;
    if (node1.title.split(':')[0] == tit) {
	    txt = callGremlinValues(gr + ".V('" + id1 + "').valueMap().next().toString()")[0];	    
	    txt = txt.substring(1, txt.length - 1);
	    txt1 = "";
	    inside = 0;
	    for (var j = 0; j < txt.length; j++) {
	      tj = txt[j]
        if (tj == "[") {
          inside++;
          }
        else if (tj == "]") {
          inside--;
          }
        if (inside > 0 && tj == ",") {
          tj = ";";
          }
        txt1 += tj;
        }
      txt = txt1;
      if (!firstrow) {
        tdata += ",";
        }
      else {
        firstrow = false;
        }
	    tdata += "{";
	    firstval = true;
      columnes = [];
      values   = [];
      for (t of txt.split(",")) {
        [column, value] = t.trim().split(":");
        value = value.substring(1, value.length - 1);
        if (value.includes(";")) {
          valus = value.split(";");
          k = 0;
          for (v of valus) {
            columnes.push(column + k++);
            values.push(v.trim());
            }
          }
        else {
          columnes.push(column);
          values.push(value.trim());
          }
        }
      for (var j = 0; j < columnes.length; j++) {
        c = columnes[j];
        v = values[j];
        if (!columns.includes(c)) {
          columns.push(c);
          }
        if (!firstval) {
          tdata += ",";
          }
        else {
          firstval = false;
          }
        tdata += "\"" + c + "\":\"" + v + "\"";
        }
      tdata += "}";
      }
    }
  tdata += "]";
  tdata = JSON.parse(tdata);
  var header = "<tr>";
  for (var i = 0; i < columns.length; i++) {
    var column = columns[i];
    header += "<th data-field='" + plotTableEscape(column) + "' data-sortable='true' data-visible='true'>" + plotTableEscape(column) + "</th>";
    }
  header += "</tr>";
  </script>
  
<div id="toolbar" class="plot-table-toolbar">
  <div class="plot-table-intro"><strong>Vertices table</strong><span>Expand a row to choose x / y / z / s / k for a plot.</span></div>
  <div class="plot-table-actions">
  <button onClick="w2popup.load({url:'Help-Nodes.html', showMax: true})" title="Table help" aria-label="Table help">
    <img src="images/Help.png" width="10"/>
    </button>
  <button onclick="showScatter('evolution')" style="background-color:#ddddff" title="time dependence of multiple variables">Evolution Plot</button>    
  <button onclick="showScatter('scatter')"   style="background-color:#ddddff" title="scatter plot of multiple variables"   >Scatter Plot</button>    
  <button onclick="showSky()"                style="background-color:#ddddff" title="sky view"                             >Sky View</button>    
  </div>
  </div>
<table id='tbl' class='plot-table-grid'
       data-sortable='true'
       data-search='true'
       data-show-search-button='true'
       data-show-toggle='true'
       data-detail-view='true'
       data-detail-formatter='detailFormatter'
       data-show-button-icons='true'
       data-show-pagination-switch='true'
       data-page-number='1'
       data-page-size='25'
       data-pagination-pre-text='Previous'
       data-pagination-next-text='Next'
       data-show-columns='true'
       data-show-columns-toggle-all='true'
       data-show-columns-search='true'
       xdata-height='600'
       data-resizable='true'
       xxdata-id-field='key'
       xxdata-unique-id='key' 
       data-pagination='true'>
  </table>
  
<script>
  var $table = $('#tbl');
  var table = document.getElementById("tbl");
  var thead = table.createTHead();
  thead.innerHTML = header;
  $(function() { 
    $table.bootstrapTable({data: tdata})
    $table.bootstrapTable('refreshOptions', {classes: 'table table-bordered table-hover table-striped table-sm plot-table-grid'})
    $table.bootstrapTable('refreshOptions', {theadClasses: 'thead-light'})
    $table.bootstrapTable('refreshOptions', {sortable: 'true'})
      })
  function detailFormatter(index, row) {
    return plotTableDetailFormatter(index, row);
    }
  function showScatter(kind) {
    var x = "";
    var y = "";
    var z = "";
    var s = "";
    var k = "";
    var xs = document.getElementsByClassName('x');
    var ys = document.getElementsByClassName('y');
    var zs = document.getElementsByClassName('z');
    var ss = document.getElementsByClassName('s');
    var ks = document.getElementsByClassName('k');
    for (i = 0; i < xs.length; i++) {
      if (xs[i].checked) {
         if (!plotTableHasField(x, plotTableFieldName(xs[i]))) {
           x += plotTableFieldName(xs[i]) + " ";
           }
         }
      }
    for (i = 0; i < ys.length; i++) {
      if (ys[i].checked) {
        if (!plotTableHasField(y, plotTableFieldName(ys[i]))) {
          y += plotTableFieldName(ys[i]) + " ";
          }
        }
      }
    for (i = 0; i < zs.length; i++) {
      if (zs[i].checked) {
        if (!plotTableHasField(z, plotTableFieldName(zs[i]))) {
          z = plotTableFieldName(zs[i]);
          break;
          }
        }
      }
    for (i = 0; i < ss.length; i++) {
      if (ss[i].checked) {
        s = plotTableFieldName(ss[i]);
        break;
        }
      }
    for (i = 0; i < ks.length; i++) {
      if (ks[i].checked) {
        if (!plotTableHasField(k, plotTableFieldName(ks[i]))) {
          k += plotTableFieldName(ks[i]) + " ";
          }
        }
      }
    if (kind == "evolution") {
      if ("<%=timestampField%>".trim() == "") {
        window.alert("timestampField not defined");
        return;
        }
      if (!x && !y) {
        window.alert("x or y - axis should be selected");
        return;
        }
      y = (x + y).trim();
      params = "name=" + tit + "&url=&x=&y=" + y + "&z=" + z + "&s=" + s + "&tdata=[";
      first = true;
      for (i = 0; i < tdata.length; i++) {
        for (yy of y.split(" ")) { 
          if (tdata[i][yy]) {
            if (!first) {
              params += ",";
              }
            else {
              first = false;
              }
            params += "{";
            params += "\"y\":\"" + tdata[i][yy] + "\"";
            params += ",\"g\":\"" + yy + "\"";
            if (tdata[i]['<%=timestampField%>']) {
              params += ",\"t\":\"" + tdata[i]['<%=timestampField%>'] + "\"";
              }
            if (z != "" && tdata[i][z]) {
              params += ",\"z\":\"" + tdata[i][z] + "\"";
              }
            g = yy;
            if (s != "" && tdata[i][s]) {
              g = tdata[i][s] + "/" + g;
              }
            params += ",\"g\":\"" + g + "\"";
            c = ""
            if (k != "") {
              for (kk of k.split(" ")) { 
                if (tdata[i][kk]) {
                  c += kk + "=" + tdata[i][kk] + " "; 
                  }
                }
              params += ",\"k\":\"" + c + "\"";
              }
            params += "}";
            }
          }
        }
      params += "]";
      }
    if (kind == "scatter") {
      if (!x || !y) {
        window.alert("x and y - axis should be selected");
        return;
        }
      params = plotTableScatterURL(tit, x.trim(), y.trim(), z, s, k, tdata);
      }
    loadPane("plot", (kind == "scatter" ? params : "d3/scatterplot.jsp?" + params), true, 400 * 1.2, kind == "scatter");
    }
  function showSky() {
    var z = "";
    var s = "";
    var k = "";
    var zs = document.getElementsByClassName('z');
    var ss = document.getElementsByClassName('s');
    var ks = document.getElementsByClassName('k');
    for (i = 0; i < zs.length; i++) {
      if (zs[i].checked) {
        if (!plotTableHasField(z, plotTableFieldName(zs[i]))) {
          z = plotTableFieldName(zs[i]);
          break;
          }
        }
      }
    for (i = 0; i < ss.length; i++) {
      if (ss[i].checked) {
        s = plotTableFieldName(ss[i]);
        break;
        }
      }
    for (i = 0; i < ks.length; i++) {
      if (ks[i].checked) {
        if (!plotTableHasField(k, plotTableFieldName(ks[i]))) {
          k += plotTableFieldName(ks[i]) + " ";
          }
        }
      }
    var params = "name=" + tit + "&url=";
    if (z) {
      params += "&z=" + z;
      }
    if (s) {
      params += "&s=" + s;
      }
    params += "&tdata=[";
    first = true;
    for (i = 0; i < tdata.length; i++) {
      if (tdata[i]['ra'] && tdata[i]['dec']) {
        if (!first) {
          params += ",";
          }
        else {
          first = false;
          }
        params += "{\"x\":\"" + tdata[i]['ra'] + "\",\"y\":\"" + tdata[i]['dec'] + "\"";
        if (z != "" && tdata[i][z]) {
          params += ",\"z\":\"" + tdata[i][z] + "\"";
          }
        if (s != "" && tdata[i][s]) {
          params += ",\"g\":\"" + tdata[i][s] + "\"";
          }
        if (k != "") {
          c = "";
          for (kk of k.split(" ")) { 
            if (tdata[i][kk]) {
              c += kk + "=" + tdata[i][kk] + " "; 
              }
            }
          params += ",\"k\":\"" + c + "\"";
          }
        params += "}";
        }
      }
    params += "]";
    loadPane("skyview", "d3/skyview.jsp?" + params, true, 600 * 1.2);
    }
  </script>

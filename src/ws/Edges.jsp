<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel Edges -->
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

<%@ page import="org.apache.log4j.Logger" %>

<!--%@ page errorPage="ExceptionHandler.jsp" %-->

<link href="Nodes.css" rel="stylesheet" type="text/css"/>

<%! static Logger log = Logger.getLogger(Edges_jsp.class); %>

<%@include file="Params.jsp" %>

<%
  String id = request.getParameter("id");
  %>
  
<script type="text/javascript">
  id = "<%=id%>";
  var edge = findObjectByKey(edges, 'id', id);
  var tit = edge.title.split(':')[0];
  var columns = ["from", "to"];
  var tdata  = "[";
  var firstrow = true;
  var firstval;
  for (var i = 0; i < edges.length; i++) {
    edge1 = edges[i];
    id1 = edge1.id;
    if (edge1.title.split(':')[0] == tit) {
	    txt = callGremlinValues(gr + ".E('" + id1 + "').valueMap().toList().toString().replace(']', '').replace('[', '')")[0];
      if (!firstrow) {
        tdata += ",";
        }
      else {
        firstrow = false;
        }
	    tdata += "{";
	    firstval = true;
      for (t of txt.split(",")) {
        [column, value] = t.trim().split(":");
        if (!columns.includes(column)) {
          columns.push(column);
          }
        if (!firstval) {
          tdata += ",";
          }
        else {
          firstval = false;
          }
        tdata += "\"" + column + "\":\"" + value + "\"";
        }
      vFrom = callGremlinValues(gr + ".E('" + id1 + "').inV().valueMap('title').toList().toString().replace(']', '').replace('[', '')")[0].replace("title:", "");
      vTo   = callGremlinValues(gr + ".E('" + id1 + "').outV().valueMap('title').toList().toString().replace(']', '').replace('[', '')")[0].replace("title:", "");
      tdata += ",\"from\":\"" + vFrom + "\"";
      tdata += ",\"to\":\""   + vTo   + "\"";
      tdata += "}";
      }
    }
  tdata += "]";
  tdata = JSON.parse(tdata);
  var header = "<tr>";
  for (var i = 0; i < columns.length; i++) {
    var column = columns[i];
    header += "<th data-field='" + column + "' data-sortable='true' data-visible='true' ><b><u>" + column + "</u></b></th>";
    }
  header += "</tr>";
  </script>
  
<div id="toolbar">
  <button onClick="w2popup.load({url:'Help-Nodes.html', showMax: true})" style="position:absolute; right:0">
    <img src="images/Help.png" width="10"/>
    </button>
  <button onclick="showScatter('scatter')"   style="background-color:#ddddff" title="scatter plot of multiple variables"   >Scatter Plot</button>    
  <button onclick="showCorrelogram()"        style="background-color:#ddddff" title="correlogram">Correlogram</button>    
  </div>
<table id='tbl'
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
    $table.bootstrapTable('refreshOptions', {classes: 'table table-bordered table-hover table-striped table-sm'})
    $table.bootstrapTable('refreshOptions', {theadClasses: 'thead-light'})
    $table.bootstrapTable('refreshOptions', {sortable: 'true'})
      })
  function detailFormatter(index, row) {
    var html = []
    $.each(row, function (key, value) {
      html.push(histSelector(key) + '<b>' + key + ':</b> ' + value + '<br/>');
      })
    return html.join('')
    }
  function histSelector(column) {
    return "<input type='checkbox' name='x1_" + column + "' class='x' id='x1_" + column + "'></input><label for='x1_" + column + "' title='var x'   >x</label>&nbsp;" +
           "<input type='checkbox' name='y1_" + column + "' class='y' id='y1_" + column + "'></input><label for='y1_" + column + "' title='var y'   >y</label>&nbsp;" +
           "<input type='checkbox' name='z1_" + column + "' class='z' id='z1_" + column + "'></input><label for='z1_" + column + "' title='var z'   >z</label>&nbsp;" +
           "<input type='checkbox' name='s1_" + column + "' class='s' id='s1_" + column + "'></input><label for='s1_" + column + "' title='selector'>s</label>&nbsp;" +
           "<input type='checkbox' name='k1_" + column + "' class='k' id='k1_" + column + "'></input><label for='k1_" + column + "' title='info'    >k</label>&nbsp;";
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
         if (!x.includes(xs[i].id.substring(3))) { 
           x += xs[i].id.substring(3) + " ";
           }
         }
      }
    for (i = 0; i < ys.length; i++) {
      if (ys[i].checked) {
        if (!y.includes(ys[i].id.substring(3))) { 
          y += ys[i].id.substring(3) + " ";
          }
        }
      }
    for (i = 0; i < zs.length; i++) {
      if (zs[i].checked) {
        if (!z.includes(zs[i].id.substring(3))) { 
          z = zs[i].id.substring(3);
          break;
          }
        }
      }
    for (i = 0; i < ss.length; i++) {
      if (ss[i].checked) {
        s = ss[i].id.substring(3);
        break;
        }
      }
    for (i = 0; i < ks.length; i++) {
      if (ks[i].checked) {
        if (!k.includes(ks[i].id.substring(3))) { 
          k += ks[i].id.substring(3) + " ";
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
      x = x.trim();
      y = y.trim();
      params = "name=" + tit + "&url=&x=" + x + "&y=" + y + "&z=" + z + "&s=" + s + "&tdata=[";
      first = true;
      for (i = 0; i < tdata.length; i++) {
        for (xx of x.split(" ")) {    
          for (yy of y.split(" ")) { 
            if (tdata[i][xx] && tdata[i][yy]) {
              if (!first) {
                params += ",";
                }
              else {
                first = false;
                }
              params += "{\"x\":\"" + tdata[i][xx] + "\",\"y\":\"" + tdata[i][yy] + "\"";
              params += ",\"g\":\"" + xx + "/" + yy + "\"";
              if (z != "" && tdata[i][z]) {
                params += ",\"z\":\"" + tdata[i][z] + "\"";
                }
              g = xx + "/" + yy;
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
        }
      params += "]";
      }  
    loadPane("plot", "d3/scatterplot.jsp?" + params, true, 600 * 1.2);
    }
  function showCorrelogram() {
    var params = "tdata=[";
    first = true;
    for (i = 0; i < tdata.length; i++) {
      if (!first) {
        params += ",";
        }
      else {
        first = false;
        }
        params += "{\"x\":\"" + tdata[i]['from'] + "\",\"y\":\"" + tdata[i]['to'] + "\"";
        params += ",\"value\":\"" + tdata[i]['intersection'] + "\"";
        params += ",\"info\":\"" + tdata[i]['sizeIn'] + "/" + tdata[i]['sizeOut'] + "\"";
        params += "}";        
      }
    params += "]";
    loadPane("plot", "d3/correlogram.jsp?" + params, true, 430 * 1.2);
    }
  </script>
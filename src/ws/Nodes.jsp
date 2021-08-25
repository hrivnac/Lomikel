<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel Nodes -->
<!-- @author Julius.Hrivnac@cern.ch  -->

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

<%! static Logger log = Logger.getLogger(Nodes_jsp.class); %>

<%
  String id = request.getParameter("id");
  %>
  
<script type="text/javascript">
  id = <%=id%>;
  var node = findObjectByKey(nodes, 'id', id);
  var tit = node.title.split(':')[0];
  var columns = [];
  var data  = "[";
  var firstrow = true;
  var firstval;
  for (var i = 0; i < nodes.length; i++) {
    node1 = nodes[i];
    id1 = node1.id;
    if (node1.title.split(':')[0] == tit) {
	    txt = callGremlinValues(gr + ".V('" + id1 + "').valueMap().toList().toString().replace(']', '').replace('[', '')")[0];
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
        if (!columns.includes(column)) {
          columns.push(column);
          }
        if (!firstval) {
          data += ",";
          }
        else {
          firstval = false;
          }
        data += "\"" + column + "\":\"" + value + "\"";
        }
      data += "}";
      }
    }
  data += "]";
  data = JSON.parse(data);
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
  <button onclick="showScatter('evolution')" style="background-color:#ddddff" title="time dependence of multiple variables">Evolution Plot</button>    
  <button onclick="showScatter('scatter')"   style="background-color:#ddddff" title="scatter plot of multiple variables"   >Scatter Plot</button>    
  <button onclick="showSky()"                style="background-color:#ddddff" title="sky view"                             >Sky View</button>    
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
    $table.bootstrapTable({data: data})
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
           "<input type='checkbox' name='s1_" + column + "' class='s' id='s1_" + column + "'></input><label for='s1_" + column + "' title='selector'>s</label>&nbsp;";
    }
  function showScatter(kind) {
    var x = "";
    var y = "";
    var z = "";
    var s = "";
    var xs = document.getElementsByClassName('x');
    var ys = document.getElementsByClassName('y');
    var zs = document.getElementsByClassName('z');
    var ss = document.getElementsByClassName('s');
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
    if (kind == "evolution") {
      if (!x && !y) {
        window.alert("x or y - axis should be selected");
        return;
        }
      }
    if (kind == "scatter") {
      if (!x || !y) {
        window.alert("x and y - axis should be selected");
        return;
        }
      x = x.trim();
      y = y.trim();
      params = "name=" + tit + "&url=&x=" + x + "&y=" + y + "&z=" + z + "&s=" + s + "&data=[";
      first = true;
      for (i = 0; i < data.length; i++) {
        for (xx of x.split(" ")) {    
          for (yy of y.split(" ")) { 
            if (data[i][xx] && data[i][yy]) {
              if (!first) {
                params += ",";
                }
              else {
                first = false;
                }
              params += "{\"x\":\"" + data[i][xx] + "\",\"y\":\"" + data[i][yy] + "\"";
              params += ",\"g\":\"" + xx + "/" + yy + "\"";
              if (z != "" && data[i][z]) {
                params += ",\"z\":\"" + data[i][z] + "\"";
                }
              g = xx + "/" + yy;
              if (s != "" && data[i][s]) {
                g = data[i][s] + "/" + g;
                }
              params += ",\"g\":\"" + g + "\"";
              params += "}";
              }
            }
          }
        }
      params += "]";
      }  
    console.log(params);
    loadPane("plot", "d3/scatterplot.jsp?" + params, true, visheight);
    }
  function showSky() {
    var z = "";
    var s = "";
    var zs = document.getElementsByClassName('z');
    var ss = document.getElementsByClassName('s');
    for (i = 0; i < zs.length; i++) {
      if (zs[i].checked) {
        if (!z.includes(zs[i].id.substring(3))) { 
          z += zs[i].id.substring(3) + " ";
          }
        }
      }
    for (i = 0; i < ss.length; i++) {
      if (ss[i].checked) {
        s = ss[i].id.substring(3);
        break;
        }
      }
    var params = "";
    if (z) {
      params += "&z=" + z;
      }
    if (s) {
      params += "&s=" + s;
      }
    loadPane("skyview", "d3/skyview.jsp?" + params, true, visheight);
    }
  </script>

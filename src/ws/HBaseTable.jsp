<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel HBase Table-->
<!-- @author Julius.Hrivnac@cern.ch  -->

<%@ page import="com.Lomikel.HBaser.HBaseClient" %>
<%@ page import="com.Lomikel.HBaser.HBaseSchema" %>
<%@ page import="com.Lomikel.WebService.HBase2Table" %>
<%@ page import="com.Lomikel.HBaser.BinaryDataRepository" %>
<%@ page import="com.Lomikel.Utils.DateTimeManagement" %>

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

<link href="HBaseTable.css" rel="stylesheet" type="text/css"/>

<%! static Logger log = Logger.getLogger(HBaseTable_jsp.class); %>

<jsp:useBean id="style"   class="com.Lomikel.WebService.Style"            scope="session"/>
<jsp:useBean id="h2table" class="com.Lomikel.WebService.HBase2Table"      scope="session"/>
<jsp:useBean id="memory"  class="com.Lomikel.WebService.HBaseTableMemory" scope="session"/>

<%@include file="Params.jsp" %>

<%
  String s = style.style();
  %>

<div id='hbasetable'>

  <div id="hbaseTableForm" style="width: 100%"></div>
      
  <div id="hbaseResult" style="width: 100%">
    <button onClick="w2popup.load({url:'Help-HBaseGeneric.html', showMax: true})" style="position:absolute; top:0; right:0">
      <img src="images/Help.png" width="10"/>
      </button>

    <%
      String hbase       = request.getParameter("hbase");
      String htable      = request.getParameter("htable");
      String key         = request.getParameter("key");
      String selects     = request.getParameter("selects");
      String filters     = request.getParameter("filters");
      String start       = request.getParameter("start");
      String stop        = request.getParameter("stop");
      String group       = request.getParameter("group");
      String schema      = request.getParameter("schema");
      String limitS      = request.getParameter("limit");
      String formula     = request.getParameter("formula");
      String formulaArgs = request.getParameter("formulaArgs");
      if (hbase  == null || hbase.trim( ).equals("")) {
        hbase = memory.hbase();
        log.info("Trying to reuse memorised hbase: " + hbase);
        }
      if (htable == null || htable.trim().equals("")) {
        htable = memory.htable();
        log.info("Trying to reuse memorised htable: " + htable);
        }
      if (hbase  == null || hbase.trim( ).equals("") ||
          htable == null || htable.trim().equals("")) {
        log.fatal("Cannot connect to " + htable + "@" + hbase);
        }
      memory.setHBase(hbase);
      memory.setHTable(htable);
      String msg = "<b><u>" + htable + "@" + hbase + "</u></b><br/>";
      log.info("Connection to " + htable + "@" + hbase);
      key         = (key         == null || key.equals(         "null")) ? "" : key.trim();
      filters     = (filters     == null || filters.equals(     "null")) ? "" : filters.trim();
      selects     = (selects     == null || selects.equals(     "null")) ? "" : selects.trim();
      start       = (start       == null || start.equals(       "null")) ? "" : start.trim();
      stop        = (stop        == null || stop.equals(        "null")) ? "" : stop.trim();
      group       = (group       == null || group.equals(       "null")) ? "" : group.trim();
      schema      = (schema      == null || schema.equals(      "null")) ? "" : schema.trim();
      limitS      = (limitS      == null || limitS.equals(      "null")) ? "" : limitS.trim();
      formula     = (formula     == null || formula.equals(     "null")) ? "" : formula.trim();
      formulaArgs = (formulaArgs == null || formulaArgs.equals( "null")) ? "" : formulaArgs.trim();
      Map<String, String> filterMap = new HashMap<>();
      if (!key.equals("")) {
        msg += "<b>key</b> is <b>" + key + "</b><br/>";
        }
      else {
        if (!filters.equals("")) {
          String[] term;
          String k;
          String v;
          msg += "<b>searching for</b> " + filters;
          for (String f : filters.split(",")) {
            term = f.split(":");
            k = term[0] + ":" + term[1];
            v = term[2];
            if (filterMap.containsKey(k)) {
              v = filterMap.get(k) + "," + v;
              }
            filterMap.put(k, v);
            }
          }
        msg += "<br/>";
        }
      int limit = 0;
      if (!limitS.equals("") && !limitS.equals("0")) {
        msg += "showing max <b>" + limitS + "</b> results<br/>";
        limit = Integer.valueOf(limitS);
        }
      if (!formula.equals("")) {
        msg += "evaluation formula: " + formula + "[" + formulaArgs + "]<br/>";
        }
      if (formulaArgs.contains("all")) {
        selects = "all";
        }
      if (selects.contains("all")) {
        selects = "*";
        msg += "showing <b>all</b> columns<br/>";
        }
      else if (!selects.equals("")) {
        msg += "showing only columns <b>" + selects + "</b><br/>";
        }
      if (schema.equals("")) {
        schema = memory.schema();
        log.info("Trying to reuse memorised schema: " + schema);
        }
      if (!schema.equals("")) {
        msg += "using schema <b>" + schema + "</b><br/>";
        memory.setSchema(schema);
        }
      if (group.equals("")) {
        group = memory.group();
        log.info("Trying to reuse memorised group: " + group);
        }
      if (!group.equals("")) {
        msg += "using group <b>" + group + "</b><br/>";
        memory.setGroup(group);
        }
      long startL = 0;
      long stopL  = 0;
      if (!start.equals("")) {
        startL = DateTimeManagement.string2time(start, "dd/MM/YYYY HH:mm");
        msg += "since: <b>" + start + "</b> = " + startL + "<br/>";
        }
      if (!stop.equals("")) {
        stopL  = DateTimeManagement.string2time(stop,  "dd/MM/YYYY HH:mm");
        msg += "till: <b>" + stop + "</b> = " + stopL + "<br/>";
        }
      %>
    <%@include file="HBaseClient.jsp"%>
    <%      
      String clientName = h.getClass().getSimpleName();
      h.setLimit(limit);
      if (schema.equals("")) {
        h.connect(htable);
        }
      else {
        h.connect(htable, schema, 0);
        }
      Map<String, Map<String, String>> results = null;
      boolean showTable = true;
      String url = "HBaseTable.jsp?hbase=" + hbase + "&htable=" + htable + "&schema=" + schema + "&group=" + group;
      %>
    <%@include file="CustomRange.jsp"%>
    <%
      out.println(msg);
      %>
    <%@include file="CustomQuery.jsp"%>
    <%
      if (results == null) { // not performed in CustomQuery.jsp
        if (formula != null && !formula.trim().equals("")) {
          h.setEvaluation(formula, formulaArgs);
          }
        results = h.scan(key.equals("") ? null : key,
                         filterMap,
                         selects,
                         startL,
                         stopL,
                         false,
                         false);
        }
      h2table.processTable(results, h.schema(), h.repository());
      h.close();
      String toHide = "";
      if (showTable && !group.equals("")) {
        toHide = h2table.toHide(group);
      %>
    <div id="toolbar">
      <button onClick="w2popup.load({url:'Help-HBaseTable.html', showMax: true})" style="position:absolute; right:0">
        <img src="images/Help.png" width="10"/>
        </button>
      <button id="buttonHide" class="btn btn-secondary" style="background-color:#aaaaff; color:black" title="show only the latest <%=hbaseRowName%>s of each <%=group%>">Latest <%=hbaseRowName%>s</button>
      <button id="buttonShow" class="btn btn-secondary" style="background-color:#aaaaff; color:black" title="show all <%=hbaseRowName%>s of each <%=group%>"            >All <%=hbaseRowName%>s</button>
      <button onclick="showScatter('evolution')"        style="background-color:#ddddff"              title="time dependence of multiple variables"                     >Evolution Plot</button>    
      <button onclick="showScatter('scatter')"          style="background-color:#ddddff"              title="scatter plot of multiple variables"                        >Scatter Plot</button>    
      <button onclick="showSky()"                       style="background-color:#ddddff"              title="sky view"                                                  >Sky View</button>    
      </div>
    <%
      }
    if (showTable) {
      %>
    <table id='table'
           data-sortable='true'
           data-search='true'
           data-show-search-button='true'
           data-show-toggle='true'
           data-detail-view='true'
           data-detail-formatter='detailFormatter'
           data-show-button-icons='true'
           data-show-pagination-switch='true'
           data-page-number='1'
           xdata-page-size='15'
           data-pagination-pre-text='Previous'
           data-pagination-next-text='Next'
           data-show-columns='true'
           data-show-columns-toggle-all='true'
           data-show-columns-search='true'
           xdata-height='600'
           data-resizable='true'
           data-id-field='key'
           data-unique-id='key' 
           data-pagination='true'>
      <thead>
        <tr>
          <th data-field='key' data-sortable='true'>row key</th>
          <%=h2table.thead()%>
          </tr>
        </thead>
      </table>
      </div>
    <%
      }
      %>
    
    </div>

  <script type="text/javascript" src="CustomQuery.js"></script> 
  
  <script type="text/javascript" src="styles/<%=s%>.js"></script>
  
  <script>  
    $(function() {
      if (w2ui.hbaseTableForm) {
        w2ui['hbaseTableForm'].destroy();
        }
      w2utils.settings.dateFormat ='dd/MM/yyyy|h24:mm';
      var hform = { 
        name   : 'hbaseTableForm',
        header : '<%=clientName%>: <%=htable%>@<%=hbase%>',
        url    : 'HBaseTable.jsp',
        fields : [
          {field:'key',     type: 'text',     html: {caption: 'Exact Key',      text : ' (exact search on row keys: key,key,...)',                          attr: 'style="width: 500px"'}},
          {field:'filters', type: 'text',     html: {caption: 'Search Columns', text : ' (columns substring search: family:column:value[:comparator],...)', attr: 'style="width: 500px"'}},
          {field:'selects', type: 'text',     html: {caption: 'Show Columns',   text : ' (columns to show family:column,... or *)',                         attr: 'style="width: 500px"'}},
          {field:'start',   type: 'datetime', html: {caption: 'From',           text : ' (start time)',                                                     attr: 'style="width: 150px"'}},
          {field:'stop',    type: 'datetime', html: {caption: 'Till',           text : ' (end time)',                                                       attr: 'style="width: 150px"'}},
          {field:'limit',   type: 'int',      html: {caption: 'Limit',          text : ' (max number of rows)',                                             attr: 'style="width: 100px"'}}
          ], 
        record : { 
          key     : '<%=key%>',
          filters : '<%=filters%>',
          selects : '<%=selects%>',
          start   : '<%=start%>',
          stop    : '<%=stop%>',
          limit   : <%=limit%>
          },
        actions: {
          Reset: function () {
            this.clear();
            },
          Search: function () {
            var selects = w2ui.hbaseTableForm.record.selects;
            if (selects.includes("*")) {
              selects = "all";
              }
            var request = w2ui.hbaseTableForm.url + "?hbase=<%=hbase%>&htable=<%=htable%>&schema=<%=schema%>&group=<%=group%>"
                                                  + "&key="     + w2ui.hbaseTableForm.record.key
                                                  + "&filters=" + w2ui.hbaseTableForm.record.filters
                                                  + "&selects=" + selects
                                                  + "&start="   + w2ui.hbaseTableForm.record.start
                                                  + "&stop="    + w2ui.hbaseTableForm.record.stop
                                                  + "&limit="   + w2ui.hbaseTableForm.record.limit
                                                  + modifyRequest(w2ui.hbaseTableForm);
            loadPane("result", request);
            }
          }
        };
      modifyMenu(hform);
      if (typeof hideHBaseForm === 'undefined' || !hideHBaseForm) {
        $('#hbaseTableForm').w2form(hform);
        }
      });
    </script>
    
  <script>
    var $table = $('#table');
    var $buttonShow = $('#buttonShow')
    var $buttonHide = $('#buttonHide')
    var toHide = <%=toHide%>
    $(function() { 
      var data = [
        <%=h2table.data()%>
        ]    
      $table.bootstrapTable({data: data})
      $table.bootstrapTable('refreshOptions', {classes: 'table table-bordered table-hover table-striped table-sm'})
      $table.bootstrapTable('refreshOptions', {theadClasses: 'thead-light'})
      $table.bootstrapTable('refreshOptions', {sortable: 'true'})
      $buttonShow.click(function() {
        for (var i = 0; i < toHide.length; i++) {
          $table.bootstrapTable('showRow', {uniqueId: toHide[i]})
          }
        })
      $buttonHide.click(function() {
        for (var i = 0; i < toHide.length; i++) {
          $table.bootstrapTable('hideRow', {uniqueId: toHide[i]})
          }
        })
      })
    function detailFormatter(index, row) {
      var html = []
      $.each(row, function (key, value) {
        if (value.startsWith('binary:')) {
          html.push("<b><a href='#' onclick='loadPane(\"image\", \"FITSView.jsp?id=" + value + "\", true, \"" + visheight + "px\")' title='show using JS9'>" + key + "</a>");
          html.push("<a target='popup' href='FITSView.jsp?id=" + value + "' title='show in JS9' title='show in Graph'>&#8599;</a></b></br/>");
          }
        else {
          if (key == "key") {
            html.push("<b><a href='#' onclick='gcall(`g.V().has(\"lbl\", \"<%=hbaseRowName%>\").has(\"<%=hbaseRowKey%>\", \"" + value + "\")`)'>&#9738;</a></b></br/>");
            html.push('<b>' + key + ':</b> ' + value + '<br/>');
            }
          else {
            html.push(histSelector(key) + '<b>' + key + ':</b> ' + value + '<br/>');
            }
          }
        })
      return html.join('')
      }
    function binaryFormatter(value, row) {
      return "<b><a href='#' onclick='loadPane(\"image\", \"FITSView.jsp?id=" + value + "\", true, \"" + visheight + "px\")'>*binary*</a><a target='popup' href='FITSView.jsp?id=" + value + "'>&#8599;</a></b>"
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
      if (kind == "evolution" && !x && !y) {
        window.alert("x or y - axis should be selected");
        }
      if (kind == "scatter" && !y) {
        window.alert("y - axis should be selected");
        return;
        }
      var params = "hbase=<%=hbase%>&htable=<%=htable%>&schema=<%=schema%>&group=<%=group%>";
      params += "&y=" + y;
      if (x) {
        if (kind == "evolution") {
          params += x;
          }
        else {
          params += "&x=" + x;
          }
        }
      if (z) {
        params += "&z=" + z;
        }
      if (s) {
        params += "&s=" + s;
        }
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
      var params = "hbase=<%=hbase%>&htable=<%=htable%>&schema=<%=schema%>&group=<%=group%>";
      if (z) {
        params += "&z=" + z;
        }
      if (s) {
        params += "&s=" + s;
        }
      loadPane("plot", "d3/skyview.jsp?" + params, true, visheight);
      }
    </script>

  <script type="text/javascript" src="CustomRange.js"></script>
  

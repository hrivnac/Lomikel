<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- JHTools HBase Table-->
<!-- @author Julius.Hrivnac@cern.ch  -->

<%@ page import="com.JHTools.HBaser.HBaseClient" %>
<%@ page import="com.JHTools.HBaser.Schema" %>
<%@ page import="com.JHTools.WebService.HBase2Table" %>
<%@ page import="com.JHTools.WebService.BinaryDataRepository" %>

<%@ page import="org.json.JSONObject" %>

<%@ page import="java.util.Map" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Calendar" %>
<%@ page import="java.util.GregorianCalendar" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.SimpleDateFormat" %>

<%@ page import="org.apache.log4j.Logger" %>

<!--%@ page errorPage="ExceptionHandler.jsp" %-->

<%! static Logger log = Logger.getLogger(HBaseTable_jsp.class); %>

<jsp:useBean id="h2table" class="com.JHTools.WebService.HBase2Table" scope="session"/>

<div id='hbasetable'>

  <div id="hbaseTableForm" style="width: 100%"></div>
  
  <div id="hbaseResult" style="width: 100%">
    <%
      String hbase   = request.getParameter("hbase");
      String htable  = request.getParameter("htable");
      String key     = request.getParameter("key");
      String krefix  = request.getParameter("krefix");
      String selects = request.getParameter("selects");
      String filters = request.getParameter("filters");
      String version = request.getParameter("version");
      String limitS  = request.getParameter("limit");
      String start   = request.getParameter("start");
      String stop    = request.getParameter("stop");
      String group   = request.getParameter("group");
      if (hbase  == null || hbase.trim( ).equals("") ||
          htable == null || htable.trim().equals("")) {
        log.fatal("Cannot connect to " + htable + "@" + hbase);
        }
      out.println("<b><u>" + htable + "@" + hbase + "</u></b><br/>");
      log.info("Connection to " + htable + "@" + hbase);
      key       = (key     == null || key.equals(    "null")  ) ? "" : key.trim();
      krefix    = (krefix  == null || krefix.equals( "null")  ) ? "" : krefix.trim();
      filters   = (filters == null || filters.equals("null")  ) ? "" : filters.trim();
      selects   = (selects == null || selects.equals("null")  ) ? "" : selects.trim();
      version   = (version == null || version.equals("null")  ) ? "" : version.trim();
      start     = (start   == null || start.equals(  "null")  ) ? "" : start.trim();
      stop      = (stop    == null || stop.equals(   "null")  ) ? "" : stop.trim();
      group     = (group   == null || group.equals(   "null") ) ? "" : group.trim();
      int limit = (limitS  == null || limitS.trim().equals("")) ? 0  : Integer.parseInt(limitS);
      int size = 0;
      long startL;
      long stopL;
      DateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm");
      try {
        Date startD = formatter.parse(start);
        Date stopD = formatter.parse(stop);
        Calendar startC = GregorianCalendar.getInstance();
        Calendar stopC  = GregorianCalendar.getInstance();
        startC.setTime(startD);
        stopC.setTime(stopD);
        startL = startC.getTimeInMillis();
        stopL  = stopC.getTimeInMillis(); 
        start = formatter.format(startD);
        stop = formatter.format(stopD);
        out.println("<b>period:</b> "  + start + " - " + stop + "<br/>");
        }
      catch (Exception e) {
        log.info("Cannot parse period " + start + " - " + stop);
        start = "";
        stop  = "";
        startL = 0;
        stopL  = 0;
        }
      Map<String, String> filterMap = new HashMap<>();
      if (!key.equals("")) {
        out.println("<b>key</b> is <b>" + key + "</b><br/>");
        }
      else if (!krefix.equals("")) {
        out.println("<b>key</b> starts with <b>" + krefix + "</b></br>");
        filterMap.put(":", krefix + ":");
        }
      else if (!filters.equals("")) {
        String[] term;
        for (String f : filters.split(",")) {
          term = f.split(":");
          out.println("<b>" + term[0] + ":" + term[1] + "</b> contains <b>" + term[2] + "</b></br>");
          filterMap.put(term[0] + ":" + term[1], term[2] + ":SubstringComparator");
          }
        }
      if (!selects.equals("")) {
        out.println("showing only columns <b>" + selects + "</b><br/>");
        }
      if (limit > 0) {
        out.println("showing only <b>" + limit + "</b> rows<br/>");
        }
      if (!version.equals("")) {
        out.println("showing only version <b>" + version + "</b><br/>");
        }
      HBaseClient h = new HBaseClient(hbase);
      JSONObject json = h.get2JSON(htable,
                                   "schema_*");
      h2table.setup(hbase, htable);
      h2table.setTable(json, 0);
      Map<String, Map<String, String>> schemas = h2table.table();
      if (schemas != null && !schemas.isEmpty()) {
        Map<String, String> schemaMap = null;
        if (version != null && !version.trim().equals("")) {
          schemaMap = schemas.get("schema_" + version);
          }
        if (schemaMap == null) {
          schemaMap = schemas.entrySet().iterator().next().getValue();
          }
        Schema schema = new Schema(schemaMap);
        h.setSchema(schema);
        h2table.setSchema(schema);
        }
      if (!key.equals("")) {
        json = h.get2JSON(htable,
                          key);
        }
      else {
        if (limit > 0 && h2table.width() > 0) {
          size = limit * h2table.width();
          out.println("getting only <b>" + size + "</b> cells<br/>");
          }
        String filter = h.filter(filterMap, null);
        json = h.scan2JSON(htable,
                           filter,
                           size,
                           startL,
                           stopL);
        }
      if (! selects.equals("")) {
        h2table.setShowColumns(selects.split(","));
        }
      h2table.processTable(json, limit);
      String toHide = "";
      if (!group.equals("")) {
        toHide = h2table.toHide("i:objectId");
      %>
    <div id="toolbar">
      <button id="buttonHide" class="btn btn-secondary">latest alerts</button>
      <button id="buttonShow" class="btn btn-secondary">all alerts</button>
      </div>
    <%
      }
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
           data-page-size='15'
           data-pagination-pre-text='Previous'
           data-pagination-next-text='Next'
           data-show-columns='true'
           data-show-columns-toggle-all='true'
           data-show-columns-search='true'
           data-height='600'
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
      <input type="button" onclick="showHist()" value="Plot" style="background-color:#ddffdd">      
      </div>
    
    </div>
  
  <script>  
    $(function () {
      if (w2ui.hbaseTableForm) {
        w2ui['hbaseTableForm'].destroy();
        }
      w2utils.settings.dateFormat ='dd/MM/yyyy|h24:mm';
      $('#hbaseTableForm').w2form( { 
        name   : 'hbaseTableForm',
        header : 'HBase Search',
        url    : 'HBaseTable.jsp',
        fields : [
          {field:'key',     type: 'text',     html: {caption: 'Exact Key',      text : ' (exact search on row key)' ,                               attr: 'style="width: 500px"'}},
          {field:'krefix',  type: 'text',     html: {caption: 'Prefix Key',     text : ' (prefix search on row key)',                               attr: 'style="width: 500px"'}},
          {field:'filters', type: 'text',     html: {caption: 'Search Columns', text : ' (columns substring search: family:column:value,...)',      attr: 'style="width: 500px"'}},
          {field:'selects', type: 'text',     html: {caption: 'Show Columns',   text : ' (columns to show family:column:value,...)',                attr: 'style="width: 500px"'}},
          {field:'limit',   type: 'int' ,     html: {caption: 'Limit',          text : ' (max number of results)',                                  attr: 'style="width: 50px"' }},
          {field:'start',   type: 'datetime', html: {caption: 'From',           text : ' (start time)',                                             attr: 'style="width: 150px"'}},
          {field:'stop',    type: 'datetime', html: {caption: 'Till',           text : ' (end time)',                                               attr: 'style="width: 150px"'}}
          ], 
        record : { 
          key     : '<%=key%>',
          krefix  : '<%=krefix%>',
          filters : '<%=filters%>',
          selects : '<%=selects%>',
          limit   : '<%=limit%>',
          start   : '<%=start%>',
          stop    : '<%=stop%>'
          },
        actions: {
          Reset: function () {
            this.clear();
            },
          Search: function () {
            var request = w2ui.hbaseTableForm.url + "?hbase=<%=hbase%>&htable=<%=htable%>&version=<%=version%>&size=<%=size%>&group=<%=group%>"
                                                  + "&key="     + w2ui.hbaseTableForm.record.key
                                                  + "&krefix="  + w2ui.hbaseTableForm.record.krefix
                                                  + "&filters=" + w2ui.hbaseTableForm.record.filters
                                                  + "&selects=" + w2ui.hbaseTableForm.record.selects
                                                  + "&limit="   + w2ui.hbaseTableForm.record.limit
                                                  + "&start="   + w2ui.hbaseTableForm.record.start
                                                  + "&stop="    + w2ui.hbaseTableForm.record.stop;
            loadPane("result", request);
            }
          }
        });
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
        if (value.startsWith('url:')) { // TBD: whould work also for other types
          html.push("<b><a href='#' onclick='loadPane(\"graph\", \"FITSView.jsp?id=" + value + "\", true, \"" + visheight + "px\")'>" + key + "</a>(<a target='popup' href='FITSView.jsp?id=" + value + "'>*</a>)</b></br/>");
          }
        else {
          if (key == "key") {
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
      return "<b><a href='#' onclick='loadPane(\"graph\", \"FITSView.jsp?id=" + value + "\", true, \"" + visheight + "px\")'>*binary*</a>(<a target='popup' href='FITSView.jsp?id=" + value + "'>*</a>)</b>"
      }
    function histSelector(key) {
      return "<input type='checkbox' name='y' class='y' id='y_" + key + "'></input>&nbsp;";
      }
    function showHist() {
      var ys = document.getElementsByClassName('y');
      var y = "";
      for (i = 0; i < ys.length; i++) {
        if (ys[i].checked) {
          y += ys[i].id.substring(2) + " ";
          }
        }
      loadPane("graph", "HistView.jsp?y=" + y.trim(), true, visheight);
      }
    </script>
    
  
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- JHTools HBase Table-->
<!-- @author Julius.Hrivnac@cern.ch  -->

<%@ page import="com.JHTools.HBaser.HBaseClient" %>
<%@ page import="com.JHTools.HBaser.Schema" %>
<%@ page import="com.JHTools.WebService.HBase2Table" %>

<%@ page import="org.json.JSONObject" %>

<%@ page import="java.util.Map" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Calendar" %>
<%@ page import="java.util.GregorianCalendar" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.SimpleDateFormat" %>

<!--%@ page errorPage="ExceptionHandler.jsp" %-->

<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">
  <link href="HBaseTable.css"                                     rel="stylesheet" type="text/css"/>
  <link rel="stylesheet" href="bootstrap-4.4.1/css/bootstrap.min.css" type="text/css">
  <link rel="stylesheet" href="fontawesome-free-5.13.0-web/css/all.css" type="text/css">
  <link rel="stylesheet" href="bootstrap-table-1.16.0/dist/bootstrap-table.min.css" type="text/css">
  </head>
  
<body bgcolor="#ddddff">
  <script type="text/javascript" src="HBaseTable.js"></script>
  <script type="text/javascript" src="jquery-3.5.1.min.js"></script>
  <script src="https://cdnjs.cloudflare.com/ajax/libs/popper.js/1.14.7/umd/popper.min.js" integrity="sha384-UO2eT0CpHqdSJQ6hJty5KVphtPhzWj9WO1clHTMGa3JDZwrnQq4sF86dIHNDz0W1" crossorigin="anonymous"></script>
  <script src="bootstrap-4.4.1/js/bootstrap.min.js"></script>
  <script src="bootstrap-table-1.16.0/dist/bootstrap-table.min.js"></script>

  <div id='hbasetable'>
    <%  
      // TBD: make period time type
      // TBD: make list of shown columns selectable
      String hbase     = request.getParameter("hbase");
      String htable    = request.getParameter("htable");
      String key       = request.getParameter("key");
      String krefix    = request.getParameter("krefix");
      String columns   = request.getParameter("columns");
      String filters   = request.getParameter("filters");
      String version   = request.getParameter("version");
      String sizeS     = request.getParameter("size");
      String limitS    = request.getParameter("limit");
      String periodS   = request.getParameter("period");
      if (hbase  == null || hbase.trim( ).equals("") ||
          htable == null || htable.trim().equals("")) {
        // TBD: make it error
        }
      out.println("<b><u>" + htable + "@" + hbase + "</u></b>");
      key       = (key     == null                            ) ? "" : key;
      krefix    = (krefix  == null                            ) ? "" : krefix;
      columns   = (columns == null                            ) ? "" : columns;
      filters   = (filters == null                            ) ? "" : filters;
      version   = (version == null                            ) ? "" : version;
      periodS   = (periodS == null                            ) ? "" : periodS;
      int size  = (sizeS   == null || sizeS.trim( ).equals("")) ? 0  : Integer.parseInt(sizeS);
      int limit = (limitS  == null || limitS.trim().equals("")) ? 0  : Integer.parseInt(limitS);
      long startL = 0;
      long stopL  = 0;
      if (periodS != null && !periodS.trim().equals("")) {
        out.println("<b>period:</b> "  + periodS + "<br/>");
        String[] period  = periodS.split("-");
        String start = period[0].trim();
        String stop  = period[1].trim();
        DateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        Date startD = formatter.parse(start);
        Date stopD  = formatter.parse(stop);
        Calendar startC = GregorianCalendar.getInstance();
        Calendar stopC  = GregorianCalendar.getInstance();
        startC.setTime(startD);
        stopC.setTime(stopD);
        startL = startC.getTimeInMillis();
        stopL  = stopC.getTimeInMillis();
        }
      %>
    <input type="hidden" id="hbase"  value="<%=hbase%>">
    <input type="hidden" id="htable" value="<%=htable%>">
    <!-- TBD: add size,version -->
    <table>
      <tr><td><b>Exact Key:</b> </td>
          <td><input type="text" id="key" size="40" value="<%=key%>"></td>
          <td>(exact search)</td></tr>
      <tr><td><b>Prefix Key:</b> </td>
          <td><input type="text" id="krefix" size="40" value="<%=krefix%>"></td>
          <td>(prefix search)</td></tr>
      <tr><td><b>Columns Search:</b></td>
          <td><input type="text" id="filters" size="40" value="<%=filters%>"></td>
          <td>family:column:value,family:column:value,...(substring search)</td></tr>
      <tr><td><b>Columns Show First:</b></td>
          <td><input type="text" id="columns" size="40" value="<%=columns%>"></td>
          <td>family:column,family:column,...</td></tr>
      <tr><td><b>Limit:</b></td>
          <td><input type="number" id="limit" size="5" value="<%=limit%>"></td>
          <td>int</td></tr>
      <tr><td><b>Period:</b></td>
          <td><input type="text" id="period" size="40" value="<%=periodS%>"></td>
          <td>dd/MM/yyyy HH:mm-dd/MM/yyyy HH:mm</td></tr>
      <tr><td colspan="3"><button onclick="search()">Search</button></td></tr>
      </table>
    <hr/>
    <%
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
      if (!columns.equals("")) {
        out.println("showing first columns <b>" + columns + "</b><br/>");
        }
      if (limit > 0) {
        out.println("showing only <b>" + limit + "</b> results<br/>");
        }
      // TBD: show also version, size, period
      HBaseClient h = new HBaseClient(hbase);
      HBase2Table h2table = new HBase2Table();
      JSONObject json = h.get2JSON(htable,
                                   "schema_*");
      Map<String, Map<String, String>> schemas = h2table.table(json, 0);
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
        json = h.scan2JSON(htable,
                           filterMap,
                           size,
                           startL,
                           stopL);
        }
      if (!columns.equals("")) {
        h2table.setColumns(columns.split(","));
        }
      h2table.process(json, limit);
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
           data-page-size='25'
           data-pagination-pre-text='Previous'
           data-pagination-next-text='Next'
           data-show-columns='true'
           data-show-columns-toggle-all='true'
           data-show-columns-search='true'
           data-height='800'
           data-resizable='true'
           data-pagination='true'>
    <thead><tr><th data-field='key' data-sortable='true'>key</th>
      <%=h2table.thead()%>
      </tr></thead></table></div>
    <script>
      var $table = $('#table')
      $(function() { 
      var data = [
      <%=h2table.data()%>
      ]    
      $table.bootstrapTable({data: data})
      $table.bootstrapTable('refreshOptions', {classes: 'table table-bordered table-hover table-striped table-sm'})
      $table.bootstrapTable('refreshOptions', {theadClasses: 'thead-light'})
      $table.bootstrapTable('refreshOptions', {sortable: 'true'})
      })
    function detailFormatter(index, row) {
      var html = []
      $.each(row, function (key, value) {
        html.push('<b>' + key + ':</b> ' + value + '<br/>')
        })
      return html.join('')
    }
    </script>
    </div>
  </body>

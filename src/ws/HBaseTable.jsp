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

<%@ page errorPage="ExceptionHandler.jsp" %>

<!--%
  session.removeAttribute("bdr");
  %-->
<jsp:useBean id="bdr" class="com.JHTools.WebService.BinaryDataRepository" scope="session" />

<link href="HBaseTable.css" rel="stylesheet" type="text/css"/>

<div id='hbasetable'>

  <div id="hbaseTableForm" style="width: 100%"></div>
  
  <div id="hbaseTable" style="width: 100%">
    <%  
      String msg = "";
      // TBD: make period time type
      String hbase     = request.getParameter("hbase");
      String htable    = request.getParameter("htable");
      String key       = request.getParameter("key");
      String krefix    = request.getParameter("krefix");
      String columns   = request.getParameter("columns");
      String selects   = request.getParameter("selects");
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
      selects   = (selects == null                            ) ? "" : selects;
      version   = (version == null                            ) ? "" : version;
      periodS   = (periodS == null                            ) ? "" : periodS;
      int size  = (sizeS   == null || sizeS.trim( ).equals("")) ? 0  : Integer.parseInt(sizeS);
      int limit = (limitS  == null || limitS.trim().equals("")) ? 0  : Integer.parseInt(limitS);
      long startL = 0;
      long stopL  = 0;
      String start = "";
      String stop  = "";
      if (periodS != null && !periodS.trim().equals("")) {
        out.println("<b>period:</b> "  + periodS + "<br/>");
        String[] period  = periodS.split("-");
        start = period[0].trim();
        stop  = period[1].trim();
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
      if (!columns.equals("")) {
        out.println("showing first columns <b>" + columns + "</b><br/>");
        }
      if (limit > 0) {
        out.println("showing only <b>" + limit + "</b> rows<br/>");
        }
      if (size > 0) {
        out.println("showing only <b>" + size + "</b> cells<br/>");
        }
      if (msg != null) {
        out.println(msg);
        }
      // TBD: show also version, period
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
      if (! columns.equals("")) {
        h2table.setFirstColumns(columns.split(","));
        }
      h2table.process(json, limit);
      //bdr = h2table.repository();
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
           data-pagination='true'>
      <thead>
        <tr>
          <th data-field='key' data-sortable='true'>row key</th>
          <%=h2table.thead()%>
          </tr>
        </thead>
      </table>
      
      </div>
    
    </div>
      
  <script>
    async function loadHBaseTable(url) {
      $("#hbaseTable").load(url);
      }
    </script>
  
  <script>  
    $(function () {
      w2utils.settings.dateFormat ='dd/MM/yyyy|h24:mm';
      $('#hbaseTableForm').w2form( { 
        name   : 'hbaseTableForm',
        header : 'HBase Search',
        url    : 'HBaseTable.jsp',
        fields : [
          {field:'key',     type: 'text',     html: {caption: 'Key (exact search)', attr: 'style="width: 300px"'}},
          {field:'krefix',  type: 'text',     html: {caption: 'Key (prefix search)', attr: 'style="width: 300px"'}},
          {field:'columns', type: 'text',     html: {caption: 'Columns (show first: family:column,family:column,...)', attr: 'style="width: 300px"'}},
          {field:'selects', type: 'text',     html: {caption: 'Columns (show family:column:value,...)', attr: 'style="width: 300px"'}},
          {field:'filters', type: 'text',     html: {caption: 'Columns (substring search: family:column:value,...', attr: 'style="width: 300px"'}},
          {field:'limit',   type: 'int' ,     html: {caption: 'Limit'}},
          {field:'start',   type: 'datetime', html: {caption: 'From'}},
          {field:'stop',    type: 'datetime', html: {caption: 'Till'}}
          ], 
        record : { 
          krefix  : '<%=krefix%>',
          columns : '<%=columns%>',
          selects : '<%=selects%>',
          filters : '<%=filters%>',
          limit   : '<%=limit%>',
          start   : '<%=start%>',
          stop    : '<%=stop%>'
          },
        actions: {
          Reset: function () {
            this.clear();
            },
          Search: function () {
            this.save();
            var request = w2ui.hbaseTableForm.url + "?hbase=<%=hbase%>&htable=<%=htable%>&version=<%=version%>&size=<%=size%>"
                                                  + "&krefix="  + w2ui.hbaseTableForm.record.krefix
                                                  + "&columns=" + w2ui.hbaseTableForm.record.columns
                                                  + "&selects=" + w2ui.hbaseTableForm.record.selects
                                                  + "&filters=" + w2ui.hbaseTableForm.record.filters
                                                  + "&limit="   + w2ui.hbaseTableForm.record.limit
                                                  + "&period="  + w2ui.hbaseTableForm.record.start + "-" + w2ui.hbaseTableForm.record.stop;
            loadHBaseTable(request);
            }
          }
        });
      });
    </script>

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
        if (value.startsWith('url:')) { // TBD: whould work also for other types
          html.push("<b><a href='#' onclick='loadGraph(\"FITSView.jsp?id=" + value + "\", true, \"800px\")'>" + key + "</a>(<a target='popup' href='FITSView.jsp?id=" + value + "'>*</a>)</b></br/>");
          }
        else {
          html.push('<b>' + key + ':</b> ' + value + '<br/>')
          }
        })
      return html.join('')
      }
    function binaryFormatter(value, row) {
      return "<b><a href='#' onclick='loadGraph(\"FITSView.jsp?id=" + value + "\", true, \"800px\")'>*binary*</a>(<a target='popup' href='FITSView.jsp?id=" + value + "'>*</a>)</b>"
      }
    </script>
    
  
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

<!--%@ page errorPage="ExceptionHandler.jsp" %-->

<!--%
  session.removeAttribute("bdr");
  %-->
<jsp:useBean id="bdr" class="com.JHTools.WebService.BinaryDataRepository" scope="session" />

<div id='hbasetable'>
  <%  
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
    <tr><td><b>Columns Show:</b></td>
        <td><input type="text" id="selects" size="40" value="<%=selects%>"></td>
        <td>family:column:value,family:column:value,...</td></tr>
    <tr><td><b>Columns Show First:</b></td>
        <td><input type="text" id="columns" size="40" value="<%=columns%>"></td>
        <td>family:column,family:column,...</td></tr>
    <tr><td><b>Limit:</b></td>
        <td><input type="number" id="limit" size="5" value="<%=limit%>"></td>
        <td>int</td></tr>
    <tr><td><b>Period:</b></td>
        <td><input type="text" id="period" size="40" value="<%=periodS%>"></td>
        <td></td></tr>
    <tr><td colspan="3"><button onclick="search()">Search</button></td></tr>
    </table>
  <hr/>
  <%
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
      json = h.scan2JSON(htable,
                         filterMap,
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
        if (value.startsWith('url:')) { // TBD: whould work also for other types
          html.push('<b><a href="FITSView.jsp?id=' + value + '" target="_blank">' + key + '</a></b></br/>');
          }
        else {
          html.push('<b>' + key + ':</b> ' + value + '<br/>')
          }
        })
      return html.join('')
      }
    </script>
    
  <script>
    $('#period').daterangepicker({
        singleDatePicker: false,    
        showDropdowns: true,
        showWeekNumbers: false,
        showISOWeekNumbers: false,
        timePicker: true,
        timePicker24Hour: true,
        timePickerIncrement: 10,
        timePickerSeconds: false,    
        autoApply: true,
        ranges: {
          'Today': [moment(), moment()],
          'Yesterday': [moment().subtract(1, 'days'), moment().subtract(1, 'days')],
          'Last 7 Days': [moment().subtract(6, 'days'), moment()],
          'Last 30 Days': [moment().subtract(29, 'days'), moment()],
          'This Month': [moment().startOf('month'), moment().endOf('month')],
          'Last Month': [moment().subtract(1, 'month').startOf('month'), moment().subtract(1, 'month').endOf('month')]
          },
        locale: {
          format: 'DD/MM/YYYY HH:mm'
          },
        linkedCalendars: false,
        autoUpdateInput: true,
        showCustomRangeLabel: true,
        alwaysShowCalendars: true,
        startDate: moment().subtract(6, 'days'),
        endDate: moment(),
        opens: "right",
        drops: "down"
        },
      function(start, end, label) {
        $(this).val(start.format('DD/MM/YYYY HH:mm') + ' - ' + end.format('DD/MM/YYYY HH:mm'));
        }
      );
    </script>
    
  </div>

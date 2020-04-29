<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- JHTools HBase Table-->
<!-- @author Julius.Hrivnac@cern.ch  -->

<%@ page import="com.JHTools.HBaser.HBaseClient" %>
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
  <link href="sortable.css" rel="stylesheet" type="text/css"/>
  <script type="text/javascript" src="sortable.js"></script>
  </head>
  
<body bgcolor="#ddddff">
  <%  
    String hbaseS    = request.getParameter("hbase");
    String table     = request.getParameter("table");
    String columns   = request.getParameter("columns"); // TBD: out.print
    String filters   = request.getParameter("filters"); // TBD: out.print
    String periodS   = request.getParameter("period");
    String sizeS     = request.getParameter("size");
    String limitS    = request.getParameter("limit");
    String version   = request.getParameter("version");
    out.println("<h1><u>" + table + " table at " + hbaseS + "</u></h1>");
    if (version != null) {
      out.println("<b>schema version:</b> "  + version + "<br/>");
      }
    int size = 0;
    if (sizeS != null) {
      out.println("<b>size:</b> "  + sizeS + "<br/>");
      size = Integer.parseInt(sizeS);
      }
    int limit = 0;
    if (limitS != null) {
      out.println("<b>limit:</b> "  + limitS + "<br/>");
      limit = Integer.parseInt(limitS);
      }
    long startL = 0;
    long stopL  = 0;
    if (periodS != null) {
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
    if (filters != null) {
      String term;
      for (String f : filters.split(",")) {
        term = request.getParameter(f.split(":")[1]);
        if (!term.equals("*") && !term.trim().equals("")) {
          out.println("<b>" + f + "</b> = " + term + "</br>");
          filterMap.put(f + ":SubstringComparator", term);
          }
        }
      }
    HBaseClient hbase = new HBaseClient(hbaseS);
    HBase2Table h2table = new HBase2Table();
    JSONObject json = hbase.get2JSON(table,
                                     "schema_*");
    Map<String, Map<String, String>> schemas = h2table.table(json, 0);
    if (!schemas.isEmpty()) {
      Map<String, String> schema = null;
      if (version != null) {
        schema = schemas.get("schema_" + version);
        }
      if (schema == null) {
        schema = schemas.entrySet().iterator().next().getValue();
        }
      h2table.setSchema(schema);
      }
    json = hbase.scan2JSON(table,
                           filterMap,
                           size,
                           startL,
                           stopL);
    if (columns != null) {
      h2table.setColumns(columns.split(","));
      }
    String html = h2table.htmlTable(json, limit);
    out.println(html);
    %>
  </body>

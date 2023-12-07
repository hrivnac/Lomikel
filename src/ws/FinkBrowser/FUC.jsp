<%@ page language="java"
         contentType="application/json; charset=UTF-8"
         pageEncoding="UTF-8"
         trimDirectiveWhitespaces="true" %>

<%@ page import="com.astrolabsoftware.FinkBrowser.Apps.FUC" %>
<%@ page import="java.net.URLDecoder" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="org.apache.log4j.Logger" %>

<%! static Logger log = Logger.getLogger(FUC_jsp.class); %>

<%
  String help         = request.getParameter("help");
  String api          = request.getParameter("api");
  String oformat      = request.getParameter("output-format");
  String importStatus = request.getParameter("importStatus");
  String objectId     = request.getParameter("objectId");
  String gremlin      = request.getParameter("gremlin");
  List<String> argsL = new ArrayList<>();
  argsL.add("--web");
  if (help != null) {
    argsL.add("--help");
    }
  if (api != null) {
    argsL.add("--api");
    argsL.add(api);
    }
  if (oformat != null) {
    argsL.add("--output-format");
    argsL.add(oformat);
    }
  if (importStatus != null) {
    argsL.add("--importStatus");
    }
  if (objectId != null) {
    argsL.add("--objectId");
    argsL.add(objectId);
    }
  if (gremlin != null) {
    argsL.add("--gremlin");
    argsL.add(gremlin);
    }
  String[] args = argsL.toArray(new String[0]);
  if (help != null) {
    // TBD: convert into WS format
    out.println(FUC.cli().help());
    }
  out.println(FUC.doit(args));
  %>

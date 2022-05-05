<%@ page language="java"
         contentType="application/json; charset=UTF-8"
         pageEncoding="UTF-8"
         trimDirectiveWhitespaces="true" %>

<%@ page import="com.Lomikel.Apps.LUC" %>
<%@ page import="java.net.URLDecoder" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="org.apache.log4j.Logger" %>

<%! static Logger log = Logger.getLogger(LUC_jsp.class); %>

<%
  String help = request.getParameter("help");
  String api  = request.getParameter("api");
  List<String> argsL = new ArrayList<>();
  argsL.add("--web");
  if (help != null) {
    argsL.add("--help");
    }
  if (api != null) {
    argsL.add("--api");
    argsL.add(api);
    }
  String[] args = argsL.toArray(new String[0]);
  if (help != null) {
    // TBD: convert into WS format
    out.println(LUC.cli().help());
    }
  out.println(LUC.doit(args));
  %>

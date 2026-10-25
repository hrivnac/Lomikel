<%@ page language="java" contentType="application/json; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ page import="com.Lomikel.Utils.Init" %>
<%@ page import="com.Lomikel.Januser.JanusClient" %>
<%@ page import="org.apache.tinkerpop.gremlin.process.traversal.P" %>
<%@ page import="org.json.JSONArray" %>
<%@ page import="org.json.JSONObject" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%
  response.setHeader("Cache-Control", "no-store");
  String survey = request.getParameter("survey");
  if (!"LSST".equals(survey) && !"ZTF".equals(survey)) {
    response.sendError(400, "Survey must be LSST or ZTF");
    return;
  }
  String janusip = "LSST".equals(survey) ? "134.158.243.163" : "157.136.250.219";
  JanusClient jc = null;
  try {
    Init.initWS("ClassifiersWS");
    jc = new JanusClient(janusip, 2183, "janusgraph1");
    // A metadata-only traversal, not a scan of every object or alert.
    List<Map<Object, Object>> rows = jc.g().V().has("lbl", "OCol")
      .has("survey", P.within(survey, "ANY"))
      .valueMap("classifier", "survey", "flavor").dedup().toList();
    JSONArray result = new JSONArray();
    for (Map<Object, Object> row : rows) {
      JSONObject item = new JSONObject();
      for (String key : new String[] {"classifier", "survey", "flavor"}) {
        Object values = row.get(key);
        if (values instanceof List && !((List<?>) values).isEmpty())
          item.put(key, ((List<?>) values).get(0));
      }
      if (item.has("classifier")) result.put(item);
    }
    out.print(result.toString());
  } catch (Exception ex) {
    application.log("Cannot load ClassificationView classifier metadata", ex);
    response.resetBuffer();
    response.sendError(503, "Classifier metadata unavailable");
  } finally {
    if (jc != null) jc.close();
  }
%>

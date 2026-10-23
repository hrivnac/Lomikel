<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page errorPage="../ExceptionHandler.jsp" %>
<!-- Workspace preview: compact information/reset panel; legacy root is untouched. -->
<div id="mini" class="ws-mini" title="workspace information">
  <img class="ws-mini-logo" src="@LOGO@" alt="@NAME@ logo">
  <div class="ws-mini-details">
    <a class="ws-mini-name" href="@HOME@" target="RESULT" rel="noopener">@NAME@</a>
    <span class="ws-mini-meta">Version @VERSION@ <span class="ws-mini-build">· @BUILD@</span></span>
  </div>
  <div class="ws-mini-actions">
    <button type="button" class="ws-mini-help" onclick="w2popup.load({url:'Help-TopMini.html',showMax:true})" title="Help for the workspace" aria-label="Workspace help"><i class="far fa-question-circle" aria-hidden="true"></i></button>
    <button type="button" class="ws-mini-reset" onclick="reset()" title="Clear the current graph, results and feedback">Reset workspace</button>
  </div>
</div>
<script>
  // Keep the legacy Reset behavior, but reload the preview's connection panel.
  async function reset() {
    loadPane("feedback", "Feedback.jsp");
    loadPane("top",      "webservice/Top.jsp");
    loadPane("graph",    "GraphView.jsp");
    loadPane("image",    "Image.jsp");
    loadPane("plot",     "Plot.jsp");
    loadPane("result",   "Result.jsp");
    showTab("graph");
  }
</script>

<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page errorPage="../ExceptionHandler.jsp" %>
<jsp:useBean id="profile" class="com.Lomikel.WebService.Profile" scope="session"/>
<jsp:useBean id="style" class="com.Lomikel.WebService.Style" scope="session"/>
<%
  // This opt-in shell reuses the existing root-level JSPs and application session.
  String p = request.getParameter("profile");
  if (p == null || !p.matches("[A-Za-z0-9_-]+")) p = "null";
  profile.setProfile(p);
  String s = request.getParameter("style");
  style.setStyle(s);
%>
<%@ include file="../Init.jsp" %>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>@NAME@ — workspace preview</title>
  <!-- All existing fragment, script and image URLs resolve at the WAR root. -->
  <base href="<%= request.getContextPath() %>/">
  <link rel="icon" type="image" href="@ICON@">
  <link rel="stylesheet" href="vis-network-8.3.2/styles/vis-network.min.css">
  <link rel="stylesheet" href="vis-timeline-7.3.9/styles/vis-timeline-graph2d.min.css">
  <link rel="stylesheet" href="bootstrap-4.4.1/css/bootstrap.min.css">
  <link rel="stylesheet" href="fontawesome-free-5.13.0-web/css/all.css">
  <link rel="stylesheet" href="bootstrap-table-1.16.0/dist/bootstrap-table.min.css">
  <link rel="stylesheet" href="jquery-ui-1.12.1/jquery-ui.min.css">
  <link rel="stylesheet" href="w2ui-1.5.rc1/w2ui-1.5.rc1.min.css">
  <link rel="stylesheet" href="index.css">
  <link rel="stylesheet" href="webservice/workspace.css">
  <link rel="stylesheet" href="webservice/panels.css">
</head>
<body class="workspace">
  <div class="workspace-frame">
    <header class="workspace-header">
      <div class="workspace-brand"><span class="brand-mark" aria-hidden="true">◈</span>
        <div><strong>@NAME@</strong><span>Graph exploration workspace</span></div>
      </div>
      <div class="workspace-guide" aria-hidden="true">
        <span class="guide-left">Explore graph</span><span class="guide-arrow">→</span>
        <span class="guide-right">Inspect results</span>
      </div>
      <div class="workspace-tools">
        <span class="resize-hint"><i class="fas fa-arrows-alt" aria-hidden="true"></i> Drag dividers to resize</span>
        <button type="button" id="restore-layout" title="Restore the default panel sizes without resetting data">Reset layout</button>
      </div>
    </header>
    <main id="layout" aria-label="Graph exploration panels"></main>
    <footer class="workspace-footer"><span class="status-dot" aria-hidden="true"></span>
      <span>Workspace preview</span><span class="footer-separator">·</span>
      <span>Graph, analysis and feedback retain the existing application actions.</span>
    </footer>
  </div>
  <script src="vis-network-8.3.2/standalone/umd/vis-network.min.js"></script>
  <script src="vis-timeline-7.3.9/standalone/umd/vis-timeline-graph2d.min.js"></script>
  <script src="jquery-3.5.1.min.js"></script>
  <script src="jquery-ui-1.12.1/jquery-ui.min.js"></script>
  <script src="https://cdnjs.cloudflare.com/ajax/libs/popper.js/1.14.7/umd/popper.min.js" integrity="sha384-UO2eT0CpHqdSJQ6hJty5KVphtPhzWj9WO1clHTMGa3JDZwrnQq4sF86dIHNDz0W1" crossorigin="anonymous"></script>
  <script src="bootstrap-4.4.1/js/bootstrap.min.js"></script>
  <script src="bootstrap-table-1.16.0/dist/bootstrap-table.min.js"></script>
  <script src="knockout-3.2.0.js"></script>
  <script src="moment-2.25.2.js"></script>
  <script src="w2ui-1.5.rc1/w2ui-1.5.rc1.min.js"></script>
  <script src="OptionsDefault.js"></script>
  <script src="Options.js"></script>
  <script src="Tabs.js"></script>
  <script src="Sleep.js"></script>
  <script src="LoadPane.js"></script>
  <script src="Help.js"></script>
  <script>
    // Legacy GraphView.jsp reads the global viewport height while creating #vis.
    window.height = window.innerHeight;
    window.width = window.innerWidth;
    window.visheight = 360;
  </script>
  <script src="webservice/workspace.js"></script>
  <script>
    // Keep the legacy public functions: graph actions and nested D3 frames call these.
    window.feedback = function (txt) {
      var pane = document.getElementById('feedback');
      if (pane) pane.innerHTML += txt + '<br/>';
    };
    var helpButton = '<button onClick="w2popup.load({url:\'Help-Top.html\', showMax:true})" style="position:absolute;top:0;right:0"><img src="images/Help.png" width="10"/></button>';
    window.commands = function (info, actions) {
      var pane = document.getElementById('commands');
      if (pane) pane.innerHTML = info + helpButton + '<hr/>' + actions;
    };
    $.getScript('profiles/<%= p %>.js');
  </script>
</body>
</html>

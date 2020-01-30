<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- JHTools Command Center-->
<!-- @author Julius.Hrivnac@cern.ch  -->

<jsp:useBean id="wsc" class="com.JHTools.WebService.WSCommand" scope="session" />

<%@ page import="com.JHTools.WebService.WSCommand" %>

<!--%@ page errorPage="ExceptionHandler.jsp" %-->

<head>
  <script type="text/javascript" src="vis-4.21.0/dist/vis.js"></script>
  <script type="text/javascript" src="Options.js"></script>
  <script type="text/javascript" src="http://ajax.googleapis.com/ajax/libs/jquery/1.10.2/jquery.min.js"></script>
  <script type="text/javascript" src="http://ajax.googleapis.com/ajax/libs/jqueryui/1.10.2/jquery-ui.min.js"></script>
  <link href="vis-4.21.0/dist/vis-network.min.css" rel="stylesheet" type="text/css"/>  
  <link href="http://ajax.googleapis.com/ajax/libs/jqueryui/1.10.2/themes/start/jquery-ui.css" rel="stylesheet"  type="text/css"/>
  <link href="CommandCenter.css"                   rel="stylesheet" type="text/css"/>
  <link href="GraphView.css"                       rel="stylesheet" type="text/css"/>
  </head>
  
<body>
  <table width="100%" height="30%" id="Table">
    <tr height="20%">
      <td bgcolor="#ddffdd" height="20%">
        <div id="commands" title="context-sensitive commands">
          <table>
            <tr>
              <td><img src="images/JHTools.png" width="100"/></td>
              <td><h1><u>JHTools Demo</u></h1>
                  <h2>@VERSION@ <small>[@BUILD@]</small></h2>
                  </td>
              <td><a href="https://hrivnac.web.cern.ch/hrivnac/Activities/Packages/JHTools" target="RESULT">JHTools Home</a>
                  </td>
              </tr>
            </table>
          </div>
        </td>
      </tr>
    <tr height="10%">
      <td bgcolor="#ddddff">
        <div id="feedback" title="operation feedback">
          --- operation feedback ---
          </div>
        </td>
      </tr>
    <tr height="10%">
      <td bgcolor="#ddffdd">
        <div title="graph manipulations">
          <%@ include file="GraphView.jsp" %>
          </div>
        </td>
      </tr>
    </table>
  <div id="visnetwork" height="70%">
    --- graph network ---
    </div>
  <script type="text/javascript" src="Cookies.js"></script>
  <script type="text/javascript" src="Actions.js"></script>
  <script type="text/javascript" src="PostProc.js"></script>
  <script type="text/javascript" src="GraphView.js"></script>
  <%  
    // by Bean: WSCommand wsc = new WSCommand();
    wsc.setup();
    String nodesS = wsc.nodes().toJSONArray().toString();
    String edgesS = wsc.edges().toJSONArray().toString();
   %>
  <script type="text/javascript" src ="resizableTable.js"></script>
  <script type="text/javascript">
    var nodesS  = '<%=nodesS%>';
    var edgesS  = '<%=edgesS%>';
    document.getElementById("feedback").innerHTML = "Loading Graph";
    show(nodesS, edgesS);
    document.getElementById("feedback").innerHTML += "<br/>Loading Recorded Elements";
    loadCookies();
    show(null, null);
    clusterByGroups();
    </script>
  </body>

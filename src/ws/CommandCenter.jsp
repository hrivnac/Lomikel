<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- JHTools Command Center-->
<!-- @author Julius.Hrivnac@cern.ch  -->

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
              <td><img src="@LOGO@" width="100"/></td>
              <td><h1><u>@NAME@</u></h1>
                  <h2>@VERSION@ <small>[@BUILD@]</small></h2>
                  </td>
              <td><a href="https://hrivnac.web.cern.ch/hrivnac/Activities/Packages/JHTools" target="RESULT">JHTools Home</a>
                  </td>
              </tr>
            <tr>
              <td>Connect to the <b>graph server</b><br/> and request the initial <b>graph</b></td>
              <td colspan="2">
                <select name="gremlin_server" id="gremlin_server" title="database server url">
                  <%@include file="Servers.jsp" %>
                  </select>
                <br/>
                <input type="text" name="bootstrap_command" value="@BOOT@" size="40" id="bootstrap_command" title="bootstrap gremlin command"/>
                <input type="button" onclick="bootstrap()"  value="Start" title="execute command on the server"/>
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
  <script type="text/javascript" src="StylesheetDefault.js"></script>
  <script type="text/javascript" src="Stylesheet.js"></script>
  <script type="text/javascript" src="GraphView.js"></script>
  <script type="text/javascript" src="resizableTable.js"></script>
  </body>

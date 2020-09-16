<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel Top -->
<!-- @author Julius.Hrivnac@cern.ch -->

<%@ page errorPage="ExceptionHandler.jsp" %>

<link href="Top.css" rel="stylesheet" type="text/css"/>

<div id="top" title="top">
  <button onClick="w2popup.load({url:'Help-Top.html', showMax: true})" style="position:absolute; top:0; right:0">
    <img src="images/Help.png" width="10"/>
    </button>
  <table width="100%">
    <tr width="50%">
      <td id="bar"><img src="@LOGO@" width="100"/></td>
      <td id="bar"><h2><u><a href="@HOME@" target="RESULT">@NAME@</a></u></h2>
          @VERSION@ <small>[@BUILD@]</small>
          </td>
      <td id="bar" colspan="2" rowspan="2">
        <div id="commands" title="context sensitive commands">
          Connect to the <b>graph server</b>
          <br/>
          and request the initial <b>graph</b>          
          </div>
        </td>
      </tr>
    <tr width="50%">
      <td id="bar" colspan="2">
        <select name="gremlin_server" id="gremlin_server" title="database server url">
          <%@include file="Servers.jsp"%>
          </select>
         <input type="checkbox" name="add2graph" id="add2graph" value="false" title="add to the current graph">add</input>
         <br/>
        <input type="button" onclick="bootstrap('selection')"  value="Start" title="execute command on the server" style="background-color:#eeffee;"/>
        <select name="bootstrap_graph" id="bootstrap_graph" title="bootstrap gremlin graph">
          <%@include file="Graphs.jsp"%>
          </select>
        <br/>
        <input type="button" onclick="bootstrap('text')"  value="Start" title="execute command on the server" style="background-color:#eeffee;"/>
        <input type="text" name="bootstrap_command" value="@BOOT@" size="40" id="bootstrap_command" title="bootstrap gremlin command"/>
        </td>
      </tr>
    </table>
  </div>


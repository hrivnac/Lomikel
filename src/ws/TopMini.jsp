<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel Top Mini -->
<!-- @author Julius.Hrivnac@cern.ch -->

<%@ page import="com.Lomikel.WebService.HBase2Table" %>

<%@ page errorPage="ExceptionHandler.jsp" %>

<div id="mini" title="stable top">
  <button onClick="w2popup.load({url:'Help-TopMini.html', showMax: true})" style="position:absolute; top:0; right:0">
    <img src="images/Help.png" width="10"/>
    </button>
  <center><table>
    <tr>
      <td><img src="@LOGO@" width="50"/></td>
      <td><b><u><a href="@HOME@" target="RESULT">@NAME@</a></u> <small>@VERSION@ [@BUILD@]</small></b></td>
      <td><input type="button" onclick="reset()"   value="Reset"   style="color:red"/></td>
      </tr>
    </table></center>
  </div>
  
<script type="text/javascript" src="Tabs.js"></script>
  
<script>
  async function reset() {
    loadPane("feedback", "Feedback.jsp");
    loadPane("top",      "Top.jsp");
    loadPane("graph",    "GraphView.jsp");
    loadPane("image",    "Image.jsp");
    loadPane("plot",     "Plot.jsp");
    loadPane("result",   "Result.jsp");
    showTab("graph");
    }
  </script>
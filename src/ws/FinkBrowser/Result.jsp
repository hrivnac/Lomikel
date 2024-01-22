<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Fink Browser Result -->
<!-- @author Julius.Hrivnac@cern.ch -->

<%@ page import="com.Lomikel.Utils.Info" %>

<%@ page errorPage="ExceptionHandler.jsp" %>

<div id="result" title="results">
  <button onClick="w2popup.load({url:'Help-Result.html', showMax: true})" style="position:absolute; top:0; right:0">
    <img src="images/Help.png" width="10"/>
    </button>
  <center>
    <h2>Connect to the <u>graph server</u> and request the initial <u>graph</u></h2>
    <h2>Select an <u>element</u> to see possible <u>actions</u></h2>
    <table width="80%">
      <tr>
        <td align="center"><a href="https://fink-broker.org" target="_blank"><img src="images/lsst.jpg" width="70%" style="border:5px solid blue"/></a></td>
        </tr>
      <tr><td></td></tr>
      <tr>
        <td align="center"><a href="https://astrolabsoftware.github.io" target="_blank"><img src="images/bandeau_web_AstroLab.jpg" width="70%" style="border:5px solid blue"/></a></td>
        </tr>
      </table>
    </center>
  </div>

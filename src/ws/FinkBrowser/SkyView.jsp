<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Fink Browser SkyView -->
<!-- @author Julius.Hrivnac@cern.ch -->

<%@ page import="com.Lomikel.Utils.Info" %>

<%@ page errorPage="ExceptionHandler.jsp" %>

<div id="skyview" title="skyview">
  <button onClick="w2popup.load({url:'Help-SkyView.html', showMax: true})" style="position:absolute; top:0; right:0">
    <img src="images/Help.png" width="10"/>
    </button>
  <center>
    <h1>Plots goes here.</h1>
    <table width="80%">
      <tr>
        <td align="center"><a href="https://hrivnac.web.cern.ch/hrivnac/Activities/Packages/Lomikel" target="_blank"><img src="images/overlap.png" width="70%" style="border:5px solid blue"/></a></td>
        </tr>
      </table>
    </center>
  </div>

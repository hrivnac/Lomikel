<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel SkyView -->
<!-- Only visible until actual skyview is available -->
<!-- @author Julius.Hrivnac@cern.ch -->

<%@ page errorPage="ExceptionHandler.jsp" %>

<div id="skyview" title="skyview" style="none">
  <button onClick="w2popup.load({url:'Help-SkyView.html', showMax: true})" style="position:absolute; top:0; right:0">
    <img src="images/Help.png" width="10"/>
    </button>
  <center><h1>SkyView goes here.</h1></center>
  <center><img src="images/GraphDB_0.jpg" width="60%"/></center>
  </div>

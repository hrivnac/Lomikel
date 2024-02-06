<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Fink Browser Result -->
<!-- @author Julius.Hrivnac@cern.ch -->

<%@ page errorPage="ExceptionHandler.jsp" %>

<link href="Result.css" rel="stylesheet" type="text/css"/>

<div id="result" title="results">
  <button onClick="w2popup.load({url:'Help-Result.html', showMax: true})" style="position:absolute; top:0; right:0">
    <img src="images/Help.png" width="10"/>
    </button>
  <center><h1>Results go here.</h1></center>
  <center><h2>Connect to the <u>graph server</u> and request the initial <u>graph</u></h2></center>
  <center><h2>Select an <u>element</u> to see possible <u>actions</u></h2></center>
  <center><img src="images/overlap.png" width="60%"/></center>
  </div>

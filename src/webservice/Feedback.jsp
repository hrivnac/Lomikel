<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel Feedback -->
<!-- @author Julius.Hrivnac@cern.ch -->

<%@ page errorPage="ExceptionHandler.jsp" %>

<link href="Feedback.css" rel="stylesheet" type="text/css"/>

<div id="feedback" title="operation feedback">
  <button onClick="w2popup.load({url:'Help-Feedback.html', showMax: true})" style="position:absolute; top:0; right:0">
    <img src="images/Help.png" width="10"/>
    </button>
  <p class="activity-message" style="color:#a33;margin:0">Select a graph server and initial graph; click an element for actions.</p>
  </div>

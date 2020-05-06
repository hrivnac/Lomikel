<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- JHTools JSP -->
<!-- @author Julius.Hrivnac@cern.ch  -->

<%@ page import="com.JHTools.Utils.Info"%>
<%@ page import="org.apache.log4j.PropertyConfigurator"%>

<!--%@ page errorPage="ExceptionHandler.jsp" %-->

<!DOCTYPE html>
<html>

  <head>
    <title>JHTools Browser</title>
    <script type="text/javascript" src="vis-network-7.3.6/standalone/umd/vis-network.min.js"></script> 
    <script type="text/javascript" src="OptionsDefault.js"></script>
    <script type="text/javascript" src="Options.js"></script>
    <script type="text/javascript" src="HBaseTable.js"></script>
    <script type="text/javascript" src="sortable.js"></script>
    <script type="text/javascript" src="http://ajax.googleapis.com/ajax/libs/jquery/1.10.2/jquery.min.js"></script>
    <script type="text/javascript" src="http://ajax.googleapis.com/ajax/libs/jqueryui/1.10.2/jquery-ui.min.js"></script>
    <link href="index.css"                        rel="stylesheet" type="text/css"/>
    <link href="sortable.css"                     rel="stylesheet" type="text/css"/>
    <link href="GraphView.css"                    rel="stylesheet" type="text/css"/>
    <link href="gridstack/dist/gridstack.min.css" rel="stylesheet" type="text/css"/>
    </head>
    
  <body>  

    <script src="gridstack/dist/gridstack.all.js"></script>
    
      <div class="row">
        <div class="col-sm-12" style="padding-bottom: 25px;">
          <div style="padding: 15px; border: 1px grey">
    <div class="grid-stack" style="background-color:lightgrey">
    
      <div class="grid-stack-item" data-gs-x="0" data-gs-y="0" data-gs-width="4" data-gs-height="3">
        <div class="grid-stack-item-content" id="commands" title="context-sensitive commands" style="background-color:#ddffdd">
          <%@ include file="Top.jsp"%>
          </div>    
        </div>

      <div class="grid-stack-item" data-gs-x="0" data-gs-y="3" data-gs-width="4" data-gs-height="1">
        <div class="grid-stack-item-content" id="feedback" title="operation feedback" style="background-color:#ddddff">
          <p style="color:red;font-size:15px">Select <b>graph server</b> and initial <b>graph</b>,<br/>
                                              then select an <b>element</b> to see possible actions.</p>
          </div>
        </div>
      
      <div class="grid-stack-item" data-gs-x="0" data-gs-y="4" data-gs-width="4" data-gs-height="2">
        <div class="grid-stack-item-content" id="manip" title="graph manipulations" style="background-color:#ddffdd">
          <%@ include file="GraphView.jsp"%>
          </div>
        </div>
   
      <div class="grid-stack-item" data-gs-x="0" data-gs-y="6" data-gs-width="4" data-gs-height="9">
        <div class="grid-stack-item-content" id="visnetwork" title="graph network" style="background-color:white">
          --- graph network ---
          </div>
        </div>
  
      <div class="grid-stack-item" data-gs-x="4" data-gs-y="1" data-gs-width="8" data-gs-height="1">
        <div class="grid-stack-item-content" id="mini" title="main top" style="background-color:#ddffdd">
          <%@include file="TopMini.jsp"%>
          </div>
        </div>
  
      <div class="grid-stack-item" data-gs-x="4" data-gs-y="0" data-gs-width="8" data-gs-height="14">
        <div class="grid-stack-item-content" id="result" title="results" style="background-color:#ddddff">
          <%@include file="Result.jsp"%>
          </div>
        </div>
      
      </div></div></div></div>

    <script type="text/javascript" src="StylesheetDefault.js"></script>
    <script type="text/javascript" src="Stylesheet.js"></script>
    <script type="text/javascript" src="GraphView.js"></script>
    <script type="text/javascript" src="resizableTable.js"></script>
    <script type="text/javascript">
      var grid = GridStack.init({
        alwaysShowResizeHandle: /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent),
        resizable: {
          handles: 'e, se, s, sw, w'
          },
        removable: '#trash',
        removeTimeout: 100,
        acceptWidgets: '.newWidget'
        });
      grid.disable();
      </script>
      
    </body>
  
  </html>

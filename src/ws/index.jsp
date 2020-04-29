<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- JHTools JSP -->
<!-- @author Julius.Hrivnac@cern.ch  -->

<!--%@ page errorPage="ExceptionHandler.jsp" %-->

<!DOCTYPE html>
<html>

  <head>
    <title>JHTools Browser</title>
    <script type="text/javascript" src="vis-network-7.3.6/standalone/umd/vis-network.min.js"></script> 
    <script type="text/javascript" src="OptionsDefault.js"></script>
    <script type="text/javascript" src="Options.js"></script>
    <script type="text/javascript" src="http://ajax.googleapis.com/ajax/libs/jquery/1.10.2/jquery.min.js"></script>
    <script type="text/javascript" src="http://ajax.googleapis.com/ajax/libs/jqueryui/1.10.2/jquery-ui.min.js"></script>
    <link href="index.css"         rel="stylesheet" type="text/css"/>
    <link href="GraphView.css"     rel="stylesheet" type="text/css"/>
    </head>
    
  <body>  

    <script src="gridstack/dist/gridstack.all.js"></script>
    <link rel="stylesheet" href="gridstack/dist/gridstack.min.css" />
    
    <div class="grid-stack" style="background-color:lightgrey">
    
      <div class="grid-stack-item" data-gs-x="0" data-gs-y="0" data-gs-width="4" data-gs-height="3">
        <div class="grid-stack-item-content" id="commands" title="context-sensitive commands" style="background-color:#ddffdd">
          <%@ include file="Top.jsp"%>
          </div>    
        </div>

      <div class="grid-stack-item" data-gs-x="0" data-gs-y="3" data-gs-width="4" data-gs-height="1">
        <div class="grid-stack-item-content" id="feedback" title="operation feedback" style="background-color:#ddddff">
          --- operation feedback ---
          </div>
        </div>
      
      <div class="grid-stack-item" data-gs-x="0" data-gs-y="4" data-gs-width="4" data-gs-height="2" style="background-color:#ddffdd">
        <div class="grid-stack-item-content" title="graph manipulations">
          <%@ include file="GraphView.jsp"%>
          </div>
        </div>
   
      <div class="grid-stack-item" data-gs-x="0" data-gs-y="6" data-gs-width="4" data-gs-height="9" data-gs-no-move="yes" data-gs-no-resize="no" data-gs-locked="yes" style="background-color:white">
        <div class="grid-stack-item-content" id="visnetwork">
          --- graph network ---
          </div>
        </div>
  
      <div class="grid-stack-item" data-gs-x="4" data-gs-y="0" data-gs-width="8" data-gs-height="1"  data-gs-no-move="yes" data-gs-no-resize="no" data-gs-locked="yes" style="background-color:#ddffdd">
        <div class="grid-stack-item-content">
          <%@include file="TopMini.jsp"%>
          </div>
        </div>
  
      <div class="grid-stack-item" data-gs-x="4" data-gs-y="0" data-gs-width="8" data-gs-height="14" style="background-color:#ddddff">
        <div class="grid-stack-item-content" id="result">
          <%@include file="Result.jsp"%>
          </div>
        </div>
      
      </div>

    <script type="text/javascript" src="StylesheetDefault.js"></script>
    <script type="text/javascript" src="Stylesheet.js"></script>
    <script type="text/javascript" src="GraphView.js"></script>
    <script type="text/javascript" src="resizableTable.js"></script>
    <script type="text/javascript">
      var grid = GridStack.init({
        alwaysShowResizeHandle: /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(
          navigator.userAgent
        ),
        resizable: {
          handles: 'e, se, s, sw, w'
        },
        removable: '#trash',
        removeTimeout: 100,
        acceptWidgets: '.newWidget'
      });
    
      grid.on('added', function(e, items) { log('added ', items) });
      grid.on('removed', function(e, items) { log('removed ', items) });
      grid.on('change', function(e, items) { log('change ', items) });
      function log(type, items) {
        var str = '';
        items.forEach(function(item) { str += ' (x,y)=' + item.x + ',' + item.y; });
        console.log(type + items.length + ' items.' + str );
      }
    
      // TODO: switch jquery-ui out
      $('.newWidget').draggable({
        revert: 'invalid',
        scroll: false,
        appendTo: 'body',
        helper: 'clone'
      });
      </script>
      
    <script>
function switchDrag() {
  if (document.getElementById('drag').checked) {
    //grid.disableDrag = false;
  }
  else {
    //grid.disableDrag = true ;
  }
  }
  </script>
 
    </body>
  
  </html>

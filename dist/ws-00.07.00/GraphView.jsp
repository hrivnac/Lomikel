<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel Graph View -->
<!-- @author Julius.Hrivnac@cern.ch -->

<%@ page errorPage="ExceptionHandler.jsp" %>

<link href="GraphView.css" rel="stylesheet" type="text/css"/>

<div id="graph">
  <div id="manip" title="graph manipulations" style="background-color:#ddffdd">
    <button onClick="w2popup.load({url:'Help-GraphView.html', showMax: true})" style="position:absolute; top:0; right:0">
      <img src="images/Help.png" width="10"/>
      </button>
    Customize the interactions with the <b>graph</b>.
     <br/>
     <input type="button" onclick="clusterByGroups()"  value="Cluster by group type" style="background-color:#bbffbb;">
     <input type="button" onclick="clusterByHubsize()" value="Cluster by group size" style="background-color:#bbffbb;">
     <input type="button" onclick="clusterExpand()"    value="Expand all clusters"   style="background-color:#bbffbb;">
     <input type="button" onclick="fillEdges()"        value="Show all edges"        style="background-color:#bbffbb;">
     <input type="checkbox" name="layout"     id="glayout"           onclick="switchLayout()"  value="false" title="hierarchical layout"                   >hierarchical</input>
    (<input type="checkbox" name="layout"     id="glayout_direction" onclick="switchLayout()"  value="false" title="up-down or left-right"                 >up/lr</input>
     <input type="checkbox" name="layout"     id="glayout_method"    onclick="switchLayout()"  value="false" title="ordered by size or hierarchy"          >size/hierarchy</input>)
     <input type="checkbox" name="physics"    id="physics"           onclick="switchPhysics()" value="true"  title="activate animation"             checked>live</input>
     <br/>
     <input type="checkbox" name="clusterize" id="clusterize"                          value="true"  title="clusterize after change"               checked>clusterize</input>
     <input type="checkbox" name="zoom"       id="zoom"                                value="true"  title="cluster by zoom"                       checked>zoom cluster</input>
    (<input type="checkbox" name="stabilize"  id="stabilize"                           value="false" title="stabilize when clustering by zoom"            >stabilize</input>)
     <input type="checkbox" name="expandTo"   id="expandTo"                            value="true"  title="activate children node expansion"      checked>get children</input>
     <input type="checkbox" name="expandFrom" id="expandFrom"                          value="false" title="activate parent node expansion"               >get parents</input>
     <input type="checkbox" name="removeOld"  id="removeOld"                           value="false" title="activate removal of old nodes"                >remove old</input>
    <br/>
    filter: <input type="text" name="filter" value="" id="filter" title="show only nodes with a string in their label"/>
            <input type="button" onclick="applyFilter()" value="Apply" style="background-color:#bbffbb;">
    </div>  
  <script>
    miniHeight  = document.getElementById("mini" ).offsetHeight;
    topHeight   = document.getElementById("top"  ).offsetHeight;
    manipHeight = document.getElementById("manip").offsetHeight;
    visheight = (height - miniHeight - topHeight - manipHeight - 10);
    div = document.createElement("div");
    div.style.width = "100%";
    div.style.height = visheight + "px";
    div.id = "vis";
    document.getElementById("graph").appendChild(div);
    </script>
  </div>
  
<script type="text/javascript" src="StylesheetDefault.js"></script>
<script type="text/javascript" src="Stylesheet.js"></script>
<script type="text/javascript" src="GraphView.js"></script>
 
 

<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page errorPage="ExceptionHandler.jsp" %>
<link href="GraphView.css" rel="stylesheet" type="text/css">
<!-- Opt-in graph canvas: the same vis.js controller and action IDs as the root view. -->
<div id="graph" class="ws-graph">
  <details id="manip" class="ws-graph-tools">
    <summary>Graph tools <span>Double-click to expand · click to inspect · drag to move</span></summary>
    <div class="ws-graph-controls">
      <fieldset><legend>Explore</legend>
        <label><input type="checkbox" id="expandTo" checked> Children</label>
        <label><input type="checkbox" id="expandFrom"> Parents</label>
        <label><input type="checkbox" id="removeOld"> Replace current graph on expansion</label>
        <label class="ws-graph-field" for="select">Result limit</label>
        <input type="text" id="select" value="limit(10)" title="Append a Gremlin traversal to graph requests">
      </fieldset>
      <fieldset><legend>View</legend>
        <label><input type="checkbox" id="physics" checked onclick="switchPhysics()"> Live physics</label>
        <label><input type="checkbox" id="glayout" onclick="switchLayout()"> Hierarchical</label>
        <label><input type="checkbox" id="glayout_direction" onclick="switchLayout()"> Vertical</label>
        <label><input type="checkbox" id="glayout_method" onclick="switchLayout()"> Sort by hub size</label>
        <label class="ws-graph-field" for="filter">Filter labels</label>
        <span class="ws-graph-input"><input type="text" id="filter" title="Keep nodes whose labels contain this text"><button type="button" onclick="applyFilter()">Apply</button></span>
      </fieldset>
      <fieldset><legend>Whole graph</legend>
        <label><input type="checkbox" id="clusterize" checked> Cluster after loading</label>
        <label><input type="checkbox" id="zoom" checked> Cluster when zooming out</label>
        <label><input type="checkbox" id="stabilize"> Stabilize clusters</label>
        <div class="ws-graph-buttons">
          <button type="button" onclick="clusterByGroups()">Group nodes</button>
          <button type="button" onclick="clusterByHubsize()">Cluster hubs</button>
          <button type="button" onclick="clusterExpand()">Open clusters</button>
          <button type="button" onclick="fillEdges()">Show all edges</button>
        </div>
      </fieldset>
      <button class="ws-graph-help" type="button" onclick="w2popup.load({url:'Help-GraphView.html', showMax:true})" aria-label="Graph controls help" title="Graph controls help">?</button>
    </div>
  </details>
  <div id="vis" role="region" tabindex="0" aria-label="Interactive graph: click a node or edge for actions, double-click a node to expand it"></div>
</div>
<script type="text/javascript" src="StylesheetDefault.js"></script>
<script type="text/javascript" src="Stylesheet.js"></script>
<script type="text/javascript" src="SurveyStylesheet.js?v=survey-colors-2"></script>
<script type="text/javascript" src="GraphView.js?v=query-activity-1"></script>
<script type="text/javascript" src="skyview-graph.js?v=query-activity-1"></script>
<script type="text/javascript" src="lightcurve-graph.js?v=query-activity-1"></script>
<script type="text/javascript" src="graph-table-origin.js?v=mixed-table-3"></script>
<script type="text/javascript" src="cutouts-graph.js?v=query-activity-1"></script>

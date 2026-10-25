<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page errorPage="ExceptionHandler.jsp" %>
<!-- Workspace preview: connection and context actions; legacy IDs and handlers remain. -->
<div id="top" class="ws-top" title="graph connection and selected-element actions">
  <section class="ws-connection" aria-label="Graph connection">
    <div class="ws-section-heading"><span class="ws-section-icon" aria-hidden="true"><i class="fas fa-project-diagram"></i></span>
      <div><strong>Explore a graph</strong><small>Connect, search or run a command</small></div>
      <button type="button" class="ws-panel-help" onclick="w2popup.load({url:'Help-Top.html',showMax:true})" title="Connection help" aria-label="Connection help"><i class="far fa-question-circle" aria-hidden="true"></i></button>
    </div>
    <div class="ws-server-row">
      <label for="gremlin_server">Graph server</label>
      <select name="gremlin_server" id="gremlin_server" title="database server url">
        <%@ include file="Servers.jsp" %>
      </select>
    </div>
    <div class="ws-action-row">
      <label for="bootstrap_graph">Initial graph</label>
      <div class="ws-input-action">
        <select name="bootstrap_graph" id="bootstrap_graph" title="bootstrap gremlin graph">
          <%@ include file="Graphs.jsp" %>
        </select>
        <button type="button" class="ws-primary-action" onclick="bootstrap('selection')" title="Search for graph nodes">Search</button>
      </div>
    </div>
    <div class="ws-action-row">
      <label for="bootstrap_command">Gremlin command</label>
      <div class="ws-input-action">
        <input type="text" name="bootstrap_command" value="@BOOT@" id="bootstrap_command" title="bootstrap gremlin command" autocomplete="off">
        <button type="button" class="ws-secondary-action" onclick="bootstrap('text')" title="Execute command returning graph nodes">Execute</button>
      </div>
    </div>
    <label class="ws-append"><input type="checkbox" name="add2graph" id="add2graph" value="false" title="add to the current graph"> Add results to current graph</label>
  </section>
  <section class="ws-context" aria-label="Selected element actions">
    <div class="ws-section-heading"><span class="ws-section-icon" aria-hidden="true"><i class="far fa-hand-pointer"></i></span>
      <div><strong>Selection actions</strong><small>Tools for the selected graph element</small></div>
    </div>
    <div id="commands" class="ws-commands" title="context sensitive commands">
      Connect to a <b>graph server</b> and request an initial <b>graph</b>. Then select an element to see available actions.
    </div>
  </section>
</div>

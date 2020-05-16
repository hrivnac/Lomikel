<div id="commands" title="context sensitive commands">
  <table>
    <tr>
      <td><img src="@LOGO@" width="100"/></td>
      <td><h2><u><a href="@HOME@" target="RESULT">@NAME@</a></u></h2>
          <h3>@VERSION@ <small>[@BUILD@]</small></h3>
          </td>
      </tr>
    <tr>
      <td>Connect to the <b>graph server</b><br/> and request the initial <b>graph</b></td>
      <td colspan="2">
        <select name="gremlin_server" id="gremlin_server" title="database server url">
          <%@include file="Servers.jsp"%>
          </select>
        <br/>
        <input type="button" onclick="bootstrap('selection')"  value="Start" title="execute command on the server"/>
        <select name="bootstrap_graph" id="bootstrap_graph" title="bootstrap gremlin graph">
          <%@include file="Graphs.jsp"%>
          </select>
        <br/>
        <input type="button" onclick="bootstrap('text')"  value="Start" title="execute command on the server"/>
        <input type="text" name="bootstrap_command" value="@BOOT@" size="40" id="bootstrap_command" title="bootstrap gremlin command"/>
        </td>
      </tr>
    </table>
  </div>


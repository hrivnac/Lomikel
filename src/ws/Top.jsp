<table>
  <tr>
    <td><img src="@LOGO@" width="100"/></td>
    <td><h2><u>@NAME@</u></h2>
        <h3>@VERSION@ <small>[@BUILD@]</small></h3>
        </td>
    <td><a href="https://hrivnac.web.cern.ch/hrivnac/Activities/Packages/JHTools" target="RESULT">JHTools Home</a>
        </td>
    </tr>
  <tr>
    <td>Connect to the <b>graph server</b><br/> and request the initial <b>graph</b></td>
    <td colspan="2">
      <select name="gremlin_server" id="gremlin_server" title="database server url">
        <%@include file="Servers.jsp"%>
        </select>
      <br/>
      <input type="text" name="bootstrap_command" value="@BOOT@" size="40" id="bootstrap_command" title="bootstrap gremlin command"/>
      <input type="button" onclick="bootstrap()"  value="Start" title="execute command on the server"/>
      </td>
    </tr>
  </table>

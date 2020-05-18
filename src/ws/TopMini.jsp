<div id="mini" title="stable top">
  <center><table>
    <tr>
      <td><img src="@LOGO@" width="30"/></td>
      <td><b><u><a href="@HOME@" target="RESULT">@NAME@</a></u> <small>@VERSION@ [@BUILD@]</small></b></td>
      <td><input type="button" onclick="reset()" value="Reset"/></td>
      </tr>
    </table></center>
  </div>
  
<script>
  async function reset() {
    loadPane("commands", "Top.jsp");
    loadPane("graph", "GraphView.jsp");
    loadPane("result", "Result.jsp");
    //container = document.getElementById('visnetwork');
    }
  </script>

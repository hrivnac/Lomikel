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
    document.getElementById('commands').innerHTML = await(await fetch("Top.jsp"      )).text();// TBD:unify with GraphView.js
    document.getElementById('graph'   ).innerHTML = await(await fetch("GraphView.jsp")).text();
    document.getElementById('result'  ).innerHTML = await(await fetch("Result.jsp"   )).text();
    container = document.getElementById('visnetwork');
    }
  </script>

<center><table>
  <tr>
    <td><img src="@LOGO@" width="30"/></td>
    <td><b><u><a href="@HOME@" target="RESULT">@NAME@</a></u> <small>@VERSION@ [@BUILD@]</small></b></td>
    <td><input type="button" onclick="reset()" value="Reset"/>
        <input type="button" onclick="switchPanesInteractivity()" value="-" title="movable/resizable panes" id="switchPaneInteractivityButton"/></td>
    </tr>
  </table></center>
 
<script>
async function reset() {
  document.getElementById('commands').innerHTML = await(await fetch("Top.jsp"      )).text();
  document.getElementById('graph'   ).innerHTML = await(await fetch("GraphView.jsp")).text();
  document.getElementById('result'  ).innerHTML = await(await fetch("Result.jsp"   )).text();
  container = document.getElementById('visnetwork');
  }
function switchPanesInteractivity() {
  var button = document.getElementById('switchPaneInteractivityButton');
  if (button.value == '+') {
    button.value = "-";
    grid.disable();
    }
  else {
    button.value = "+";
    grid.enable();
    }
  }
  </script>

<center><table>
  <tr>
    <td><img src="@LOGO@" width="30"/></td>
    <td><b><u>@NAME@</u> <small>@VERSION@ [@BUILD@]</small></b></td>
    <td><a href="https://hrivnac.web.cern.ch/hrivnac/Activities/Packages/JHTools" target="RESULT">JHTools</a></td>
    <td><input type="button" onclick="reset()" value="Reset"/>
        <input type="button" onclick="switchPanesInteractivity()" value="-" title="movable/resizable panes" id="switchPaneInteractivityButton"/></td>
    </tr>
  </table></center>
 
<script>
async function reset() {
  document.getElementById('commands').innerHTML = await(await fetch("Top.jsp")).text();
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

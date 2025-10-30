function updateDetailsPanel(data) {
  const panel = document.getElementById("objectList");
  if (!panel) return;
  
  const rows = [];

  let row = `<div class="objLine mainObj"><b><u>${data.objectId}</u></b><div>`;
  for (var [k, v] of iterate_object(data.objectClassification)) {
    row += `${k}: ${v.toFixed(4)} <br/>`;
    }
  row += `</div></div>`;
  rows.push(row);
  
  for (var [key, val] of iterate_object(data.objects)) {
    rows.push(`<div class="objLine">
                 <div><b><u>${key}</b> ${val.distance.toFixed(4)}</u></div>
                 <div>${JSON.stringify(val.classes)
                            .replaceAll("{", "")
                            .replaceAll("}", "")
                            .replaceAll('"', '')
                            .replaceAll(",", "<br/>")}</div>
                 </div>`);
    }
    
  panel.innerHTML = rows.join("");
  }

function* iterate_object(o) {
    var keys = Object.keys(o);
    for (var i=0; i<keys.length; i++) {
      yield [keys[i], o[keys[i]]];
      }
  }

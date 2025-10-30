function updateDetailsPanel(data) {
  const panel = document.getElementById("objectList");
  if (!panel) return;
  
  const rows = [];

  rows.push(`<div class="objLine mainObj">
               <div><b>${data.objectId}</b></div>
               <div>${JSON.stringify(data.objectClassification)
                          .replaceAll("{", "")
                          .replaceAll("}", "")
                          .replaceAll('"', '')
                          .replaceAll(",", "<br/>")}</div>
               </div>`);
  
  for (var [key, val] of iterate_object(data.objects)) {
    rows.push(`<div class="objLine">
                 <div><b>${key}</b> ${val.distance}</div>
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

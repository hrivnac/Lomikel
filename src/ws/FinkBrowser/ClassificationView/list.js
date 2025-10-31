function updateDetailsPanel(data) {
  const panel = document.getElementById("objectList");
  if (!panel) return;
  
  const rows = [];

  let row = `<div class="objLine mainObj"><div><b><u>${data.objectId}</u></b> (<a href="https://fink-portal.org/${data.objectId}" target="_blank">Fink</a>)</div><div>`;
  for (var [k, v] of iterate_object(data.objectClassification)) {
    row += `${k}: ${v.toFixed(4)}<br/>`;
    }
  row += `</div></div>`;
  rows.push(row);
  
  let objects = {};
  for (var [key, val] of iterate_object(data.objects)) {
    objects[key] = val;
    }
  const objectsSorted = new Map([...objects.entries()].sort((a, b) => b[1]['distance'] - a[1]['distance']));
  console.log(objectsSorted);
  
  for (var [key, val] of iterate_object(data.objects)) {
    row = `<div class="objLine"><div><b><u>${key}</b>: ${val.distance.toFixed(4)}</u> (<a href="https://fink-portal.org/${key}" target="_blank">Fink</a>)</div><div>`;
    for (var [k, v] of iterate_object(val.classes)) {
      row += `${k}: ${v.toFixed(4)}<br/>`;
      }
    row += `</div></div>`;
    rows.push(row);
    }

  panel.innerHTML = rows.join("");
  }

function* iterate_object(o) {
  var keys = Object.keys(o);
  for (var i = 0; i  < keys.length; i++) {
    yield [keys[i], o[keys[i]]];
    }
  }
 
function updateDetailsPanel(data) {
  const panel = document.getElementById("objectList");
  if (!panel) return;
  
  const rows = [];

  // Add main object
  rows.push(`<div class="objLine mainObj">
               <div><b>${data.objectId}</b>: ${JSON.stringify(data.objectClassification)}</div>
               </div>`);
  
  for (object of data.objects) {
    console.log(object);
  }
  /*

  // Add all other visible objects
  for (const obj of objects) {
    if (obj.id === mainObjectId) continue;
    const dx = obj.x - mainPos.x;
    const dy = obj.y - mainPos.y;
    const distance = Math.sqrt(dx*dx + dy*dy).toFixed(2);
    rows.push(`
      <div class="objLine">
        <div>${obj.name || obj.id}</div>
        <div>Distance: ${distance}</div>
        <div>Classes: ${formatClasses(obj.classes)}</div>
      </div>
    `);
  }
*/
  panel.innerHTML = rows.join("");
  
}


function formatClasses(classData) {
  if (!classData) return "—";
  return Object.entries(classData)
               .map(([cls, weight]) => `${cls} (${weight.toFixed(2)})`)
               .join(", ");
  }


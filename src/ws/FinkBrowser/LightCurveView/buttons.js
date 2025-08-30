function createSNIDButtons() {
  const container = document.getElementById("snid-buttons");
  snid_selection.forEach(snid => {
    const btn = document.createElement("button");
    btn.textContent = snid;
    btn.className = "snid-btn";
    btn.dataset.snid = String(snid);
    btn.onclick = () => loadSNID(snid);
    container.appendChild(btn);
  });
}

function updateSNIDHighlight() {
  const buttons = document.querySelectorAll("#snid-buttons .snid-btn");
  buttons.forEach(btn => {
    if (btn.dataset.snid === String(activeSNID)) {
      btn.classList.add("active");
      }
    else {
      btn.classList.remove("active");
      }
    });
  }
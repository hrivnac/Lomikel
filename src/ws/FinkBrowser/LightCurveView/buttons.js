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
  container.appendChild(document.createElement("hr"));
  demo_selection.forEach(snid => {
    const btn = document.createElement("button");
    btn.textContent = snid;
    btn.className = "snid-btn";
    btn.dataset.snid = String(snid);
    btn.onclick = () => loadDemo(snid);
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
  
function loadPresets() {
  const stored = localStorage.getItem("savedPresets");
  if (stored) {
    savedPresets = JSON.parse(stored);
    Object.keys(savedPresets).forEach(name => createPresetButton(name));
    }
  }

function savePresetsToStorage() {
  localStorage.setItem("savedPresets", JSON.stringify(savedPresets));
  }

function initSaveButton() {
  const container = document.getElementById("save-buttons");
  const saveBtn = document.createElement("button");
  saveBtn.textContent = "Save";
  saveBtn.className = "save-btn";
  container.appendChild(saveBtn);
  saveBtn.addEventListener("click", () => {
    const name = prompt("Enter a name for this parameter set:");
    if (!name) return;
    savedPresets[name] = {
      x: JSON.parse(JSON.stringify(coeffs.x)),
      y: JSON.parse(JSON.stringify(coeffs.y)),
      rainbowMode: xTime
      };
      
    savePresetsToStorage();
    createPresetButton(name);
    });
  }

function createPresetButton(name) {
  const container = document.getElementById("save-buttons");
  if (document.getElementById("preset_container_" + name)) return;
  const wrapper = document.createElement("div");
  wrapper.id = "preset_container_" + name;
  wrapper.className = "flex items-center m-1";
  const presetBtn = document.createElement("button");
  presetBtn.id = "preset_" + name;
  presetBtn.textContent = name;
  presetBtn.className = "preset-btn";
  const delBtn = document.createElement("button");
  delBtn.textContent = "×";
  delBtn.className = "del-btn";
  presetBtn.addEventListener("click", () => {
    Object.keys(savedPresets[name].x).forEach(f => {
      coeffs.x[f] = savedPresets[name].x[f];
      coeffs.y[f] = savedPresets[name].y[f];
      rainbowMode = savedPresets[name].rainbowMode;
      xTime = rainbowMode;
      });
    update();
    });
  delBtn.addEventListener("click", () => {
    if (confirm(`Delete preset "${name}"?`)) {
      delete savedPresets[name];
      savePresetsToStorage();
      wrapper.remove();
      }
    });
  wrapper.appendChild(presetBtn);
  wrapper.appendChild(delBtn);
  container.appendChild(wrapper);
  }
  

let demo = generateDemoData();
let activeSNID = null;
let savedPresets = {};
let coeffs = {x: {}, y: {}};
let rainbowMode = false;

function initFinkObjectForm() {
  const form = document.getElementById("fink-object-form");
  const input = document.getElementById("fink-object-id");
  if (!form || !input) return;
  form.addEventListener("submit", async event => {
    event.preventDefault();
    await loadFinkObject(input.value);
    });
  }

window.addEventListener("DOMContentLoaded", () => {
  initSliders();
  createSNIDButtons();
  initSaveButton();
  initFinkObjectForm();
  loadPresets();
  document.getElementById("resetRandom").addEventListener("click", resetRandom);
  document.getElementById("resetZero").addEventListener("click", resetZero);
  document.getElementById("resetRainbow").addEventListener("click", resetRainbow);
  resetRandom();
  plotLightCurves(demo);
  });

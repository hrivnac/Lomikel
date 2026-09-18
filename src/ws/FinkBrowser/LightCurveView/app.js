let demo = generateDemoData();
let activeSNID = null;
let savedPresets = {};
let coeffs = {x: {}, y: {}};
let rainbowMode = false;

window.addEventListener("DOMContentLoaded", () => {
  initSliders();
  createSNIDButtons();
  initSaveButton();
  loadPresets();
  document.getElementById("resetRandom").addEventListener("click", resetRandom);
  document.getElementById("resetZero").addEventListener("click", resetZero);
  document.getElementById("resetRainbow").addEventListener("click", resetRainbow);
  resetRandom();
  plotLightCurves(demo);
  });

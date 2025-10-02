let demo = generateDemoData();
let activeSNID = null;
let savedPresets = {};
let coeffs = {x:{}, y:{}};
let rainbowMode = false;
resetRandom();
initSaveButton();
loadPresets();

window.addEventListener('DOMContentLoaded', () => {
  initSliders();
  createSNIDButtons();
  document.getElementById('resetRandom' ).addEventListener('click', resetRandom);
  document.getElementById('resetZero'   ).addEventListener('click', resetZero);
  document.getElementById('resetRainbow').addEventListener('click', resetRainbow);
  update();
  });

let coeffs = {x:{}, y:{}};
let demo = generateDemoData();
let activeSNID = null;

window.addEventListener('DOMContentLoaded', () => {
  initSliders();
  createSNIDButtons();
  document.getElementById('resetRandom').addEventListener('click', resetRandom);
  document.getElementById('resetZero').addEventListener('click', resetZero);
  update();
  });

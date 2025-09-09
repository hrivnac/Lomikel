let demo = generateDemoData();
let activeSNID = null;
let coeffs = {x:{}, y:{}};
resetRandom();


window.addEventListener('DOMContentLoaded', () => {
  initSliders();
  createSNIDButtons();
  document.getElementById('resetRandom' ).addEventListener('click', resetRandom);
  document.getElementById('resetZero'   ).addEventListener('click', resetZero);
  document.getElementById('resetRainbow').addEventListener('click', resetRainbow);
  update();
  });

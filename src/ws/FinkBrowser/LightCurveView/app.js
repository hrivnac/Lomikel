let coeffs = {x:{}, y:{}};
let demo = generateDemoData();

window.addEventListener('DOMContentLoaded', () => {
  initSliders();
  document.getElementById('resetRandom').addEventListener('click', resetRandom);
  document.getElementById('resetZero').addEventListener('click', resetZero);
  update();
  });
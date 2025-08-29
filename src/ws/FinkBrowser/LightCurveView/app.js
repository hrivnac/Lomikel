const filters = ["Y","z","g","i","u","r"];

function generateDemoData(n = 70) {
  const baseJD = 2460000;
  // base irregular timeline
  const baseTimes = []; let t = 0;
  for (let k = 0; k < n; k++){
    t += 1 + Math.random() * 5;
    baseTimes.push(baseJD + t);
    }
  const data = {};
  filters.forEach(f => {
    data[f] = {
      times: [],
      values: []
      };
    });
  // different shapes per filter; drop points independently (gaps per filter)
  baseTimes.forEach((jd, idx) => {
    const tt = idx; // shape parameter
    // per-filter missingness
    const keep = {
      Y: Math.random() > 0.12,
      z: Math.random() > 0.18,
      g: Math.random() > 0.15,
      i: Math.random() > 0.20,
      u: Math.random() > 0.22,
      r: Math.random() > 0.14,
      };
    const shapes = {
      Y: 18 - 1.8 * Math.exp(-Math.pow((tt - 40) / 12, 2)) + 0.05 * Math.random(), // Gaussian dip (magnitudes)
      z: 18 + 0.5 * Math.sin(tt / 5) + 0.05 * Math.random(),
      g: 18 - 0.9 * Math.exp(-Math.pow((tt - 25) / 9, 2)) +  0.6 * Math.exp(-Math.pow((tt - 60) / 10, 2)) + 0.05 * Math.random(),
      i: 18 + ((tt % 30) < 15 ? (tt % 30) / 15 : (30 - (tt % 30)) / 15) - 0.5 + 0.04 * Math.random(), // triangle-ish
      u: 18 + 0.0008 * tt * tt - 0.06 * tt + 0.1 * Math.random(),
      r: 18 + 0.4 * Math.cos(tt / 6) + 0.004 * tt + 0.05 * Math.random(),
      };
    filters.forEach(f => {
      if (keep[f]) {
        data[f].times.push(jd);
        data[f].values.push(shapes[f]);
        }
      });
    });
  return data;
  }

// Linear interpolation with linear extrapolation (edge slopes)
function interp1D(times, values, t){
  const n = times.length;
  if (n===0) return {val:null, mode:"none"};
  if (n===1) return {val: values[0], mode: (t < times[0] ? "extrapLeft": (t > times[0] ? "extrapRight":"interp"))};
  if (t < times[0]) {
    const m = (values[1] - values[0]) / (times[1] - times[0]);
    return {val: values[0] + m * (t - times[0]), mode: "extrapLeft"};
    }
  if (t > times[n-1]){
    const m = (values[n-1] - values[n - 2]) / (times[n - 1] - times[n - 2]);
    return {val: values[n-1] + m * (t - times[n - 1]), mode:"extrapRight"};
    }
  // binary search for interval
  let lo = 0, hi = n - 1;
  while (hi - lo > 1) {
    const mid = (lo + hi) >> 1;
    if (times[mid] >= t) hi=mid;
    else lo=mid;
    }
  const frac = (t - times[lo]) / (times[hi] - times[lo]);
  return {val: values[lo] * (1 -frac) + values[hi] * frac, mode:"interp"};
  } 

// Build grid and compute combined X,Y with segmentation (left/interp/right)
function projectXY(data, coeffs){
  // intersection domain where all filters are within their observed ranges
  const firsts = filters.map(f => data[f].times[0]).filter(v => v != null);
  const lasts  = filters.map(f => data[f].times[data[f].times.length - 1]).filter(v => v != null);
  const startJD = Math.max.apply(null, firsts);
  const endJD   = Math.min.apply(null, lasts);
  const span = Math.max(1, endJD - startJD);
  const leftTail  = 0.15 * span;
  const rightTail = 0.15 * span;
  // grids
  const gridLeft = [];
  for (let t = startJD - leftTail; t < startJD; t += span / 200) gridLeft.push(t);
  const gridMid = [];
  for (let t = startJD; t <= endJD; t += span / 200) gridMid.push(t);
  const gridRight = [];
  for (let t = endJD; t <= endJD + rightTail; t += span / 200) gridRight.push(t);
  function combineAt(t) {
    let x = 0, y = 0;
    let mode = "interp";
    for (const f of filters){
      const it = interp1D(data[f].times, data[f].values, t);
      if (it.val == null) return null; // give up if any is undefined
      x += coeffs.x[f] * it.val;
      y += coeffs.y[f] * it.val;
      }
    if (t < startJD) mode = "extrapLeft";
    else if (t > endJD) mode = "extrapRight";
    else mode = "interp";
    return {x, y, mode, t};
    }
  const L = gridLeft.map(combineAt).filter(Boolean);
  const M = gridMid.map(combineAt).filter(Boolean);
  const R = gridRight.map(combineAt).filter(Boolean);
  return {L, M, R, startJD, endJD};
  }

// ======= UI state =======
let coeffs = {x:{}, y:{}};
let demo = generateDemoData();

function initSliders(){
  const panel = document.getElementById('sliders');
  panel.innerHTML = '';
  filters.forEach(f => {
    // random initial values for more interesting start
    coeffs.x[f] = + (Math.random() * 2 - 1).toFixed(2);
    coeffs.y[f] = + (Math.random() * 2 - 1).toFixed(2);
    // X slider
    const rowX = document.createElement('div');
    rowX.className = 'slider-row';
    const labX = document.createElement('label');
    labX.textContent = `${f} (X)`;
    const sX = document.createElement('input');
    sX.type = 'range';
    sX.min = -2;
    sX.max = 2;
    sX.step = 0.01;
    sX.value = coeffs.x[f];
    sX.id = `x_${f}`;
    sX.addEventListener('input', () => {
      coeffs.x[f] = + sX.value;
      update();
      });
    rowX.appendChild(labX);
    rowX.appendChild(sX);
    panel.appendChild(rowX);
    // Y slider
    const rowY = document.createElement('div');
    rowY.className = 'slider-row';
    const labY = document.createElement('label');
    labY.textContent = `${f} (Y)`;
    const sY = document.createElement('input');
    sY.type = 'range'; 
    sY.min = -2;
    sY.max = 2;
    sY.step = 0.01;
    sY.value = coeffs.y[f];
    sY.id = `y_${f}`;
    sY.addEventListener('input', () => {
      coeffs.y[f] = +sY.value;
      update();
      });
    rowY.appendChild(labY);
    rowY.appendChild(sY);
    panel.appendChild(rowY);
    });
  }

function updateFormulas(){
  const fx = 'x = ' + filters.map(f => `${coeffs.x[f].toFixed(2)}·${f}`).join(' + ');
  const fy = 'y = ' + filters.map(f => `${coeffs.y[f].toFixed(2)}·${f}`).join(' + ');
  document.getElementById('formulaX').textContent = fx;
  document.getElementById('formulaY').textContent = fy;
  }

function updatePlot(){
  const {L, M ,R , startJD, endJD} = projectXY(demo, coeffs);
  const traces = [];
  if (L.length){
    traces.push({
      x: L.map(p => p.x),
      y: L.map(p => p.y),
      mode:'lines',
      line:{dash:'dot'},
      name:'Extrapolated (left)'
      });
    }
  if (M.length){
    traces.push({
      x: M.map(p => p.x),
      y: M.map(p => p.y),
      mode:'lines+markers',
      line: {color:'grey'},
      marker: {
        color: M.map(p => p.t),
        colorscale:'Viridis',
        size:6,
        colorbar: {
          title:'JD',
          len:0.5
          }
        },
      name:'Interpolated'
      });
    // endpoints (first/last of mid)
    traces.push({
      x:[M[0].x],
      y:[M[0].y],
      mode:'markers',
      marker: {
        color:'green',
        size:10},
        name:'First'
        });
    traces.push({
      x:[M[M.length - 1].x],
      y:[M[M.length - 1].y],
      mode:'markers',
      marker: {
        color:'red',
        size:10
        },
      name:'Last'
      });
    }
  if (R.length){
    traces.push({
      x: R.map(p => p.x),
      y: R.map(p => p.y),
      mode:'lines',
      line:{
        dash:'dot'
        },
      name:'Extrapolated (right)'
      });
    }
  Plotly.newPlot('plot', traces, {
    margin:{t:24},
    xaxis:{title:'X'},
    yaxis:{title:'Y'},
    legend:{orientation:'h'}
    });
  }

function update(){
  updateFormulas();
  updatePlot();
  }

// Reset handlers update existing sliders (no duplication)
function resetRandom(){
  filters.forEach(f => {
    coeffs.x[f] = +(Math.random() * 2 - 1).toFixed(2);
    coeffs.y[f] = +(Math.random() * 2 - 1).toFixed(2);
    document.getElementById(`x_${f}`).value = coeffs.x[f];
    document.getElementById(`y_${f}`).value = coeffs.y[f];
    });
  update();
  }
  
function resetZero(){
  filters.forEach(f => {
    coeffs.x[f] = 0;
    coeffs.y[f] = 0;
    document.getElementById(`x_${f}`).value = 0;
    document.getElementById(`y_${f}`).value = 0;
    });
  update();
  }

// Wire buttons
window.addEventListener('DOMContentLoaded', () => {
  initSliders();
  document.getElementById('resetRandom').addEventListener('click', resetRandom);
  document.getElementById('resetZero').addEventListener('click', resetZero);
  update();
  });
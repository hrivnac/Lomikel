function normalizeLightcurve(data) {
  const normalized = {};
  for (const band of filters) {
    const source = data && data[band] ? data[band] : {};
    const times = Array.isArray(source.times) ? source.times : [];
    const values = Array.isArray(source.values) ? source.values : [];
    const samples = [];
    for (let index = 0; index < Math.min(times.length, values.length); index++) {
      const time = times[index];
      const value = values[index];
      if (Number.isFinite(time) && Number.isFinite(value)) {
        samples.push({time, value});
        }
      }
    samples.sort((left, right) => left.time - right.time);
    const timesNormalized = [];
    const valuesNormalized = [];
    for (let start = 0; start < samples.length;) {
      let end = start + 1;
      let sum = samples[start].value;
      while (end < samples.length && samples[end].time === samples[start].time) {
        sum += samples[end].value;
        end++;
        }
      timesNormalized.push(samples[start].time);
      valuesNormalized.push(sum / (end - start));
      start = end;
      }
    normalized[band] = {
      times: timesNormalized,
      values: valuesNormalized,
      };
    }
  return normalized;
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
  const missingBands = filters.filter(f => !data[f] || !data[f].times.length || !data[f].values.length);
  if (missingBands.length) {
    return {L: [], M: [], R: [], startJD: null, endJD: null, missingBands};
    }
  // intersection domain where all filters are within their observed ranges
  const firsts = filters.map(f => data[f].times[0]);
  const lasts  = filters.map(f => data[f].times[data[f].times.length - 1]);
  const startJD = Math.max(...firsts);
  const endJD   = Math.min(...lasts);
  if (!Number.isFinite(startJD) || !Number.isFinite(endJD) || endJD < startJD) {
    return {L: [], M: [], R: [], startJD: null, endJD: null, missingBands: []};
    }
  const span = endJD - startJD;
  const gridStep = span > 0 ? span / 200 : 0;
  const gridLeft = span > 0
    ? Array.from({length: 30}, (_, index) => startJD - (30 - index) * gridStep)
    : [];
  const gridMid = span > 0
    ? Array.from({length: 201}, (_, index) => index === 200 ? endJD : startJD + index * gridStep)
    : [startJD];
  const gridRight = span > 0
    ? Array.from({length: 30}, (_, index) => endJD + (index + 1) * gridStep)
    : [];
  function combineAt(t) {
    let x = 0, y = 0;
    for (const f of filters){
      const it = interp1D(data[f].times, data[f].values, t);
      if (it.val == null) return null; // give up if any is undefined
      if (xTime) {
        x = t - startJD;
        }
      else {
        x += coeffs.x[f] * it.val;
        }
      y += coeffs.y[f] * it.val;
      }
    const mode = t < startJD ? "extrapLeft" : (t > endJD ? "extrapRight" : "interp");
    return {x, y, mode, t: t - startJD};
    } 
    
  const L = gridLeft.map(combineAt).filter(Boolean);
  const M = gridMid.map(combineAt).filter(Boolean);
  const R = gridRight.map(combineAt).filter(Boolean);
  return {L, M, R, startJD, endJD, missingBands: []};
  }
  
function rainbowCoefficients() {
  const inv = {};
  for (const f of filters) {
    inv[f] = 1 / effLambda[f];
    }
  const maxVal = Math.max(...Object.values(inv));
  const coeffsNorm = {};
  for (const f of filters) {
    coeffsNorm[f] = inv[f] / maxVal; // normalize to 1
    }
  return coeffsNorm;
  } 

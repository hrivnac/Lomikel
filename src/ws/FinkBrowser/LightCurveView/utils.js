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
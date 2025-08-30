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
 
lightcurve = "";
function loadSNID(snid) {
  lightcurve = "";
  fetch(`sn_lightcurves/${snid}.json`).then(resp => resp.json()).
                                       then(data => {
                                         lightcurve = data; 
                                         resetRandom();     
                                         activeSNID = String(snid);
                                         updateSNIDHighlight();
                                         }).
                                       catch(err => console.error("Failed to load SNID", snid, err));
  }
// Alerts
alertsPool = [];
fetch("ztf_example.json").then(response => response.json()).
                          then(x => {alertsPool = x});
    
// Constellations      
constellations = [];
fetch("constellations.lines.json").then(response => response.json()).
                                   then(x => {constellations = x});

// Stars
const stars = [];
const d = fetch('hyg_v38.csv').then(res => res.text()).
                               then(csv => {Papa.parse(csv, {header: true,
                                                             skipEmptyLines: true,
                                                             complete: function(results, file) {
                                                                         for (let i = 0; i < results.data.length; i++) {
                                                                            ra = results.data[i].ra * 15;
                                                                            dec = results.data[i].dec;
                                                                            mag = results.data[i].mag;
                                                                            proper = results.data[i].proper;
                                                                            r = Math.max(0.5, 2.5 - mag * 0.2);
                                                                            if (ra != 0 && r > 1.5) {
                                                                              stars.push({ra: ra,
                                                                                          dec: dec,
                                                                                          r: r,
                                                                                          proper: proper,
                                                                                          alpha:  Math.max(0, 1 - mag * 0.05),
                                                                                          twinkleSpeed:  Math.max(0, 0.1*(1 - mag * 0.05))});                                                                            
                                                                              }
                                                                            }
                                                                          }
                                                                        });
                                                              });
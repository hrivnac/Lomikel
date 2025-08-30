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
d3.csv('hyg_v38.csv').
   then(data => {
     data.forEach(row => {
       const ra     = row.ra * 15;
       const dec    = row.dec;
       const mag    = row.mag;
       const proper = row.proper;
       if (ra != 0 && mag < 6) {
         stars.push({ra: ra,
                     dec: dec,
                     r: Math.max(0.5, 2.5 - mag * 0.2),
                     proper: proper,
                     alpha:  Math.max(0, 1 - mag * 0.05),
                     twinkleSpeed:  Math.max(0, 0.1 * (1 - mag * 0.05))});                                                                            
         }
       })
     })
         
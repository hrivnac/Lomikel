// Alerts
let alertsPool = [];
//fetch("ztf_example.json").then(response => response.json()).
//                          then(x => {alertsPool = x});
async function fetchAlerts() {
  const allAlerts = [];
  const startdate = getStartDateParam();
  //const classes = (survey == "LSST") ? classesLSST : classesZTF;
  for (const cls of Object.keys(classes)) {
    const url = (survey == "LSST") ? `https://api.lsst.fink-portal.org/api/v1/tags?tag=${encodeURIComponent(cls)}&n=${encodeURIComponent(nAlerts)}&columns=r%3AdiaObjectId%2Cr%3AmidpointMjdTai%2Cr%3Ara%2Cr%3Adec&startdate=${encodeURIComponent(startdate)}&output-format=json`
                                   : `https://api.ztf.fink-portal.org/api/v1/latests?class=${encodeURIComponent(cls)}&n=${encodeURIComponent(nAlerts)}&columns=%3AobjectId%2Ci%3Ajd%2Ci%3Ara%2Ci%3Adec&startdate=${encodeURIComponent(startdate)}&output-format=json`;
    try {
      const response = await fetch(url, {headers: {"accept": "application/json"}});
      if (!response.ok) {
        console.error(`Failed to fetch ${cls}: ${response.status}`);
        continue;
        }
      const data = await response.json();
      data.forEach(alert => {
        alert["v:classification"] = cls;
        });
      allAlerts.push(...data);
      }
    catch (err) {
      console.error("Error fetching alerts for", cls, err);
      }
    }
  alertsPool = allAlerts;
  console.log(`Fetched ${alertsPool.length} alerts from Fink Portal`);
  updateStatusPanel();
  }
// Initial fetch
fetchAlerts();
// Refresh every 10 minutes
setInterval(fetchAlerts, fetchPeriod * 60 * 1000);

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
       if (ra != 0 && mag < magMax) {
         stars.push({ra: ra,
                     dec: dec,
                     r: Math.max(0.5, 2.5 - mag * 0.2),
                     proper: proper,
                     alpha:  Math.max(0, 1 - mag * 0.05),
                     twinkleSpeed:  Math.max(0, 0.1 * (1 - mag * 0.05))});                                                                            
         }
       })
     })
         

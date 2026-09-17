// Color mapping by alert class
const classesZTF = {
  "Microlensing candidate": "255,255,0",
  "Early SN Ia candidate": "0,255,255",
  "SN candidate": "255,0,0",
  "Solar System candidate": "0,255,0",
  "Solar System MPC": "255,0,255"
  };
// Current tags accepted by the public LSST tag-search REST endpoint.
// Other portal tags can return HTTP 400 because they are unavailable through
// this endpoint; re-check the allowlist before LSST polling is re-enabled.
const classesLSST = {
  "extragalactic_lt20mag_candidate": "255,255,127",
  "extragalactic_new_candidate": "127,255,255",
  "hostless_candidate": "255,127,127",
  "in_tns": "127,255,127",
  "sn_near_galaxy_candidate": "255,127,255"
  };
const classes = {...classesZTF, ...classesLSST};
 
let fetchPeriod = 10; // every x minutes
let fetchStart = 48; // hours before now
let nAlerts = 10;
let magMax = 6;
let fetchLSST = false; // LSST alert production is currently paused.

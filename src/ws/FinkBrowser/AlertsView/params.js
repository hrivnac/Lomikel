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
const classesLSSTTags = {
  "extragalactic_lt20mag_candidate": "255,255,127",
  "extragalactic_new_candidate": "127,255,255",
  "hostless_candidate": "255,127,127",
  "in_tns": "127,255,127",
  "sn_near_galaxy_candidate": "255,127,255"
  };
const classesLSSTLatest = {
  "LSST DIA source": "255,127,127"
  };
const classesLSST = {...classesLSSTTags, ...classesLSSTLatest};
const classes = {...classesZTF, ...classesLSST};
 
let fetchPeriod = 0; // minutes; 0 loads once
let fetchStart = 0; // hours before now; 0 requests the latest available alerts
let nAlerts = 10;
let magMax = 6;
let fetchLSST = true;

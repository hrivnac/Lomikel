// Color mapping by alert class
const classesZTF = {
  "Microlensing candidate": "255,255,0",
  "Early SN Ia candidate": "0,255,255",
  "SN candidate": "255,0,0",
  "Solar System candidate": "0,255,0",
  "Solar System MPC": "255,0,255"
  };
const classesLSST = {
  "extragalactic_lt20mag_candidate": "255,255,0",
  "extragalactic_new_candidate": "0,255,255",
  "hostless_candidate": "255,0,0",
  "in_tns": "0,255,0",
  "sn_near_galaxy_candidate": "255,0,255"
  };
 const classes = {...classesZTF, ...classesLSST};
 
let survey = "LSST";
let fetchPeriod = 10; // every x minutes
let fetchStart = 48; // hours before now
let nAlerts = 10;
let magMax = 6;

 

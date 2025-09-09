// Color mapping by alert class
const classes = {
  "Microlensing candidate": "255,255,0",
  "Early SN Ia candidate": "0,255,255",
  "SN candidate": "255,0,0",
  "Solar System candidate": "0,255,0",
  "Solar System MPC": "255,0,255"
  };

let fetchPeriod = 10; // every x minutes
let fetchStart = 24; // hours before now
let nAlerts = 10;
let magMax = 6;
  
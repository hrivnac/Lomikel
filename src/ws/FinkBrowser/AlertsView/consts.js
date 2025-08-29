// Color mapping by alert class
const classes = {
  "Microlensing candidate": "255,255,0",
  "Early SN Ia candidate": "0,255,255",
  "SN candidate": "255,0,0",
  "Solar System candidate": "0,255,0",
  "Solar System MPC": "255,0,255"
  };

// North Galactic Pole (J2000)
const alphaGP = 192.85948 * Math.PI/180;
const deltaGP = 27.12825 * Math.PI/180;
const lOmega  = 32.93192 * Math.PI/180;
const epsilon = 23.4393 * Math.PI/180; // obliquity in radians
  
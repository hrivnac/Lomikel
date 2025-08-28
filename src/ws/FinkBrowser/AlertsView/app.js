
// Color mapping by alert class
const classes = {
  "Microlensing candidate": "255,255,0",
  "Early SN Ia candidate": "0,255,255",
  "SN candidate": "255,0,0",
  "Solar System candidate": "0,255,0",
  "Solar System MPC": "255,0,255"
  };

// Alerts
alertsPool = [];
fetch("ztf_example.json").then(response => response.json()).
                          then(x => {alertsPool = x});
    
// Constellations      
constellations = [];
fetch("constellations.lines.json").then(response => response.json()).
                                   then(x => {constellations = x});
                                
// Canvas
const canvas = document.getElementById('sky');
const ctx = canvas.getContext('2d');
const overview = document.getElementById('overview');
const octx = overview.getContext('2d');
const tooltip = document.getElementById('tooltip');
canvas.width = window.innerWidth;
canvas.height = window.innerHeight;

//Camera
let camera = {
  currentCenter: {ra: 180, dec: 0},
  currentZoom: 1,
  targetCenter: {ra: 180, dec: 0},
  targetZoom: 1,
  mode: "dynamic" // or "whole"
  };

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

// Alerts Flash
class Flash {
  constructor(alert) {
    this.alert = alert;
    this.color = classes[alert.class] || "255,255,255";
    this.startTime = Date.now();
    this.alpha = 0;
    this.radius = 0;
    this.trail = [];
    this.sparklePhase = Math.random() * Math.PI * 2;
    }
  draw() {
    const elapsed = (Date.now() - this.startTime) / 1000;
    if (elapsed < 1) {
      this.radius = 5 + 15 * (elapsed / 1);
      this.alpha = elapsed / 1;
      }
    else {
      const shrinkElapsed = elapsed - 1;
      this.radius = 20 * Math.max(0, 1 - shrinkElapsed / 9);
      this.alpha = Math.max(0, 1 - shrinkElapsed / 9);
      }
    if (this.alpha <= 0) return false;
    const pos = raDecToXY(this.alert.ra, this.alert.dec);
    this.trail.push({x: pos.x,
                     y: pos.y,
                     radius: this.radius,
                     alpha: this.alpha,
                     sparklePhase: this.sparklePhase});
    if (this.trail.length > 15) this.trail.shift();
    for (let t of this.trail) {
      drawStar(t.x, t.y, t.radius, this.color, t.alpha * 0.2, t.sparklePhase);
      }
    drawStar(pos.x, pos.y, this.radius, this.color, this.alpha, this.sparklePhase);
    // Class label
    ctx.font = "bold 14px sans-serif";
    ctx.fillStyle = `rgba(${this.color},${this.alpha})`;
    ctx.fillText(this.alert.objectId, pos.x + this.radius + 5, pos.y - this.radius - 5);
    this.pos = pos;
    return true;
    }
  }

// Alerts
const randInt = (a, b) => Math.floor(a + Math.random()*(b - a + 1));
let flashes = [];
function generateAlert() {
  try {
    const pick = alertsPool[randInt(0, alertsPool.length - 1)];
    const ra = pick['i:ra'];
    const dec = pick['i:dec'];
    const cls = pick['v:classification'];
    const objectId = pick['i:objectId'];
    const jd = pick['i:jd'];
    flashes.push(new Flash({ ra, dec, class: cls, objectId, jd }));
    }
  catch (e) {}
  setTimeout(generateAlert, 1000 + Math.random() * 900);
  }

// Draw Star
function drawStar(x, y, radius, color, alpha, sparklePhase = 0) {
  const spikes = 10;
  let rot = Math.PI / 2 * 3;
  const step = Math.PI / spikes;
  ctx.beginPath();
  for (let i = 0; i < spikes; i++) {
    const sparkle = 0.1 * Math.sin(Date.now() * 0.02 + sparklePhase + i);
    const outerRadius = radius * (1 + sparkle);
    const innerRadius = radius / 2;
    ctx.lineTo(x + Math.cos(rot) * outerRadius, y + Math.sin(rot) * outerRadius);
    rot += step;
    ctx.lineTo(x + Math.cos(rot) * innerRadius, y + Math.sin(rot) * innerRadius);
    rot += step;
    }
  ctx.closePath();
  ctx.fillStyle = `rgba(${color},${alpha})`;
  ctx.fill();
  }

// Draw Ecliptic
function generateEclipticPoints(nPoints = 360) {
  const eps = 23.439 * Math.PI/180; // obliquity in radians
  const points = [];
  for (let i = 0; i <= nPoints; i++) {
    const lambda = i * 2 * Math.PI / nPoints; // ecliptic longitude
    const delta = Math.asin(Math.sin(eps) * Math.sin(lambda));
    const alpha = Math.atan2(Math.cos(eps) * Math.sin(lambda), Math.cos(lambda));
    // Convert to degrees
    let ra = alpha * 180 / Math.PI;
    if (ra < 0) ra += 360;
    const dec = delta * 180 / Math.PI;
    points.push([ra, -dec]);
    }
  return points;
  }  
function drawEcliptic() {
  const points = generateEclipticPoints(360);
  ctx.save();
  ctx.strokeStyle = "rgb(200,200,100,0.5)";
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  points.forEach(([ra, dec], idx) => {
    const p = raDecToXY(ra, dec);
    if (idx === 0) ctx.moveTo(p.x, p.y);
    else ctx.lineTo(p.x, p.y);
    });
  ctx.stroke();
  ctx.restore();
  }
const epsilon = 23.4393 * Math.PI/180; // obliquity in radians
// λ in degrees along ecliptic, β = 0 for the Sun's path
function eclipticToEquatorial(lambdaDeg) {
  const lambda = lambdaDeg * Math.PI/180;
  const beta = 0;
  const sinDec = Math.sin(beta)*Math.cos(epsilon) + Math.cos(beta)*Math.sin(epsilon)*Math.sin(lambda);
  const dec = Math.asin(sinDec);
  const y = Math.sin(lambda) * Math.cos(epsilon) - Math.tan(beta) * Math.sin(epsilon);
  const x = Math.cos(lambda);
  const ra = Math.atan2(y, x);
  return {
    ra: (ra*180/Math.PI + 360)%360,
    dec: dec*180/Math.PI
    };
  }
function drawEclipticMonths() {
  const months = ["Mar", "Feb","Jan","Dec","Nov","Oct","Sep","Aug","Jul","Jun","May","Apr"];
  ctx.save();
  ctx.fillStyle = "rgba(255,215,0,0.8)";
  ctx.font = "12px Arial";
  ctx.textAlign = "center";
  months.forEach((month, i) => {
    const lambda = 25 + i * 30; // 30° per month
    const {ra, dec} = eclipticToEquatorial(lambda);
    const pos = raDecToXY(ra, -dec);
    ctx.fillText(month, pos.x, pos.y);
    });
  ctx.restore();
  }  
  
// Draw Galactic
// North Galactic Pole (J2000)
const alphaGP = 192.85948 * Math.PI/180;
const deltaGP = 27.12825 * Math.PI/180;
const lOmega  = 32.93192 * Math.PI/180;
function galacticToEquatorial(lDeg, bDeg) {
  const l = lDeg * Math.PI/180;
  const b = bDeg * Math.PI/180;
  const sinDec = Math.sin(b)*Math.sin(deltaGP) + Math.cos(b)*Math.cos(deltaGP)*Math.sin(l - lOmega);
  const dec = Math.asin(sinDec);
  const y = Math.cos(b)*Math.cos(l - lOmega);
  const x = Math.sin(b)*Math.cos(deltaGP) - Math.cos(b)*Math.sin(deltaGP)*Math.sin(l - lOmega);
  let ra = Math.atan2(y, x) + alphaGP;
  // ensure 0–360°
  ra = (ra*180/Math.PI + 360) % 360;
  const decDeg = dec*180/Math.PI;
  return { ra, dec: decDeg };
  }
function drawGalacticPlane() {
  const points = [];
  const step = 1; // 1° step in l
  for (let l = 0; l <= 360; l += step) {
    const {ra, dec} = galacticToEquatorial(l, 0);raDecToXY
    points.push(raDecToXY(ra-20, dec));
    }
  ctx.save();
  ctx.strokeStyle = "rgba(100,200,200,0.5)";
  ctx.lineWidth = 2;
  ctx.beginPath();
  points.forEach((p, i) => {
    if (i === 309) ctx.moveTo(p.x, p.y);
    else ctx.lineTo(p.x, p.y);
    });
  ctx.stroke();
  ctx.restore();
  }

// ra*dec to X*Y
function raDecToXY(ra, dec, renorm = false) {
  if (renorm) {
    if (ra < 0) {
      ra = -ra;
      }
    else {
      ra = 360 - ra;
      }
    }
  const dx = (ra - camera.currentCenter.ra) / 360;
  const dy = (dec - camera.currentCenter.dec) / 180;
  return {
    x: canvas.width / 2 + dx * canvas.width * camera.currentZoom,
    y: canvas.height / 2 - dy * canvas.height * camera.currentZoom
    };
  }

// Camera
function getBoundingBox(flashes) {
  if (flashes.length === 0) return null;
  let minRa = Infinity, maxRa = -Infinity, minDec = Infinity, maxDec = -Infinity;
  for (const f of flashes) {
    minRa = Math.min(minRa, f.alert.ra);
    maxRa = Math.max(maxRa, f.alert.ra);
    minDec = Math.min(minDec, f.alert.dec);
    maxDec = Math.max(maxDec, f.alert.dec);
    }
  return { minRa, maxRa, minDec, maxDec };
  }
function updateCamera() {
  if (camera.mode === "whole") {
    camera.targetCenter = { ra: 180, dec: 0 };
    camera.targetZoom = 1;
    return;
    }
  const box = getBoundingBox(flashes);
  if (!box) return;
  camera.targetCenter.ra = (box.minRa + box.maxRa) / 2;
  camera.targetCenter.dec = (box.minDec + box.maxDec) / 2;
  const raSpan = Math.max(5, box.maxRa - box.minRa);
  const decSpan = Math.max(5, box.maxDec - box.minDec);
  const span = Math.max(raSpan / 360, decSpan / 180);
  camera.targetZoom = Math.min(8, Math.max(1, 0.5 / span));
  }
function smoothCamera() {
  const lerp = (a, b, t) => a + (b - a) * t;
  camera.currentCenter.ra = lerp(camera.currentCenter.ra, camera.targetCenter.ra, 0.05);
  camera.currentCenter.dec = lerp(camera.currentCenter.dec, camera.targetCenter.dec, 0.05);
  camera.currentZoom = lerp(camera.currentZoom, camera.targetZoom, 0.05);
  }

// Overview Map
function drawOverview() {
  octx.clearRect(0, 0, overview.width, overview.height);
  // draw all flashes
  for (const f of flashes) {
    const ox = (f.alert.ra / 360) * overview.width;
    const oy = overview.height - ((f.alert.dec + 90) / 180) * overview.height;
    octx.beginPath();
    octx.arc(ox, oy, 2, 0, Math.PI * 2);
    octx.fillStyle = `rgb(${f.color})`;
    octx.fill();
    }
  // draw view rectangle
  const halfW = canvas.width / 2 / canvas.width / camera.currentZoom;
  const halfH = canvas.height / 2 / canvas.height / camera.currentZoom;
  const minRa = camera.currentCenter.ra - 180 / camera.currentZoom;
  const maxRa = camera.currentCenter.ra + 180 / camera.currentZoom;
  const minDec = camera.currentCenter.dec - 90 / camera.currentZoom;
  const maxDec = camera.currentCenter.dec + 90 / camera.currentZoom;
  const x1 = (minRa / 360) * overview.width;
  const x2 = (maxRa / 360) * overview.width;
  const y1 = overview.height - ((minDec + 90) / 180) * overview.height;
  const y2 = overview.height - ((maxDec + 90) / 180) * overview.height;
  octx.strokeStyle = "red";
  octx.lineWidth = 2;
  octx.strokeRect(x1, y2, x2 - x1, y1 - y2);
  // update info
  document.getElementById('viewInfo').textContent = `RA: ${minRa.toFixed(1)}–${maxRa.toFixed(1)}, Dec: ${minDec.toFixed(1)}–${maxDec.toFixed(1)}`;
   }

// Tooltip
let tooltipTimeout;
canvas.addEventListener('mousemove', e => {
  const mouseX = e.clientX, mouseY = e.clientY;
  let found = false;
  for (const f of flashes) {
    if (!f.pos) continue;
    const dx = f.pos.x - mouseX, dy = f.pos.y - mouseY;
    const r = f.radius + 10; // larger hit radius
    if (dx * dx + dy * dy <= r * r) {
      tooltip.style.display = 'block';
      tooltip.style.left = (mouseX + 10) + 'px';
      tooltip.style.top = (mouseY + 10) + 'px';
      tooltip.innerHTML = `<b>${f.alert.objectId}</b><br>${f.alert.jd}<br>${f.alert.class}<br>` +
                          `<a href="https://fink-portal.org/${f.alert.objectId}" target="_blank">View on Fink</a>`;
      found = true;
      clearTimeout(tooltipTimeout);
      tooltipTimeout = setTimeout(() => tooltip.style.display = 'none', 3000);
      break;
      }
    }
  if (!found) tooltip.style.display = 'none';
  });

// Controls
document.getElementById('btnDynamic').onclick = () => { camera.mode = "dynamic"; };
document.getElementById('btnWhole').onclick = () => { camera.mode = "whole"; };

// Main Loop
function drawStars() {
  for (const s of stars) {
    const pos = raDecToXY(360 - s.ra, s.dec);
    s.alpha += s.twinkleSpeed * (Math.random() < 0.5 ? 1 : -1);
    s.alpha = Math.max(0.3, Math.min(1, s.alpha));
    ctx.beginPath();
    ctx.arc(pos.x, pos.y, s.r * camera.currentZoom, 0, Math.PI * 2);
    ctx.font = "10px sans-serif";
    ctx.fillStyle = `rgba(255,255,255,${s.alpha})`;
    if (s.r > 2.5) {
      ctx.fillText(s.proper, pos.x + 5, pos.y - 5);
      }
    ctx.fill();
    }
  }
function drawConstellations() {
  try {
    ctx.save();
    ctx.strokeStyle = "rgba(100,100,255,1)";
    ctx.lineWidth = 1;
    constellations.features.forEach(feature => {
      const multiLine = feature.geometry.coordinates;
      multiLine.forEach(line => {
        for (let i = 0; i < line.length - 1; i++) {
          const [ra1, dec1] = line[i    ];
          const [ra2, dec2] = line[i + 1];
          const p1 = raDecToXY(ra1, dec1, true);
          const p2 = raDecToXY(ra2, dec2, true);     
          if (Math.abs(p1.x - p2.x) < canvas.width / 2) {
            ctx.beginPath();
            ctx.moveTo(p1.x, p1.y);
            ctx.lineTo(p2.x, p2.y);
            ctx.stroke();
            }
          }
        });
      });
    ctx.restore();
    }
  catch (e) {}
  }
function drawConstellationLabels() {
  try {
  ctx.save();
  ctx.fillStyle = "rgba(100,100,255,1)";
  ctx.font = "12px Arial";
  ctx.textAlign = "center";
  constellations.features.forEach(feature => {
    const name = feature.id || feature.properties?.name || "*";
    const points = [];
    feature.geometry.coordinates.forEach(multiLine => {
      multiLine.forEach(coord => {
        points.push(coord);
        });
      });
    if (points.length === 0) return;
    let sumRA = 0, sumDec = 0;
    points.forEach(([ra, dec]) => sumRA += ra);
    points.forEach(([ra, dec]) => sumDec += dec);
    const avgRA = sumRA / points.length;
    const avgDec = sumDec / points.length;
    const pos = raDecToXY(avgRA, avgDec, true);
    ctx.fillText(name, pos.x, pos.y);
    });
    ctx.restore();
    }
  catch(e){}
  }
function animate() {
  ctx.fillStyle = 'black';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  updateCamera();
  smoothCamera();
  drawStars();
  drawConstellations();
  drawConstellationLabels();
  drawEcliptic();
  drawEclipticMonths();
  drawGalacticPlane();
  flashes = flashes.filter(f => f.draw());
  drawOverview();
  requestAnimationFrame(animate);
  }

// Start
generateAlert();
animate();
window.addEventListener('resize', () => {
  canvas.width = window.innerWidth;
  canvas.height = window.innerHeight;
  });

// Legend
function initLegend() {
  const legend = document.getElementById('legend');
  legend.innerHTML = '';
  for (const [cls, rgb] of Object.entries(classes)) {
    const item = document.createElement('div');
    item.innerHTML = `<span style="background:rgb(${rgb})"></span>${cls}`;
    legend.appendChild(item);
    }
  }
initLegend();

// Logo
function initLogo() {
  const logo = document.getElementById('logo');
  logo.innerHTML = `<img src='Fink_PrimaryLogo_WEB.png' width='` + (window.innerWidth / 16) + `'>`;
  }
initLogo();

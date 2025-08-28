// Canvas
const canvas = document.getElementById('sky');
const ctx = canvas.getContext('2d');
const overview = document.getElementById('overview');
const octx = overview.getContext('2d');
const tooltip = document.getElementById('tooltip');
canvas.width = window.innerWidth;
canvas.height = window.innerHeight;

// Camera
let camera = {
  currentCenter: {ra: 180, dec: 0},
  currentZoom: 1,
  targetCenter: {ra: 180, dec: 0},
  targetZoom: 1,
  mode: "dynamic" // or "whole"
  };

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
    drawLegend();
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
document.getElementById('btnDynamic').onclick = () => {camera.mode = "dynamic";};
document.getElementById('btnWhole').onclick = () => {camera.mode = "whole";};

// Logo
function initLogo() {
  const logo = document.getElementById('logo');
  logo.innerHTML = `<img src='Fink_PrimaryLogo_WEB.png' width='` + (window.innerWidth / 16) + `'>`;
  }
initLogo();

// Main Loop
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

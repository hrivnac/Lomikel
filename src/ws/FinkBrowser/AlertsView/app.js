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
  mode: "dynamic" || "whole"
  };

// Alerts Flash
class Flash {
  constructor(alert) {
    this.alert = alert;
    this.color = classes[alert.class] || "255,255,255";
    this.spikes = (alert.survey == "LSST") ? 10 : 5;
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
      drawStar(t.x, t.y, t.radius, this.color, t.alpha * 0.2, t.sparklePhase, this.spikes);
      }
    drawStar(pos.x, pos.y, this.radius, this.color, this.alpha, this.sparklePhase, this.spikes);
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
    const survey = pick['v:survey'];
    const ra       = (survey == "LSST") ? pick['r:ra']             : pick['i:ra'];
    const dec      = (survey == "LSST") ? pick['r:dec']            : pick['i:dec'];
    const cls      = (survey == "LSST") ? pick['v:classification'] : pick['v:classification'];
    const objectId = (survey == "LSST") ? pick['r:diaObjectId']    : pick['i:objectId'];
    const jd       = (survey == "LSST") ? pick['r:midpointMjdTai'] : pick['i:jd'];
    flashes.push(new Flash({ra, dec, class: cls, objectId, jd, survey: survey}));
    }
  catch (e) {}
  setTimeout(generateAlert, 1000 + Math.random() * 900);
  }

// Camera
function getBoundingBox(flashes) {
  if (flashes.length === 0) return null;
  let minDec = Infinity, maxDec = -Infinity;
  const ras = [];
  for (const f of flashes) {
    ras.push(f.alert.ra);
    minDec = Math.min(minDec, f.alert.dec);
    maxDec = Math.max(maxDec, f.alert.dec);
    }
  const raBounds = getCircularRaBounds(ras);
  return {raCenter: raBounds.center, raSpan: raBounds.span, minDec, maxDec};
  }
function updateCamera() {
  if (camera.mode === "whole") {
    camera.targetCenter = {ra: 180, dec: 0};
    camera.targetZoom = 1;
    return;
    }
  const box = getBoundingBox(flashes);
  if (!box) return;
  camera.targetCenter.ra = box.raCenter;
  camera.targetCenter.dec = (box.minDec + box.maxDec) / 2;
  const raSpan = Math.max(5, box.raSpan);
  const decSpan = Math.max(5, box.maxDec - box.minDec);
  const span = Math.max(raSpan / 360, decSpan / 180);
  camera.targetZoom = Math.min(8, Math.max(1, 0.5 / span));
  }
function smoothCamera() {
  const lerp = (a, b, t) => a + (b - a) * t;
  camera.currentCenter.ra = interpolateRa(camera.currentCenter.ra, camera.targetCenter.ra, 0.05);
  camera.currentCenter.dec = lerp(camera.currentCenter.dec, camera.targetCenter.dec, 0.05);
  camera.currentZoom = lerp(camera.currentZoom, camera.targetZoom, 0.05);
  }

// Overview Map
function drawOverview() {
  octx.clearRect(0, 0, overview.width, overview.height);
  // draw all flashes
  for (const f of flashes) {
    const ox = ((360 - normalizeRa(f.alert.ra)) % 360) / 360 * overview.width;
    const oy = overview.height - ((f.alert.dec + 90) / 180) * overview.height;
    octx.beginPath();
    octx.arc(ox, oy, 2, 0, Math.PI * 2);
    octx.fillStyle = `rgb(${f.color})`;
    octx.fill();
    }
  // draw view rectangle
  const viewWidth = Math.min(overview.width, overview.width / camera.currentZoom);
  const viewHeight = Math.min(overview.height, overview.height / camera.currentZoom);
  const centerX = ((360 - normalizeRa(camera.currentCenter.ra)) % 360) / 360 * overview.width;
  const centerY = overview.height - ((camera.currentCenter.dec + 90) / 180) * overview.height;
  const left = centerX - viewWidth / 2;
  const top = Math.max(0, centerY - viewHeight / 2);
  const bottom = Math.min(overview.height, centerY + viewHeight / 2);
  octx.strokeStyle = "red";
  octx.lineWidth = 2;
  for (const offset of [-overview.width, 0, overview.width]) {
    const segmentLeft = Math.max(0, left + offset);
    const segmentRight = Math.min(overview.width, left + offset + viewWidth);
    if (segmentRight > segmentLeft) {
      octx.strokeRect(segmentLeft, top, segmentRight - segmentLeft, bottom - top);
      }
    }
  // update info
  document.getElementById('viewInfo').textContent = `RA center: ${normalizeRa(camera.currentCenter.ra).toFixed(1)}°, span: ${(360 / camera.currentZoom).toFixed(1)}°, Dec center: ${camera.currentCenter.dec.toFixed(1)}°`;
   }

// Tooltip
let tooltipTimeout;
let tooltipLocked = false;
canvas.addEventListener('mousemove', e => {
  if (tooltipLocked) return;
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
      if (f.alert.survey == "LSST") {
        tooltip.innerHTML = `<b>${f.alert.objectId}</b><br>${f.alert.jd}<br>${f.alert.class}<br>` +
                            `<a href="https://lsst.fink-portal.org/${f.alert.objectId}" target="_blank">View on Fink</a>`;
        }
      else {
        tooltip.innerHTML = `<b>${f.alert.objectId}</b><br>${f.alert.jd}<br>${f.alert.class}<br>` +
                            `<a href="https://ztf.fink-portal.org/${f.alert.objectId}" target="_blank">View on Fink</a>`;
        }
      found = true;
      tooltipLocked = true;
      clearTimeout(tooltipTimeout);
      tooltipTimeout = setTimeout(() => {
        tooltip.style.display = 'none';
        tooltipLocked = false;
        }, 3000);
      break;
      }
    }
  if (!found) tooltip.style.display = 'none';
  });

// Controls
document.getElementById('btnDynamic').onclick = () => {camera.mode = "dynamic";};
document.getElementById('btnWhole').onclick = () => {camera.mode = "whole";};

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

// Canvas
const canvas = document.getElementById('sky');
const ctx = canvas.getContext('2d');
const overview = document.getElementById('overview');
const octx = overview.getContext('2d');
const tooltip = document.getElementById('tooltip');
const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

function resizeCanvas() {
  canvas.width = window.innerWidth;
  canvas.height = window.innerHeight;
  }
resizeCanvas();

// Camera
let camera = {
  currentCenter: {ra: 180, dec: 0},
  currentZoom: 1,
  targetCenter: {ra: 180, dec: 0},
  targetZoom: 1,
  mode: "dynamic"
  };

// Alerts Flash
class Flash {
  constructor(alert) {
    this.alert = alert;
    this.color = classes[alert.class] || "255,255,255";
    this.spikes = (alert.survey === "LSST") ? 10 : 5;
    this.startTime = Date.now();
    this.alpha = 0;
    this.radius = 0;
    this.trail = [];
    this.sparklePhase = Math.random() * Math.PI * 2;
    }

  draw() {
    const elapsed = (Date.now() - this.startTime) / 1000;
    if (elapsed >= 10) return false;
    if (prefersReducedMotion) {
      this.radius = 12;
      this.alpha = 0.9;
      }
    else if (elapsed < 1) {
      this.radius = 5 + 15 * elapsed;
      this.alpha = elapsed;
      }
    else {
      const shrinkElapsed = elapsed - 1;
      this.radius = 20 * Math.max(0, 1 - shrinkElapsed / 9);
      this.alpha = Math.max(0, 1 - shrinkElapsed / 9);
      }
    if (this.alpha <= 0) return false;

    const pos = raDecToXY(this.alert.ra, this.alert.dec);
    if (!prefersReducedMotion) {
      this.trail.push({
        x: pos.x,
        y: pos.y,
        radius: this.radius,
        alpha: this.alpha,
        sparklePhase: this.sparklePhase
        });
      if (this.trail.length > 15) this.trail.shift();
      for (const trailPoint of this.trail) {
        for (const wrappedTrailPoint of getWrappedScreenPositions(trailPoint, trailPoint.radius)) {
          drawStar(
            wrappedTrailPoint.x,
            wrappedTrailPoint.y,
            wrappedTrailPoint.radius,
            this.color,
            wrappedTrailPoint.alpha * 0.2,
            wrappedTrailPoint.sparklePhase,
            this.spikes,
            true
            );
          }
        }
      }
    this.positions = getWrappedScreenPositions(pos, this.radius + 160);
    for (const wrappedPosition of this.positions) {
      drawStar(wrappedPosition.x, wrappedPosition.y, this.radius, this.color, this.alpha, this.sparklePhase, this.spikes, !prefersReducedMotion);
      ctx.font = "bold 14px sans-serif";
      ctx.fillStyle = `rgba(${this.color},${this.alpha})`;
      ctx.fillText(String(this.alert.objectId), wrappedPosition.x + this.radius + 5, wrappedPosition.y - this.radius - 5);
      }
    this.pos = pos;
    return true;
    }
  }

// Static markers follow current survey snapshots, not the ten-second flash timer.
class LoadedAlertMarker {
  constructor(alert) {
    this.alert = alert;
    this.color = classes[alert.class] || "255,255,255";
    this.spikes = alert.survey === "LSST" ? 10 : 5;
    this.radius = 7;
    }

  draw() {
    this.pos = raDecToXY(this.alert.ra, this.alert.dec);
    this.positions = getWrappedScreenPositions(this.pos, this.radius);
    for (const position of this.positions) {
      drawStar(position.x, position.y, this.radius, this.color, 0.9, 0, this.spikes, false);
      }
    return true;
    }
  }

// Recent alerts provide a keyboard-accessible equivalent to canvas markers.
const recentAlerts = [];
function addRecentAlert(alert) {
  const key = `${alert.survey}:${alert.objectId}:${alert.jd}`;
  const existing = recentAlerts.findIndex(item => item.key === key);
  if (existing >= 0) recentAlerts.splice(existing, 1);
  recentAlerts.unshift({key, alert});
  recentAlerts.splice(5);
  renderRecentAlerts();
  }

function createAlertLink(alert, label) {
  const url = getPortalUrl(alert);
  if (!url) return null;
  const link = document.createElement('a');
  link.href = url;
  link.target = "_blank";
  link.rel = "noopener noreferrer";
  link.textContent = label;
  return link;
  }

function renderRecentAlerts() {
  const list = document.getElementById('recentAlerts');
  const focused = document.activeElement;
  const focusedUrl = focused?.tagName === 'A' && list.contains(focused) ? focused.href : null;
  let replacementFocus = null;
  const visible = showAllAlerts ? loadedAlertMarkers.map(marker => ({alert: marker.alert})) : recentAlerts;
  document.getElementById('recentAlertsHeading').textContent = showAllAlerts ? 'All loaded alerts' : 'Recent alerts';
  const items = visible.map(({alert}) => {
    const item = document.createElement('li');
    const link = createAlertLink(alert, `${alert.survey} ${alert.objectId}`);
    if (link) {
      item.append(link);
      if (focusedUrl === link.href) replacementFocus = link;
      }
    const details = document.createElement('span');
    details.textContent = ` — ${alert.class}`;
    item.append(details);
    return item;
    });
  if (items.length === 0) {
    const empty = document.createElement('li');
    empty.textContent = 'Waiting for alerts…';
    items.push(empty);
    }
  list.replaceChildren(...items);
  replacementFocus?.focus();
  }

// Alerts
const randInt = (a, b) => Math.floor(a + Math.random() * (b - a + 1));
let flashes = [];
let showAllAlerts = false;
let loadedAlertMarkers = [];
let loadedPoolReference = null;
let loadedBounds = null;

function alertFromRow(pick) {
  const survey = pick['v:survey'];
  if (survey !== "LSST" && survey !== "ZTF") return null;
  const rawRa = survey === "LSST" ? pick['r:ra'] : pick['i:ra'];
  const rawDec = survey === "LSST" ? pick['r:dec'] : pick['i:dec'];
  const alert = {
    survey, ra: Number(rawRa), dec: Number(rawDec),
    class: pick['v:classification'],
    objectId: survey === "LSST" ? pick['r:diaObjectId'] : pick['i:objectId'],
    jd: survey === "LSST" ? pick['r:midpointMjdTai'] : pick['i:jd']
    };
  return rawRa != null && rawDec != null && String(rawRa).trim() !== '' &&
    String(rawDec).trim() !== '' && Number.isFinite(alert.ra) &&
    Number.isFinite(alert.dec) && alert.objectId != null && alert.objectId !== '' ? alert : null;
  }

function getVisibleAlerts() {
  if (!showAllAlerts) return flashes;
  if (loadedPoolReference !== alertsPool) {
    loadedPoolReference = alertsPool;
    loadedAlertMarkers = alertsPool.map(alertFromRow).filter(Boolean).map(alert => new LoadedAlertMarker(alert));
    loadedBounds = getBoundingBox(loadedAlertMarkers);
    renderRecentAlerts();
    }
  return loadedAlertMarkers;
  }

function generateAlert() {
  if (!showAllAlerts && alertsPool.length > 0) {
    const pick = alertsPool[randInt(0, alertsPool.length - 1)];
    const alert = alertFromRow(pick);
    if (alert) {
      flashes.push(new Flash(alert));
      addRecentAlert(alert);
      }
    }
  setTimeout(generateAlert, 1000 + Math.random() * 900);
  }

// Camera
function getBoundingBox(activeFlashes) {
  if (activeFlashes.length === 0) return null;
  let minDec = Infinity;
  let maxDec = -Infinity;
  const ras = [];
  for (const flash of activeFlashes) {
    ras.push(flash.alert.ra);
    minDec = Math.min(minDec, flash.alert.dec);
    maxDec = Math.max(maxDec, flash.alert.dec);
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
  const visible = getVisibleAlerts();
  const box = showAllAlerts ? loadedBounds : getBoundingBox(visible);
  if (!box) return;
  camera.targetCenter.ra = box.raCenter;
  camera.targetCenter.dec = (box.minDec + box.maxDec) / 2;
  const raSpan = Math.max(5, box.raSpan);
  const decSpan = Math.max(5, box.maxDec - box.minDec);
  const span = Math.max(raSpan / 360, decSpan / 180);
  camera.targetZoom = Math.min(8, Math.max(1, 0.5 / span));
  }

function getMaxZoomToKeepAlertsVisible(activeFlashes, centerRa) {
  let largestRaDistance = 0;
  for (const flash of activeFlashes) {
    largestRaDistance = Math.max(largestRaDistance, Math.abs(signedRaDelta(flash.alert.ra, centerRa)));
    }
  if (largestRaDistance === 0) return Infinity;
  return Math.max(1, 180 / largestRaDistance);
  }

function smoothCamera() {
  const amount = prefersReducedMotion ? 1 : 0.05;
  const lerp = (a, b, t) => a + (b - a) * t;
  camera.currentCenter.ra = interpolateRa(camera.currentCenter.ra, camera.targetCenter.ra, amount);
  camera.currentCenter.dec = lerp(camera.currentCenter.dec, camera.targetCenter.dec, amount);
  const smoothZoom = lerp(camera.currentZoom, camera.targetZoom, amount);
  const visibleZoom = getMaxZoomToKeepAlertsVisible(getVisibleAlerts(), camera.currentCenter.ra);
  camera.currentZoom = Math.min(smoothZoom, visibleZoom);
  }

// Overview Map
function drawOverview() {
  octx.clearRect(0, 0, overview.width, overview.height);
  for (const flash of [...getVisibleAlerts(), ...ssVisibleMarkers]) {
    const ox = ((360 - normalizeRa(flash.alert.ra)) % 360) / 360 * overview.width;
    const oy = overview.height - ((flash.alert.dec + 90) / 180) * overview.height;
    octx.beginPath();
    octx.arc(ox, oy, 2, 0, Math.PI * 2);
    octx.fillStyle = `rgb(${flash.color})`;
    octx.fill();
    }
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
  document.getElementById('viewInfo').textContent = `RA center ${normalizeRa(camera.currentCenter.ra).toFixed(1)}°, span ${(360 / camera.currentZoom).toFixed(1)}°, Dec center ${camera.currentCenter.dec.toFixed(1)}°`;
  }

// Tooltip and pointer/touch interaction
let tooltipTimeout;
let tooltipExitTimeout;
function findFlashAt(clientX, clientY) {
  const bounds = canvas.getBoundingClientRect();
  const x = (clientX - bounds.left) * canvas.width / bounds.width;
  const y = (clientY - bounds.top) * canvas.height / bounds.height;
  const visible = getVisibleAlerts();
  for (let i = visible.length - 1; i >= 0; i--) {
    const flash = visible[i];
    const positions = flash.positions || (flash.pos ? [flash.pos] : []);
    for (const position of positions) {
      const dx = position.x - x;
      const dy = position.y - y;
      const radius = flash.radius + 14;
      if (dx * dx + dy * dy <= radius * radius) return flash;
      }
    }
  for (let i = ssVisibleMarkers.length - 1; i >= 0; i--) {
    const marker = ssVisibleMarkers[i];
    if (marker.positions.some(position =>
      (position.x - x) ** 2 + (position.y - y) ** 2 <= (marker.radius + 8) ** 2)) {
      return marker;
      }
    }
  return null;
  }

function showAlertTooltip(flash, clientX, clientY) {
  const alert = flash.alert;
  const heading = document.createElement('strong');
  heading.textContent = String(alert.objectId);
  const metadata = document.createElement('span');
  metadata.textContent = `${alert.survey} · ${alert.class} · ${alert.jd}`;
  const link = alert.class === 'LSST SS source' ? null : createAlertLink(alert, "View on Fink");
  tooltip.replaceChildren(heading, metadata, ...(link ? [link] : []));
  tooltip.style.display = 'flex';
  const box = tooltip.getBoundingClientRect();
  const margin = 10;
  tooltip.style.left = `${Math.max(margin, Math.min(clientX + margin, window.innerWidth - box.width - margin))}px`;
  tooltip.style.top = `${Math.max(margin, Math.min(clientY + margin, window.innerHeight - box.height - margin))}px`;
  clearTimeout(tooltipExitTimeout);
  clearTimeout(tooltipTimeout);
  tooltipTimeout = setTimeout(hideTooltip, 5000);
  }

function hideTooltip() {
  clearTimeout(tooltipExitTimeout);
  tooltip.style.display = 'none';
  }

function scheduleTooltipHide() {
  clearTimeout(tooltipExitTimeout);
  tooltipExitTimeout = setTimeout(hideTooltip, 200);
  }

function keepTooltipOpen() {
  clearTimeout(tooltipExitTimeout);
  clearTimeout(tooltipTimeout);
  }

function handleCanvasPointer(event) {
  const flash = findFlashAt(event.clientX, event.clientY);
  if (flash) showAlertTooltip(flash, event.clientX, event.clientY);
  else scheduleTooltipHide();
  }
canvas.addEventListener('pointermove', event => {
  if (event.pointerType === "mouse") handleCanvasPointer(event);
  });
canvas.addEventListener('pointerdown', handleCanvasPointer);
canvas.addEventListener('pointerleave', event => {
  if (event.pointerType === "mouse") scheduleTooltipHide();
  });
tooltip.addEventListener('pointerenter', keepTooltipOpen);
tooltip.addEventListener('pointerleave', hideTooltip);

// Controls
const dynamicButton = document.getElementById('btnDynamic');
const wholeButton = document.getElementById('btnWhole');
const allAlertsButton = document.getElementById('btnAllAlerts');
const ssTrajectoryButton = document.getElementById('btnSsTrajectory');
function setCameraMode(mode) {
  camera.mode = mode === "whole" ? "whole" : "dynamic";
  dynamicButton.setAttribute("aria-pressed", String(camera.mode === "dynamic"));
  wholeButton.setAttribute("aria-pressed", String(camera.mode === "whole"));
  }
dynamicButton.addEventListener('click', () => setCameraMode("dynamic"));
wholeButton.addEventListener('click', () => setCameraMode("whole"));
allAlertsButton.setAttribute('aria-pressed', 'false');
allAlertsButton.addEventListener('click', () => {
  showAllAlerts = !showAllAlerts;
  allAlertsButton.setAttribute('aria-pressed', String(showAllAlerts));
  document.getElementById('recentAlertsPanel').setAttribute('data-all-alerts', String(showAllAlerts));
  hideTooltip();
  if (showAllAlerts) getVisibleAlerts();
  renderRecentAlerts();
  });
ssTrajectoryButton.disabled = true;
initialRefreshPromise.then(() => {
  ssTrajectoryButton.disabled = !latestAlertsAvailable;
  ssTrajectoryButton.title = latestAlertsAvailable
    ? 'Load recent LSST Solar System trajectories from Elasticsearch'
    : 'Available only in the FinkBrowser WAR';
  });
ssTrajectoryButton.addEventListener('click', () => {
  if (ssTrajectoryButton.disabled) return;
  if (ssTrajectoryEnabled) stopSsTrajectoryLoad();
  else startSsTrajectoryLoad();
  ssTrajectoryButton.setAttribute('aria-pressed', String(ssTrajectoryEnabled));
  });
setCameraMode("dynamic");

const helpButton = document.getElementById('helpButton');
const helpPanel = document.getElementById('logo-help');
function toggleHelp(forceOpen) {
  const open = (typeof forceOpen === "boolean") ? forceOpen : helpPanel.hidden;
  helpPanel.hidden = !open;
  helpButton.setAttribute("aria-expanded", String(open));
  }
helpButton.addEventListener('click', () => toggleHelp());
document.addEventListener('keydown', event => {
  if (event.key === "Escape") toggleHelp(false);
  });

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
  if (showAllAlerts) {
    // Do not paint duplicate animation flashes over persistent markers.
    flashes = flashes.filter(flash => Date.now() - flash.startTime < 10000);
    for (const marker of getVisibleAlerts()) marker.draw();
    }
  else flashes = flashes.filter(flash => flash.draw());
  drawSsTrajectories(getVisibleAlerts());
  updateLegend([...getVisibleAlerts(), ...ssVisibleMarkers]);
  drawOverview();
  requestAnimationFrame(animate);
  }

// Start
generateAlert();
animate();
window.addEventListener('resize', resizeCanvas);

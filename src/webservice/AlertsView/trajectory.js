// SS alerts arrive with the latest LSST alerts (LatestAlerts.jsp lists the newest
// ss_mjd objects). Elasticsearch's independently deduplicated MJD and position
// arrays cannot define an observation-level chronology, so each arriving SS
// object's history is loaded from SSTrajectory.jsp, which returns exact source
// IDs with paired MJD and sky coordinates from Fink REST. The alert itself is
// placed on the paired source matching its latest ES MJD; the SS trajectory
// button only toggles drawing of the history.
const ssTrajectories = new Map();
let ssTrajectoryEnabled = false;
let ssTrajectoryController = null;
let ssTrajectoryGeneration = 0;
let ssAlertCandidates = new Map();
let ssHistoryLoaded = 0;
let ssHistoryFailed = 0;
const SS_MJD_TOLERANCE = 0.00001;

function isSsDesignation(value) {
  return typeof value === 'string' && /^[A-Za-z0-9][A-Za-z0-9 ._()-]{0,63}$/.test(value.trim());
  }

function normalizeSsTrajectory(payload) {
  if (!payload || !/^\d{1,64}$/.test(String(payload.objectId))) {
    throw new TypeError('Invalid SS object ID');
    }
  if (!Array.isArray(payload.sources)) throw new TypeError('Invalid SS source list');
  if (payload.esCoverageComplete === false) throw new TypeError('Incomplete Elasticsearch coverage');
  const points = [];
  const seen = new Set();
  for (const source of payload.sources) {
    if (!source || typeof source !== 'object' ||
        typeof source.sourceId !== 'string' || !source.sourceId.trim()) continue;
    if (seen.has(source.sourceId)) throw new TypeError('Duplicate SS source ID');
    seen.add(source.sourceId);
    if ([source.mjd, source.ra, source.dec].some(value =>
      value == null || String(value).trim() === '')) continue;
    const mjd = Number(source.mjd);
    const ra = Number(source.ra);
    const dec = Number(source.dec);
    if (!Number.isFinite(mjd) || !Number.isFinite(ra) || !Number.isFinite(dec) ||
        mjd <= 0 || ra < 0 || ra > 360 || dec < -90 || dec > 90) continue;
    points.push({sourceId: source.sourceId, mjd, ra: ra % 360, dec});
    }
  points.sort((a, b) => a.mjd - b.mjd || a.sourceId.localeCompare(b.sourceId));
  const result = {objectId: String(payload.objectId),
          latestMjd: points.length ? points[points.length - 1].mjd : null,
          points};
  // The Fink Portal addresses Solar System objects by designation, never by ssObjectId.
  if (isSsDesignation(payload.designation)) result.designation = payload.designation.trim();
  return result;
  }

function matchesSsSource(marker, point) {
  if (marker.alert.survey !== 'LSST') return false;
  const sourceId = marker.alert.sourceId;
  if (sourceId != null && sourceId !== '') return String(sourceId) === point.sourceId;
  const mjd = Number(marker.alert.jd);
  return marker.alert.jd != null && marker.alert.jd !== '' &&
    Number.isFinite(mjd) && Math.abs(mjd - point.mjd) < 0.000001 &&
    Math.abs(signedRaDelta(marker.alert.ra, point.ra)) < 0.000001 &&
    Math.abs(marker.alert.dec - point.dec) < 0.000001;
  }

function isSsAlertOf(marker, trajectory) {
  return marker.alert.survey === 'LSST' && marker.alert.class === 'LSST SS source' &&
    String(marker.alert.objectId) === trajectory.objectId;
  }

// In Follow alerts mode a history is shown only while its SS alert flashes; with
// All alerts every loaded SS alert shows its history.
function isSsTrajectoryShown(trajectory, primaryMarkers) {
  if (typeof showAllAlerts !== 'undefined' && !showAllAlerts) {
    return primaryMarkers.some(marker => isSsAlertOf(marker, trajectory));
    }
  return true;
  }

// Use one visibility rule for canvas markers and the camera.
function getVisibleSsTrajectories(primaryMarkers) {
  if (!ssTrajectoryEnabled) return [];
  const fallbackColor = classes['LSST SS source'] || '255,200,80';
  return [...ssTrajectories.values()].filter(trajectory =>
    isSsTrajectoryShown(trajectory, primaryMarkers)).map(trajectory => {
    const matchingAlert = primaryMarkers.find(marker => isSsAlertOf(marker, trajectory)) ||
      primaryMarkers.find(marker => trajectory.points.some(point => matchesSsSource(marker, point)));
    const lastAtPosition = new Map();
    const positionKey = point => `${point.ra.toFixed(7)}:${point.dec.toFixed(7)}`;
    trajectory.points.forEach((point, index) => lastAtPosition.set(positionKey(point), index));
    const points = trajectory.points.flatMap((point, index) =>
      lastAtPosition.get(positionKey(point)) === index &&
      !primaryMarkers.some(marker => matchesSsSource(marker, point))
        ? [{point, index}] : []);
    return {trajectory, color: matchingAlert?.color || fallbackColor, points};
    });
  }

// Representative pseudo-alerts so the camera frames each whole trajectory (thinned: at most
// SS_CAMERA_POINTS per object, always keeping both ends and the dec/RA extremes).
const SS_CAMERA_POINTS = 120;
let ssCameraKey = null;
let ssCameraAnchors = [];
function getSsTrajectoryCameraAnchors(primaryMarkers = []) {
  if (!ssTrajectoryEnabled || ssTrajectories.size === 0) return [];
  const shown = [...ssTrajectories.values()].filter(trajectory =>
    isSsTrajectoryShown(trajectory, primaryMarkers));
  const key = shown.map(trajectory => `${trajectory.objectId}:${trajectory.points.length}`).join('|');
  if (key === ssCameraKey) return ssCameraAnchors;
  const anchors = [];
  for (const trajectory of shown) {
    const points = trajectory.points;
    if (!points.length) continue;
    const keep = new Set([0, points.length - 1]);
    const stride = Math.max(1, Math.ceil(points.length / SS_CAMERA_POINTS));
    for (let i = 0; i < points.length; i += stride) keep.add(i);
    for (const field of ['dec', 'ra']) {
      let low = 0, high = 0;
      points.forEach((point, i) => {
        if (point[field] < points[low][field]) low = i;
        if (point[field] > points[high][field]) high = i;
        });
      keep.add(low).add(high);
      }
    for (const i of keep) anchors.push({alert: {ra: points[i].ra, dec: points[i].dec}});
    }
  ssCameraKey = key;
  ssCameraAnchors = anchors;
  return anchors;
  }

function updateSsTrajectoryStatus(message) {
  const status = document.getElementById('ssTrajectoryStatus');
  if (status) {
    status.hidden = !message;
    status.textContent = message;
    }
  }

async function ssTrajectoryJson(url, signal) {
  const response = await fetch(url, {
    cache: 'no-store', headers: {accept: 'application/json'}, signal
    });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json();
  }

function describeSsHistoryStatus() {
  const total = ssAlertCandidates.size;
  if (!total) return ssTrajectoryEnabled ? 'SS alerts: none in the latest LSST alerts' : '';
  const loading = ssTrajectoryController ? ', loading…' : '';
  const failed = ssHistoryFailed ? ` (${ssHistoryFailed} failed)` : '';
  return `SS histories: ${ssHistoryLoaded}/${total}${failed}${loading}${ssTrajectoryEnabled ? '' : ' (hidden)'}`;
  }

function refreshSsViews() {
  if (typeof rebuildAlertsPool === 'function') rebuildAlertsPool();
  updateSsTrajectoryStatus(describeSsHistoryStatus());
  }

// Validate the SS objects of one latest-alerts response.
function normalizeLatestSsPayload(payload) {
  if (!payload || !Array.isArray(payload.ssMjdHits)) return [];
  const positions = new Map();
  for (const document of Array.isArray(payload.ssRadecDocs) ? payload.ssRadecDocs : []) {
    const location = document && document.found && document._source && document._source.location;
    // Only a single stored position is unambiguous without the paired history.
    if (document && document._id != null && location && !Array.isArray(location) &&
        Number.isFinite(location.lon) && Number.isFinite(location.lat) &&
        location.lat >= -90 && location.lat <= 90) {
      positions.set(String(document._id), {
        ra: ((location.lon + 180) % 360 + 360) % 360, dec: location.lat});
      }
    }
  const candidates = [];
  const seen = new Set();
  for (const hit of payload.ssMjdHits) {
    if (!hit || hit._id == null || !hit._source) continue;
    const objectId = String(hit._id);
    if (!/^\d{1,64}$/.test(objectId) || seen.has(objectId)) continue;
    const rawMjds = Array.isArray(hit._source.mjd) ? hit._source.mjd : [hit._source.mjd];
    const mjds = rawMjds.filter(value => Number.isFinite(value) && value > 0);
    if (!mjds.length) continue;
    seen.add(objectId);
    candidates.push({objectId, latestMjd: Math.max(...mjds), fallback: positions.get(objectId) || null});
    }
  return candidates;
  }

// The current source of an SS alert: the paired source at the alert's ES MJD,
// else the most recent paired source.
function currentSsPoint(trajectory, latestMjd) {
  const points = trajectory.points;
  for (let i = points.length - 1; i >= 0; i--) {
    if (Math.abs(points[i].mjd - latestMjd) <= SS_MJD_TOLERANCE) return points[i];
    }
  return points[points.length - 1];
  }

// Pool rows (same shape as the other LSST rows) for the SS alerts that can be placed.
function getSsAlertRows() {
  const rows = [];
  for (const candidate of ssAlertCandidates.values()) {
    const trajectory = ssTrajectories.get(candidate.objectId);
    const row = {"v:survey": "LSST", "v:classification": "LSST SS source",
      "r:ssObjectId": candidate.objectId};
    if (trajectory && trajectory.points.length) {
      const point = currentSsPoint(trajectory, candidate.latestMjd);
      Object.assign(row, {"r:diaSourceId": point.sourceId, "r:midpointMjdTai": point.mjd,
        "r:ra": point.ra, "r:dec": point.dec});
      if (trajectory.designation) row["v:designation"] = trajectory.designation;
      }
    else if (candidate.fallback) {
      Object.assign(row, {"r:midpointMjdTai": candidate.latestMjd,
        "r:ra": candidate.fallback.ra, "r:dec": candidate.fallback.dec});
      }
    else continue;
    rows.push(row);
    }
  return rows;
  }

// Called with the SS objects of every successful latest-LSST refresh: newly
// arrived (or newly observed) objects get their history; departed ones are dropped.
function updateSsAlertCandidates(candidates) {
  const generation = ++ssTrajectoryGeneration;
  ssTrajectoryController?.abort();
  ssTrajectoryController = null;
  const next = new Map();
  for (const candidate of candidates) next.set(candidate.objectId, candidate);
  for (const [id, trajectory] of ssTrajectories) {
    const candidate = next.get(id);
    // A newer detection than the cached history makes the cache stale.
    if (!candidate || trajectory.latestMjd == null ||
        trajectory.latestMjd < candidate.latestMjd - SS_MJD_TOLERANCE) {
      ssTrajectories.delete(id);
      }
    }
  ssAlertCandidates = next;
  ssHistoryFailed = 0;
  ssHistoryLoaded = [...next.keys()].filter(id => ssTrajectories.has(id)).length;
  const missing = [...next.keys()].filter(id => !ssTrajectories.has(id));
  if (!missing.length) {
    updateSsTrajectoryStatus(describeSsHistoryStatus());
    return Promise.resolve();
    }
  const controller = new AbortController();
  ssTrajectoryController = controller;
  updateSsTrajectoryStatus(describeSsHistoryStatus());
  return loadSsHistories(missing, generation, controller);
  }

async function loadSsHistories(ids, generation, controller) {
  try {
    // One at a time: SSTrajectory.jsp shares a few upstream slots.
    for (const id of ids) {
      if (generation !== ssTrajectoryGeneration) return;
      try {
        const payload = await ssTrajectoryJson(`SSTrajectory.jsp?id=${encodeURIComponent(id)}`, controller.signal);
        if (generation !== ssTrajectoryGeneration) return;
        if (String(payload.objectId) !== id) throw new TypeError('SS object ID mismatch');
        const trajectory = normalizeSsTrajectory(payload);
        if (!trajectory.points.length) throw new TypeError('Empty SS history');
        ssTrajectories.set(id, trajectory);
        ssHistoryLoaded++;
        }
      catch (error) {
        if (generation !== ssTrajectoryGeneration || error.name === 'AbortError') return;
        ssHistoryFailed++;
        console.error('Cannot load SS history', id, error);
        }
      refreshSsViews();
      }
    }
  finally {
    if (ssTrajectoryController === controller) {
      ssTrajectoryController = null;
      if (generation === ssTrajectoryGeneration) refreshSsViews();
      }
    }
  }

// The SS trajectory button shows or hides the histories; SS alerts stay in the stream.
function startSsTrajectoryLoad() {
  ssTrajectoryEnabled = true;
  updateSsTrajectoryStatus(describeSsHistoryStatus());
  }

function stopSsTrajectoryLoad() {
  ssTrajectoryEnabled = false;
  updateSsTrajectoryStatus(describeSsHistoryStatus() || 'SS trajectories hidden');
  }

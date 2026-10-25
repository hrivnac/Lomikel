// Elasticsearch selects SS objects, but its independently deduplicated MJD
// and position arrays cannot define an observation-level chronology. The JSP
// returns exact source IDs with paired MJD and sky coordinates from Fink REST.
const ssTrajectories = new Map();
let ssTrajectoryEnabled = false;
let ssTrajectoryController = null;
let ssTrajectoryGeneration = 0;

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
  return {objectId: String(payload.objectId),
          latestMjd: points.length ? points[points.length - 1].mjd : null,
          points};
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

// Use one visibility rule for canvas markers and the All loaded alerts list.
function getVisibleSsTrajectories(primaryMarkers) {
  if (!ssTrajectoryEnabled) return [];
  const fallbackColor = classes['LSST SS source'] || '255,200,80';
  return [...ssTrajectories.values()].map(trajectory => {
    const matchingAlert = primaryMarkers.find(marker =>
      trajectory.points.some(point => matchesSsSource(marker, point)));
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

async function startSsTrajectoryLoad() {
  if (ssTrajectoryController) return;
  ssTrajectoryEnabled = true;
  ssTrajectories.clear();
  if (typeof showAllAlerts !== 'undefined' && showAllAlerts) renderRecentAlerts();
  const generation = ++ssTrajectoryGeneration;
  const controller = new AbortController();
  ssTrajectoryController = controller;
  let loaded = 0;
  let failed = 0;
  try {
    updateSsTrajectoryStatus('Loading SS trajectories…');
    const limit = Math.max(1, Math.min(100, Number(nAlerts) || 10));
    const listing = await ssTrajectoryJson(`SSTrajectory.jsp?list=1&n=${limit}`, controller.signal);
    if (generation !== ssTrajectoryGeneration) return;
    if (!listing || !Array.isArray(listing.ids)) throw new TypeError('Invalid SS object list');
    const ids = [...new Set(listing.ids.map(String))].filter(id => /^\d{1,64}$/.test(id)).slice(0, limit);
    for (const id of ids) {
      if (generation !== ssTrajectoryGeneration) return;
      try {
        const payload = await ssTrajectoryJson(`SSTrajectory.jsp?id=${encodeURIComponent(id)}`, controller.signal);
        if (generation !== ssTrajectoryGeneration) return;
        if (String(payload.objectId) !== id) throw new TypeError('SS object ID mismatch');
        const trajectory = normalizeSsTrajectory(payload);
        if (trajectory.points.length) {
          ssTrajectories.set(id, trajectory);
          loaded++;
          if (typeof showAllAlerts !== 'undefined' && showAllAlerts) renderRecentAlerts();
          }
        }
      catch (error) {
        if (generation !== ssTrajectoryGeneration || error.name === 'AbortError') return;
        failed++;
        console.error('Cannot load SS trajectory', id, error);
        }
      updateSsTrajectoryStatus(`SS trajectories: ${loaded}/${ids.length}${failed ? ` (${failed} failed)` : ''}`);
      }
    }
  catch (error) {
    if (generation === ssTrajectoryGeneration && error.name !== 'AbortError') {
      updateSsTrajectoryStatus(`SS trajectories unavailable: ${error.message}`);
      console.error('Cannot load SS trajectories', error);
      }
    }
  finally {
    if (ssTrajectoryController === controller) ssTrajectoryController = null;
    }
  }

function stopSsTrajectoryLoad() {
  ssTrajectoryEnabled = false;
  ssTrajectoryGeneration++;
  ssTrajectoryController?.abort();
  ssTrajectoryController = null;
  ssTrajectories.clear();
  if (typeof showAllAlerts !== 'undefined' && showAllAlerts) renderRecentAlerts();
  updateSsTrajectoryStatus('SS trajectories hidden');
  }

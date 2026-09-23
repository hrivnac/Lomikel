// Alerts
let alertsPool = [];
const surveySnapshots = {ZTF: new Map(), LSST: new Map()};
const surveyStatus = {
  ZTF:  {state: "idle", count: 0, errors: [], updatedAt: null},
  LSST: {state: "paused", count: 0, errors: [], updatedAt: null}
  };
const refreshInProgress = new Map();
let alertConfigGeneration = 0;
let refreshTimer = null;

function normalizeLatestLsstPayload(payload) {
  if (!payload || !Array.isArray(payload.mjdHits) || !Array.isArray(payload.radecDocs)) {
    throw new TypeError("invalid latest LSST response");
    }
  const positions = new Map();
  for (const document of payload.radecDocs) {
    const location = document && document.found && document._source && document._source.location;
    if (document && document._id != null && location && !Array.isArray(location) &&
        Number.isFinite(location.lon) && Number.isFinite(location.lat)) {
      positions.set(String(document._id), location);
      }
    }
  const alerts = [];
  for (const hit of payload.mjdHits) {
    if (!hit || hit._id == null || !hit._source) continue;
    const objectId = String(hit._id);
    const location = positions.get(objectId);
    if (!location) continue;
    const rawMjds = Array.isArray(hit._source.mjd) ? hit._source.mjd : [hit._source.mjd];
    const mjds = rawMjds.filter(Number.isFinite);
    if (!mjds.length) continue;
    alerts.push({
      "r:diaObjectId": objectId,
      "r:midpointMjdTai": Math.max(...mjds),
      "r:ra": ((location.lon + 180) % 360 + 360) % 360,
      "r:dec": location.lat,
      "v:classification": "LSST DIA source"
      });
    }
  return alerts;
  }

function rebuildAlertsPool() {
  alertsPool = [...surveySnapshots.ZTF.values(), ...surveySnapshots.LSST.values()];
  }

function alertIdentity(survey, alert) {
  const objectId = (survey === "LSST") ? alert['r:diaObjectId'] : alert['i:objectId'];
  const timestamp = (survey === "LSST") ? alert['r:midpointMjdTai'] : alert['i:jd'];
  return `${survey}:${objectId}:${timestamp}`;
  }

function replaceSurveySnapshot(survey, alerts) {
  const snapshot = new Map();
  for (const alert of alerts) {
    snapshot.set(alertIdentity(survey, alert), alert);
    }
  surveySnapshots[survey] = snapshot;
  rebuildAlertsPool();
  }

async function fetchAlerts(survey) {
  if (refreshInProgress.has(survey)) return false;
  const controller = new AbortController();
  const generation = alertConfigGeneration;
  const requestSettings = {fetchStart, nAlerts};
  refreshInProgress.set(survey, controller);
  const allAlerts = [];
  const errors = [];
  let successfulRequests = 0;
  surveyStatus[survey] = {...surveyStatus[survey], state: "loading", errors: []};
  updateStatusPanel();
  const latestMode = requestSettings.fetchStart === 0;
  const startdate = latestMode ? null : formatStartDateUtc(requestSettings.fetchStart);
  const classMap = (survey === "LSST") ? classesLSSTTags : classesZTF;
  const requests = (survey === "LSST" && latestMode)
    ? [{classification: null, latestLsst: true, url: `LatestAlerts.jsp?n=${encodeURIComponent(requestSettings.nAlerts)}`}]
    : Object.keys(classMap).map(classification => {
        const startdateParam = latestMode ? "" : `&startdate=${encodeURIComponent(startdate)}`;
        const url = (survey === "LSST")
          ? `https://api.lsst.fink-portal.org/api/v1/tags?tag=${encodeURIComponent(classification)}&n=${encodeURIComponent(requestSettings.nAlerts)}&columns=r%3AdiaObjectId%2Cr%3AmidpointMjdTai%2Cr%3Ara%2Cr%3Adec${startdateParam}&output-format=json`
          : `https://api.ztf.fink-portal.org/api/v1/latests?class=${encodeURIComponent(classification)}&n=${encodeURIComponent(requestSettings.nAlerts)}&columns=i%3AobjectId%2Ci%3Ajd%2Ci%3Ara%2Ci%3Adec${startdateParam}&output-format=json`;
        return {classification, url};
        });
  try {
    for (const request of requests) {
      try {
        const response = await fetch(request.url, {
          headers: {"accept": "application/json"},
          signal: controller.signal
          });
        if (generation !== alertConfigGeneration) return false;
        if (!response.ok) {
          errors.push(`${request.classification || "latest"}: HTTP ${response.status}`);
          continue;
          }
        const payload = await response.json();
        const data = request.latestLsst ? normalizeLatestLsstPayload(payload) : payload;
        if (!Array.isArray(data)) {
          errors.push(`${request.classification || "latest"}: invalid response`);
          continue;
          }
        successfulRequests += 1;
        data.forEach(alert => {
          alert["v:survey"] = survey;
          if (!alert["v:classification"]) alert["v:classification"] = request.classification;
          });
        allAlerts.push(...data);
        }
      catch (err) {
        if (err.name === "AbortError" || generation !== alertConfigGeneration) return false;
        errors.push(`${request.classification || "latest"}: ${err.message}`);
        }
      }
    if (generation !== alertConfigGeneration) return false;
    const updatedAt = new Date();
    if (successfulRequests > 0) {
      replaceSurveySnapshot(survey, allAlerts);
      const count = surveySnapshots[survey].size;
      const state = errors.length > 0 ? "partial" : (count > 0 ? "ready" : "empty");
      surveyStatus[survey] = {state, count, errors, updatedAt};
      }
    else {
      surveyStatus[survey] = {
        state: "error",
        count: surveySnapshots[survey].size,
        errors,
        updatedAt
        };
      }
    console.log(`Fetched ${surveyStatus[survey].count} unique ${survey} alerts from Fink Portal`);
    return true;
    }
  finally {
    if (refreshInProgress.get(survey) === controller) {
      refreshInProgress.delete(survey);
      }
    if (generation === alertConfigGeneration) updateStatusPanel();
    }
  }

function invalidateAlertRefreshes() {
  alertConfigGeneration += 1;
  for (const controller of refreshInProgress.values()) controller.abort();
  refreshInProgress.clear();
  for (const survey of ["ZTF", "LSST"]) {
    if (surveyStatus[survey].state === "loading") {
      surveyStatus[survey] = {
        ...surveyStatus[survey],
        state: survey === "LSST" && !fetchLSST ? "paused" : "idle",
        errors: []
        };
      }
    }
  }

async function refreshEnabledSurveys() {
  const refreshes = [fetchAlerts("ZTF")];
  if (fetchLSST) {
    if (surveyStatus.LSST.state === "paused") {
      surveyStatus.LSST = {state: "idle", count: 0, errors: [], updatedAt: null};
      }
    refreshes.push(fetchAlerts("LSST"));
    }
  await Promise.all(refreshes);
  }

function scheduleRefreshTimer() {
  if (refreshTimer !== null) {
    clearInterval(refreshTimer);
    refreshTimer = null;
    }
  if (fetchPeriod === 0) return;
  refreshTimer = setInterval(refreshEnabledSurveys, fetchPeriod * 60 * 1000);
  }

getQueryParams();
if (!fetchLSST) {
  surveyStatus.LSST = {state: "paused", count: 0, errors: [], updatedAt: null};
  }
updateStatusPanel();
const initialRefreshPromise = refreshEnabledSurveys();
scheduleRefreshTimer();

// Constellations
let constellations = [];
fetch("constellations.lines.json").then(response => response.json()).
                                   then(data => {constellations = data;});

// Stars
const stars = [];
const stellarCatalog = [];
function rebuildStars() {
  stars.length = 0;
  for (const row of stellarCatalog) {
    if (row.ra !== 0 && row.mag < magMax) {
      stars.push({
        ra: row.ra,
        dec: row.dec,
        r: Math.max(0.5, 2.5 - row.mag * 0.2),
        proper: row.proper,
        alpha: Math.max(0, 1 - row.mag * 0.05),
        twinkleSpeed: Math.max(0, 0.1 * (1 - row.mag * 0.05))
        });
      }
    }
  }

function parseStellarCatalog(csvText) {
  const lines = csvText.trim().split(/\r?\n/);
  if (lines.shift() !== "ra,dec,mag,proper") throw new Error("Unexpected stellar catalogue columns");
  return lines.map(line => {
    const [ra, dec, mag, proper] = line.split(",");
    return {ra: Number(ra), dec: Number(dec), mag: Number(mag), proper};
    });
  }

fetch('hyg_v38_mag6.csv').
  then(response => {
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return response.text();
    }).
  then(csvText => {
    for (const row of parseStellarCatalog(csvText)) {
      stellarCatalog.push({...row, ra: row.ra * 15});
      }
    rebuildStars();
    }).
  catch(error => console.error("Cannot load stellar catalogue", error));

// Alerts
let alertsPool = [];
const surveySnapshots = {ZTF: new Map(), LSST: new Map()};
const surveyStatus = {
  ZTF:  {state: "idle", count: 0, errors: [], updatedAt: null},
  LSST: {state: "paused", count: 0, errors: [], updatedAt: null}
  };
const refreshInProgress = new Set();

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
  refreshInProgress.add(survey);
  const allAlerts = [];
  const errors = [];
  let successfulRequests = 0;
  surveyStatus[survey] = {...surveyStatus[survey], state: "loading", errors: []};
  updateStatusPanel();
  const startdate = getStartDateParam();
  const classMap = (survey === "LSST") ? classesLSST : classesZTF;
  try {
    for (const cls of Object.keys(classMap)) {
      const url = (survey === "LSST")
        ? `https://api.lsst.fink-portal.org/api/v1/tags?tag=${encodeURIComponent(cls)}&n=${encodeURIComponent(nAlerts)}&columns=r%3AdiaObjectId%2Cr%3AmidpointMjdTai%2Cr%3Ara%2Cr%3Adec&startdate=${encodeURIComponent(startdate)}&output-format=json`
        : `https://api.ztf.fink-portal.org/api/v1/latests?class=${encodeURIComponent(cls)}&n=${encodeURIComponent(nAlerts)}&columns=i%3AobjectId%2Ci%3Ajd%2Ci%3Ara%2Ci%3Adec&startdate=${encodeURIComponent(startdate)}&output-format=json`;
      try {
        const response = await fetch(url, {headers: {"accept": "application/json"}});
        if (!response.ok) {
          errors.push(`${cls}: HTTP ${response.status}`);
          continue;
          }
        const data = await response.json();
        if (!Array.isArray(data)) {
          errors.push(`${cls}: invalid response`);
          continue;
          }
        successfulRequests += 1;
        data.forEach(alert => {
          alert["v:survey"] = survey;
          alert["v:classification"] = cls;
          });
        allAlerts.push(...data);
        }
      catch (err) {
        errors.push(`${cls}: ${err.message}`);
        }
      }
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
    refreshInProgress.delete(survey);
    updateStatusPanel();
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

getQueryParams();
if (!fetchLSST) {
  surveyStatus.LSST = {state: "paused", count: 0, errors: [], updatedAt: null};
  }
updateStatusPanel();
const initialRefreshPromise = refreshEnabledSurveys();
const refreshTimer = setInterval(refreshEnabledSurveys, fetchPeriod * 60 * 1000);

// Constellations
let constellations = [];
fetch("constellations.lines.json").then(response => response.json()).
                                   then(data => {constellations = data;});

// Stars
const stars = [];
d3.csv('hyg_v38_mag6.csv').
   then(data => {
     data.forEach(row => {
       const ra     = row.ra * 15;
       const dec    = row.dec;
       const mag    = row.mag;
       const proper = row.proper;
       if (ra != 0 && mag < magMax) {
         stars.push({ra: ra,
                     dec: dec,
                     r: Math.max(0.5, 2.5 - mag * 0.2),
                     proper: proper,
                     alpha:  Math.max(0, 1 - mag * 0.05),
                     twinkleSpeed: Math.max(0, 0.1 * (1 - mag * 0.05))});
         }
       });
     });

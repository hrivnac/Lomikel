function generateDemoData(snid = "random") {
  const n = 70;
  const baseJD = 2460000;
  // base irregular timeline
  const baseTimes = []; let t = 0;
  for (let k = 0; k < n; k++){
    t += 1 + Math.random() * 5;
    baseTimes.push(baseJD + t);
    }
  const data = {};
  filters.forEach(f => {
    data[f] = {
      times: [],
      values: []
      };
    });
  // different shapes per filter; drop points independently (gaps per filter)
  baseTimes.forEach((jd, idx) => {
    const tt = idx; // shape parameter
    // per-filter missingness
    const keep = {
      Y: Math.random() > 0.12,
      z: Math.random() > 0.18,
      g: Math.random() > 0.15,
      i: Math.random() > 0.20,
      u: Math.random() > 0.22,
      r: Math.random() > 0.14,
      };
    let shapes;
    if (snid == "peak") {
      shapes = {
        Y: 18 - 1.8 * Math.exp(-Math.pow((tt - 40) / 12, 2)) + 0.05 * Math.random(),
        z: 18 - 1.0 * Math.exp(-Math.pow((tt - 40) / 8, 2)) + 0.05 * Math.random(),
        g: 18 - 2.2 * Math.exp(-Math.pow((tt - 40) / 12, 2)) + 0.05 * Math.random(),
        i: 18 - 1.8 * Math.exp(-Math.pow((tt - 40) / 20, 2)) + 0.05 * Math.random(),
        u: 18 - 2.5 * Math.exp(-Math.pow((tt - 40) / 9, 2)) + 0.05 * Math.random(),
        r: 18 - 2.0 * Math.exp(-Math.pow((tt - 40) / 15, 2)) + 0.05 * Math.random(),
        };
      }
    else if (snid == "periodic") {
      shapes = {
        Y: 18 + 0.2 * Math.sin(tt / 7) + 0.015 * Math.random(),
        z: 18 + 0.5 * Math.sin(tt / 5) + 0.05 * Math.random(),
        g: 18 - 0.9 * Math.exp(-Math.pow((tt - 25) / 9, 2)) +  0.6 * Math.exp(-Math.pow((tt - 60) / 10, 2)) + 0.05 * Math.random(),
        i: 18 + ((tt % 30) < 15 ? (tt % 30) / 15 : (30 - (tt % 30)) / 15) - 0.5 + 0.04 * Math.random(), // triangle-ish
        u: 18 - 0.9 * Math.exp(-Math.pow((tt - 15) / 7, 2)) +  0.6 * Math.exp(-Math.pow((tt - 40) / 10, 2)) + 0.15 * Math.random(),
        r: 18 + 0.4 * Math.cos(tt / 6) + 0.004 * tt + 0.05 * Math.random(),
        };
      }
    else {    
      shapes = {
        Y: 18 - 1.8 * Math.exp(-Math.pow((tt - 40) / 12, 2)) + 0.05 * Math.random(), // Gaussian dip (magnitudes)
        z: 18 + 0.5 * Math.sin(tt / 5) + 0.05 * Math.random(),
        g: 18 - 0.9 * Math.exp(-Math.pow((tt - 25) / 9, 2)) +  0.6 * Math.exp(-Math.pow((tt - 60) / 10, 2)) + 0.05 * Math.random(),
        i: 18 + ((tt % 30) < 15 ? (tt % 30) / 15 : (30 - (tt % 30)) / 15) - 0.5 + 0.04 * Math.random(), // triangle-ish
        u: 18 + 0.0008 * tt * tt - 0.06 * tt + 0.1 * Math.random(),
        r: 18 + 0.4 * Math.cos(tt / 6) + 0.004 * tt + 0.05 * Math.random(),
        };
      }
    filters.forEach(f => {
      if (keep[f]) {
        data[f].times.push(jd);
        data[f].values.push(shapes[f]);
        }
      });
    });
  return normalizeLightcurve(data);
  }

function fluxNjyToAbMagnitude(flux) {
  if (!Number.isFinite(flux) || flux <= 0) return null;
  return 31.4 - 2.5 * Math.log10(flux);
  }

function normalizeLsstObjectId(value) {
  if (typeof value !== "string") return null;
  const objectId = value.trim();
  return /^[0-9]{1,30}$/.test(objectId) ? objectId : null;
  }

function finkSourcesToLightcurve(rows) {
  const data = Object.fromEntries(filters.map(band => [band, {times: [], values: []}]));
  if (!Array.isArray(rows)) return normalizeLightcurve(data);
  for (const row of rows) {
    const sourceBand = row && row["r:band"];
    const band = sourceBand === "y" ? "Y" : sourceBand;
    const time = row && row["r:midpointMjdTai"];
    const magnitude = fluxNjyToAbMagnitude(row && row["r:psfFlux"]);
    if (!filters.includes(band) || !Number.isFinite(time) || magnitude === null) continue;
    data[band].times.push(time);
    data[band].values.push(magnitude);
    }
  return normalizeLightcurve(data);
  }
 
let lightcurve = null;
let loadGeneration = 0;
const FINK_LSST_SOURCES_URL = "https://api.lsst.fink-portal.org/api/v1/sources";
const FINK_LSST_COLUMNS = "r:diaObjectId,r:midpointMjdTai,r:band,r:psfFlux,r:psfFluxErr";
const FINK_RESOLVER_URL = "https://api.lsst.fink-portal.org/api/v1/resolver";
const FINK_SSO_URL = "https://api.lsst.fink-portal.org/api/v1/sso";
const FINK_SSO_COLUMNS = "r:ssObjectId,r:midpointMjdTai,r:band,r:psfFlux,r:psfFluxErr";
const FINK_REQUEST_TIMEOUT_MS = 15000;
const FINK_SSO_TIMEOUT_MS = 90000;

function setLoadStatus(message, state = "ready") {
  if (typeof document === "undefined") return;
  const status = document.getElementById("status");
  if (!status) return;
  status.textContent = message;
  status.dataset.state = state;
  }

function setFinkPortalLink(objectId = null) {
  if (typeof document === "undefined") return;
  const link = document.getElementById("fink-portal-link");
  if (!link) return;
  link.href = objectId ? `https://lsst.fink-portal.org/${objectId}` : "";
  link.hidden = !objectId;
  }

// Fink returns /sso r:ssObjectId as a JSON number; IDs above 2^53 are rounded when parsed in JavaScript,
// so a numeric value can only be compared at double precision. Strings are compared exactly.
function sameSolarSystemId(value, objectId) {
  if (typeof value === "string") return value === objectId;
  return typeof value === "number" && Number.isFinite(value) && value === Number(objectId);
  }

async function postFink(url, body, controller) {
  const response = await fetch(url, {
    method: "POST",
    headers: {"Content-Type": "application/json"},
    body: JSON.stringify(body),
    ...(controller ? {signal: controller.signal} : {}),
    });
  if (!response.ok) throw new Error(`HTTP ${response.status || "error"}`);
  return response.json();
  }

// Solar System objects have no diaObjectId history: /sources answers [] for an ssObjectId and
// /sso accepts only a designation. Reverse-resolve the exact ID, then require every row to match it.
async function fetchSolarSystemRows(objectId, controller, armTimeout) {
  const resolved = await postFink(FINK_RESOLVER_URL, {
    resolver: "ssodnet", name_or_id: objectId, reverse: true, nmax: 10, "output-format": "json",
    }, controller);
  if (!Array.isArray(resolved) || resolved.length > 10) throw new Error("Invalid resolver reply");
  const matches = resolved.filter(row => row && sameSolarSystemId(row["r:ssObjectId"], objectId));
  if (matches.length === 0) return [];
  if (matches.length > 1) throw new Error("Ambiguous Solar System object");
  const designation = String(matches[0]["r:packed_primary_provisional_designation"] ||
    matches[0]["r:unpacked_primary_provisional_designation"] || "").trim();
  if (!designation || designation.length > 128) throw new Error("Solar System designation unavailable");
  armTimeout(FINK_SSO_TIMEOUT_MS);
  const rows = await postFink(FINK_SSO_URL, {
    n_or_d: designation, columns: FINK_SSO_COLUMNS, "output-format": "json",
    }, controller);
  if (!Array.isArray(rows) || rows.some(row => !row || !sameSolarSystemId(row["r:ssObjectId"], objectId))) {
    throw new Error("Unexpected Solar System rows");
    }
  return rows;
  }

async function loadFinkObject(value) {
  const objectId = normalizeLsstObjectId(value);
  if (!objectId) {
    setLoadStatus("Enter an LSST object ID using digits only.", "error");
    return false;
    }
  const generation = ++loadGeneration;
  const controller = typeof AbortController === "function" ? new AbortController() : null;
  let timeoutId = null;
  const armTimeout = ms => {
    if (timeoutId !== null && typeof clearTimeout === "function") clearTimeout(timeoutId);
    timeoutId = controller && typeof setTimeout === "function"
      ? setTimeout(() => controller.abort(), ms)
      : null;
    };
  armTimeout(FINK_REQUEST_TIMEOUT_MS);
  setLoadStatus(`Loading Fink object ${objectId}…`, "loading");
  try {
    const response = await fetch(FINK_LSST_SOURCES_URL, {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({
        diaObjectId: objectId,
        columns: FINK_LSST_COLUMNS,
        "output-format": "json",
        }),
      ...(controller ? {signal: controller.signal} : {}),
      });
    if (!response.ok) throw new Error(`HTTP ${response.status || "error"}`);
    let rows = await response.json();
    if (generation !== loadGeneration) return false;
    let solarSystem = false;
    if (Array.isArray(rows) && rows.length === 0) {
      rows = await fetchSolarSystemRows(objectId, controller, armTimeout);
      if (generation !== loadGeneration) return false;
      solarSystem = true;
      }
    if (!Array.isArray(rows) || rows.length === 0) throw new Error("Object not found");
    const loaded = finkSourcesToLightcurve(rows);
    const sourceCount = filters.reduce((count, band) => count + loaded[band].times.length, 0);
    if (!sourceCount) throw new Error("No positive-flux sources");
    lightcurve = loaded;
    resetRandom();
    activeSNID = `fink:${objectId}`;
    updateSNIDHighlight();
    plotLightCurves(lightcurve);
    setFinkPortalLink(objectId);
    const availableBands = filters.filter(band => loaded[band].times.length > 0);
    const missingBands = filters.filter(band => loaded[band].times.length === 0);
    if (missingBands.length) {
      setLoadStatus(
        `Loaded Fink ${solarSystem ? "Solar System " : ""}object ${objectId}: ${sourceCount} sources. Missing ${missingBands.join(", ")}; projection uses ${availableBands.join(", ")}.`,
        "warning"
        );
      }
    else {
      setLoadStatus(`Loaded Fink ${solarSystem ? "Solar System " : ""}object ${objectId}: ${sourceCount} sources.`);
      }
    return true;
    }
  catch (error) {
    if (generation === loadGeneration) {
      const message = error && error.name === "AbortError"
        ? `Fink request for ${objectId} timed out.`
        : `Failed to load Fink object ${objectId}.`;
      setLoadStatus(message, "error");
      }
    return false;
    }
  finally {
    if (timeoutId !== null && typeof clearTimeout === "function") clearTimeout(timeoutId);
    }
  }

async function loadSNID(snid) {
  const generation = ++loadGeneration;
  setLoadStatus(`Loading sample ${snid}…`, "loading");
  try {
    const response = await fetch(`${snid}.json`);
    if (!response.ok) {
      throw new Error(`HTTP ${response.status || "error"}`);
      }
    const loaded = normalizeLightcurve(await response.json());
    if (generation !== loadGeneration) return;
    lightcurve = loaded;
    resetRandom();
    activeSNID = String(snid);
    updateSNIDHighlight();
    plotLightCurves(lightcurve);
    setFinkPortalLink();
    setLoadStatus(`Loaded sample ${snid}.`);
    }
  catch (error) {
    if (generation === loadGeneration) {
      setLoadStatus(`Failed to load sample ${snid}.`, "error");
      }
    }
  }

function loadDemo(kind) {
  loadGeneration++;
  lightcurve = generateDemoData(kind);
  resetRandom();
  activeSNID = String(kind);
  updateSNIDHighlight();
  plotLightCurves(lightcurve);
  setFinkPortalLink();
  setLoadStatus(`Generated ${kind} demo.`);
  }

// All startup values live here; HTML carries only the available controls.
const DEFAULTS = Object.freeze({ objectId: "ZTF17aackceb", nmax: 20,
  metric: "JensenShannon", classifier: "FINK", reclassifier: "none" });
const DEFAULT_OBJECT_IDS = Object.freeze({
  LSST: "170028486134595648",
  ZTF: DEFAULTS.objectId,
});
const CLASSIFIERS = Object.freeze({
  LSST: ["FINK", "TAG"],
  ZTF: ["FINK", "XMATCH", "FEATURES=2025/13-50", "FEATURES=2024/13-60", "LIGHTCURVES=Latent", "TAG"],
});
let catalogRequest = 0;

function surveyForObjectId(id) {
  const value = String(id).trim();
  if (/^ZTF/i.test(value)) return "ZTF";
  if (/^[0-9]+$/.test(value)) return "LSST";
  return null;
}

function populateClassifierSelect(select, choices, fallback) {
  const previous = select.value;
  select.replaceChildren(...choices.map((value) => {
    const option = document.createElement("option");
    option.value = value;
    option.textContent = value.replace("=", " ");
    return option;
  }));
  select.value = choices.includes(previous) ? previous : fallback;
}

function setClassifiers(survey, records = []) {
  const fallback = CLASSIFIERS[survey];
  const found = records.filter((row) => row &&
    (row.survey === survey || row.survey === "ANY") &&
    typeof row.classifier === "string")
    .map((row) => row.flavor ? `${row.classifier}=${row.flavor}` : row.classifier)
    .filter((value) => /^(?:FINK|XMATCH|TAG|FEATURES=[A-Za-z0-9._/-]+|LIGHTCURVES=[A-Za-z0-9._/-]+)$/.test(value));
  const choices = [...fallback, ...[...new Set(found)].filter((value) => !fallback.includes(value)).sort()];
  populateClassifierSelect(document.getElementById("classifier"), choices, DEFAULTS.classifier);
  populateClassifierSelect(document.getElementById("reclassifier"), ["none", ...choices], DEFAULTS.reclassifier);
}

async function refreshClassifiers(survey) {
  const request = ++catalogRequest;
  setClassifiers(survey);
  try {
    const response = await fetch(`/FinkBrowser/Classifiers.jsp?survey=${encodeURIComponent(survey)}`);
    if (!response.ok) throw new Error(`Classifier catalog: HTTP ${response.status}`);
    const records = await response.json();
    if (request === catalogRequest && surveyInput.value === survey)
      setClassifiers(survey, Array.isArray(records) ? records : []);
  } catch (error) {
    if (request === catalogRequest) console.warn("Using classifier fallback catalog:", error);
  }
}

const startupParameters = new URLSearchParams(window.location.search);
const startupSurvey = startupParameters.get("survey")?.toUpperCase();
const startupObjectId = startupParameters.get("objectId");
const surveyInput = document.getElementById("survey");
const objectIdInput = document.getElementById("objectId");
const initialSurvey = surveyForObjectId(startupObjectId || "");
surveyInput.value = initialSurvey || (Object.hasOwn(DEFAULT_OBJECT_IDS, startupSurvey) ? startupSurvey : "ZTF");
objectIdInput.value = startupObjectId || DEFAULT_OBJECT_IDS[surveyInput.value];
document.getElementById("nmaxValue").value = String(DEFAULTS.nmax);
document.getElementById("metric").value = DEFAULTS.metric;
refreshClassifiers(surveyInput.value);

function clearNeighborhood() {
  invalidateNeighborhoodLoad();
  document.getElementById("viz").replaceChildren();
  document.getElementById("objectList").textContent = "No alerts loaded.";
  document.getElementById("resetBtn").disabled = true;
  hideTooltip(0);
}

function syncSurveyFromId(id) {
  const survey = surveyForObjectId(id);
  if (survey && surveyInput.value !== survey) {
    surveyInput.value = survey;
    clearNeighborhood();
    refreshClassifiers(survey);
  }
  return survey;
}

objectIdInput.addEventListener("input", () => {
  // A pending request must not render under a newly typed identifier.
  invalidateNeighborhoodLoad();
  syncSurveyFromId(objectIdInput.value);
});
surveyInput.addEventListener("change", () => {
  clearNeighborhood();
  if (surveyForObjectId(objectIdInput.value) !== surveyInput.value) {
    objectIdInput.value = DEFAULT_OBJECT_IDS[surveyInput.value];
  }
  refreshClassifiers(surveyInput.value);
  setStatus(`Survey changed to ${surveyInput.value}. Press Show neighborhood to load its graph.`, "idle");
});
setStatus("Choose parameters, then press Show neighborhood. Graph queries can take several seconds.", "idle");

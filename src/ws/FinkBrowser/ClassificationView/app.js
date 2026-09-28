// Page defaults live here, not in index.html or the server-side JSP.
const DEFAULTS = Object.freeze({objectId: "ZTF17aackceb", nmax: 20.0,
                                metric: "JensenShannon", classifier: "FINK",
                                reclassifier: "none"});
const CLASSIFIERS = Object.freeze({
  LSST: ["FINK", "TAG"],
  ZTF: ["FINK", "XMATCH", "FEATURES=2025/13-50", "FEATURES=2024/13-60", "TAG"]
});
let catalogRequest = 0;

function surveyForObjectId(id) {
  const value = id.trim();
  if (/^ZTF/i.test(value)) return "ZTF";
  if (/^[0-9]+$/.test(value)) return "LSST";
  return null;
}

function populateClassifierSelect(select, choices, fallback) {
  const previous = select.value;
  select.replaceChildren(...choices.map(value => {
    const option = document.createElement("option");
    option.value = value;
    option.textContent = value.replace("=", " ");
    return option;
  }));
  select.value = choices.includes(previous) ? previous : fallback;
}

function setClassifiers(survey, records = []) {
  const expected = CLASSIFIERS[survey];
  // Keep required choices when the graph is temporarily unavailable or has
  // not yet imported a particular flavor. Ignore records from other surveys.
  const found = records.filter(row => row &&
      (row.survey === survey || row.survey === "ANY") &&
      typeof row.classifier === "string")
    .map(row => row.flavor ? `${row.classifier}=${row.flavor}` : row.classifier);
  const extraFlavors = survey === "ZTF" ? found.filter(value =>
    /^FEATURES=[A-Za-z0-9._/-]+$/.test(value) && !expected.includes(value)).sort() : [];
  const complete = [...expected, ...new Set(extraFlavors)];
  populateClassifierSelect(document.getElementById("classifier"), complete, DEFAULTS.classifier);
  populateClassifierSelect(document.getElementById("reclassifier"), ["none", ...complete], DEFAULTS.reclassifier);
}

async function refreshClassifiers(survey) {
  const request = ++catalogRequest;
  setClassifiers(survey);
  try {
    const response = await fetch(`/FinkBrowser/Classifiers.jsp?survey=${encodeURIComponent(survey)}`);
    if (!response.ok) throw new Error(`Classifier catalog: HTTP ${response.status}`);
    const records = await response.json();
    if (request === catalogRequest && document.getElementById("survey").value === survey)
      setClassifiers(survey, Array.isArray(records) ? records : []);
  } catch (error) {
    console.warn("Using classifier fallback catalog:", error);
  }
}

function syncSurveyFromId(id) {
  const survey = surveyForObjectId(id);
  if (survey && document.getElementById("survey").value !== survey) {
    document.getElementById("survey").value = survey;
    refreshClassifiers(survey);
  }
  return survey;
}

const objectInput = document.getElementById("objectId");
objectInput.value = DEFAULTS.objectId;
document.getElementById("metric").value = DEFAULTS.metric;
const nmaxInput = document.getElementById("nmax");
nmaxInput.value = DEFAULTS.nmax === 20 ? "1" : String(DEFAULTS.nmax / 2);
nmaxInput.dispatchEvent(new Event("input"));
objectInput.oninput = () => syncSurveyFromId(objectInput.value);
document.getElementById("survey").onchange = event => {
  const survey = event.target.value;
  if (surveyForObjectId(objectInput.value) !== survey) objectInput.value = "";
  refreshClassifiers(survey);
};
document.getElementById("showBtn").onclick = () => loadNeighborhood();
document.getElementById("resetBtn").onclick = () => resetZoom();
document.getElementById("survey").value = surveyForObjectId(DEFAULTS.objectId);
refreshClassifiers(document.getElementById("survey").value);
loadNeighborhood();

const DEFAULT_OBJECT_IDS = Object.freeze({
  LSST: "170028486134595648",
  ZTF: "ZTF17aackceb",
});
const startupParameters = new URLSearchParams(window.location.search);
const startupSurvey = startupParameters.get("survey")?.toUpperCase();
const startupObjectId = startupParameters.get("objectId");
const surveyInput = document.getElementById("survey");
const objectIdInput = document.getElementById("objectId");

if (Object.hasOwn(DEFAULT_OBJECT_IDS, startupSurvey)) {
  surveyInput.value = startupSurvey;
}
if (startupObjectId) {
  objectIdInput.value = startupObjectId;
} else {
  objectIdInput.value = DEFAULT_OBJECT_IDS[surveyInput.value];
}

let previousSurvey = surveyInput.value;
surveyInput.addEventListener("change", () => {
  invalidateNeighborhoodLoad();
  const viz = document.getElementById("viz");
  viz.replaceChildren();
  const objectList = document.getElementById("objectList");
  objectList.textContent = "No alerts loaded.";
  document.getElementById("resetBtn").disabled = true;
  hideTooltip(0);

  const previousDefault = DEFAULT_OBJECT_IDS[previousSurvey];
  if (!objectIdInput.value.trim() || objectIdInput.value === previousDefault) {
    objectIdInput.value = DEFAULT_OBJECT_IDS[surveyInput.value];
  }
  previousSurvey = surveyInput.value;
  setStatus(`Survey changed to ${previousSurvey}. Press Show neighborhood to load its graph.`, "idle");
});
setStatus("Choose parameters, then press Show neighborhood. Graph queries can take several seconds.", "idle");

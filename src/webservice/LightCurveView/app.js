let demo = generateDemoData();
let activeSNID = null;
let savedPresets = {};
let coeffs = {x: {}, y: {}};
let rainbowMode = false;

function initFinkObjectForm() {
  const form = document.getElementById("fink-object-form");
  const input = document.getElementById("fink-object-id");
  if (!form || !input) return;
  form.addEventListener("submit", async event => {
    event.preventDefault();
    await loadFinkObject(input.value);
    });
  }

// Fink Browser opens this page as ?objectId=<LSST object ID>; fill the menu and load it.
function loadRequestedObject() {
  const input = document.getElementById("fink-object-id");
  const search = (typeof window !== "undefined" && window.location && window.location.search) || "";
  if (!input || !search) return;
  const params = new URLSearchParams(search);
  const requested = params.get("objectId") ?? params.get("diaObjectId");
  if (!input || requested === null) return;
  input.value = requested.trim();
  loadFinkObject(requested);
  }

window.addEventListener("DOMContentLoaded", () => {
  initSliders();
  createSNIDButtons();
  initSaveButton();
  initFinkObjectForm();
  loadPresets();
  document.getElementById("autoProjection")?.addEventListener("click", applyAutomaticProjection);
  document.getElementById("resetRandom").addEventListener("click", resetRandom);
  document.getElementById("resetZero").addEventListener("click", resetZero);
  document.getElementById("resetRainbow").addEventListener("click", resetRainbow);
  resetRandom();
  plotLightCurves(demo);
  loadRequestedObject();
  });

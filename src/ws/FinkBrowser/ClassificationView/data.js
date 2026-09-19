const GRAPH_ENDPOINTS = Object.freeze({
  LSST: Object.freeze({
    graphUrl: "http://134.158.243.144:24444",
    allowInsecureGraph: true,
  }),
  ZTF: null,
});

let neighborhoodRequestSerial = 0;

function selectedGraphOptions(survey) {
  const endpoint = GRAPH_ENDPOINTS[survey];
  if (!endpoint) {
    throw new Error(`${survey} graph endpoint is not configured`);
  }
  return { ...endpoint };
}

async function fetchNeighborhood(params) {
  const options = selectedGraphOptions(params.survey);
  showSpinner(true, "green");
  try {
    return await LomikelGraph.objectNeighborhood2JSON(
      String(params.objectId),
      params.classifier,
      {
        reclassifier: params.reclassifier === "none" ? null : params.reclassifier,
        nmax: params.nmax,
        metric: params.metric,
        climit: 0,
        ...options,
      },
    );
  } finally {
    showSpinner(false);
  }
}

function showLoadError(error) {
  const status = document.getElementById("status");
  status.textContent = `Load failed: ${error.message}`;
  status.dataset.state = "error";
  document.getElementById("viz").replaceChildren();
  document.getElementById("objectList").replaceChildren();
}

async function loadNeighborhood(objectId = null) {
  const requestSerial = ++neighborhoodRequestSerial;
  const survey = document.getElementById("survey").value;
  const params = {
    survey,
    objectId: objectId === null ? document.getElementById("objectId").value : objectId,
    classifier: document.getElementById("classifier").value,
    reclassifier: document.getElementById("reclassifier").value,
    metric: document.getElementById("metric").value,
    nmax: Number(document.getElementById("nmaxValue").textContent),
  };
  const status = document.getElementById("status");
  status.textContent = `Loading ${survey} graph data…`;
  status.dataset.state = "loading";
  try {
    const data = await fetchNeighborhood(params);
    if (requestSerial !== neighborhoodRequestSerial) return;
    data.objectId = String(data.objectId);
    updateDetailsPanel(data, survey);
    await showObjectNeighborhood(data, survey, requestSerial);
    status.textContent = `Loaded ${Object.keys(data.objects || {}).length} nearest objects from ${survey}.`;
    status.dataset.state = "ok";
  } catch (error) {
    if (requestSerial === neighborhoodRequestSerial) showLoadError(error);
  }
}

const GRAPH_ENDPOINTS = Object.freeze({
  LSST: Object.freeze({
    graphUrl: "http://134.158.243.144:24444",
    allowInsecureGraph: true,
  }),
  ZTF: Object.freeze({
    graphUrl: "http://157.136.253.253:24444",
    allowInsecureGraph: true,
  }),
});

let neighborhoodRequestSerial = 0;
let activeNeighborhoodController = null;

function selectedGraphOptions(survey) {
  const endpoint = GRAPH_ENDPOINTS[survey];
  if (!endpoint) {
    throw new Error(`${survey} graph endpoint is not configured`);
  }
  return { ...endpoint };
}

function readNeighborhoodParameters(objectId = null) {
  const inputId = objectId === null
    ? document.getElementById("objectId").value
    : objectId;
  const trimmedId = String(inputId).trim();
  if (!trimmedId) throw new Error("Enter an object ID");

  return {
    survey: document.getElementById("survey").value,
    objectId: trimmedId,
    classifier: document.getElementById("classifier").value,
    reclassifier: document.getElementById("reclassifier").value,
    metric: document.getElementById("metric").value,
    nmax: parseNeighborhoodLimit(document.getElementById("nmaxValue").value),
  };
}

async function fetchNeighborhood(params, signal) {
  const options = selectedGraphOptions(params.survey);
  return LomikelGraph.objectNeighborhood2JSON(
    params.objectId,
    params.classifier,
    {
      reclassifier: params.reclassifier === "none" ? null : params.reclassifier,
      nmax: params.nmax,
      metric: params.metric,
      climit: 0,
      signal,
      timeoutMs: 90_000,
      ...options,
    },
  );
}

function setStatus(message, state) {
  const status = document.getElementById("status");
  status.textContent = message;
  status.dataset.state = state;
}

function showLoadError(error) {
  setStatus(`Load failed: ${error.message}`, "error");
}

function invalidateNeighborhoodLoad() {
  neighborhoodRequestSerial += 1;
  const controller = activeNeighborhoodController;
  activeNeighborhoodController = null;
  controller?.abort();
  showSpinner(false);
}

function cancelNeighborhoodLoad() {
  if (!activeNeighborhoodController) return;
  invalidateNeighborhoodLoad();
  setStatus("Graph request cancelled. The previous visualization was kept.", "idle");
}

async function loadNeighborhood(objectId = null) {
  let params;
  try {
    params = readNeighborhoodParameters(objectId);
    selectedGraphOptions(params.survey);
  } catch (error) {
    showLoadError(error);
    return;
  }

  activeNeighborhoodController?.abort();
  const controller = new AbortController();
  activeNeighborhoodController = controller;
  const requestSerial = ++neighborhoodRequestSerial;

  document.getElementById("objectId").value = params.objectId;
  setStatus(`Querying ${params.survey} for ${params.objectId}…`, "loading");
  showSpinner(true, "green");

  try {
    const response = await fetchNeighborhood(params, controller.signal);
    if (requestSerial !== neighborhoodRequestSerial) return;
    const data = validateNeighborhoodData(response, params.objectId);
    const layoutResult = await showObjectNeighborhood(data, params, requestSerial);
    if (requestSerial !== neighborhoodRequestSerial) return;
    updateDetailsPanel(data, params.survey);
    const count = Object.keys(data.objects || {}).length;
    if (layoutResult?.warning) {
      setStatus(
        `Loaded ${count} nearest objects; class overlaps were unavailable, so classes are evenly spaced.`,
        "warning",
      );
    } else {
      setStatus(`Loaded ${count} nearest objects from ${params.survey}.`, "ok");
    }
  } catch (error) {
    if (requestSerial !== neighborhoodRequestSerial) return;
    if (error.name === "AbortError") {
      setStatus("Graph request cancelled. The previous visualization was kept.", "idle");
    } else {
      showLoadError(error);
    }
  } finally {
    if (activeNeighborhoodController === controller) {
      activeNeighborhoodController = null;
      showSpinner(false);
    }
  }
}

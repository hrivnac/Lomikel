const paramsButton = document.getElementById("paramsButton");
const paramsDialog = document.getElementById("paramsDialog");
const paramsForm = document.getElementById("paramsForm");
const paramsError = document.getElementById("paramsError");
const parameterInputs = {
  fetchPeriod: document.getElementById("fetchPeriodInput"),
  fetchStart: document.getElementById("fetchStartInput"),
  nAlerts: document.getElementById("nAlertsInput"),
  magMax: document.getElementById("magMaxInput")
  };

function fillParameterForm() {
  parameterInputs.fetchPeriod.value = fetchPeriod;
  parameterInputs.fetchStart.value = fetchStart;
  parameterInputs.nAlerts.value = nAlerts;
  parameterInputs.magMax.value = magMax;
  paramsError.hidden = true;
  paramsError.textContent = "";
  }

function syncAlertSettingsToUrl(settings) {
  const query = new URLSearchParams(window.location.search);
  for (const [name, value] of Object.entries(settings)) query.set(name, String(value));
  const suffix = query.toString();
  window.history.replaceState(
    null,
    "",
    `${window.location.pathname}${suffix ? `?${suffix}` : ""}${window.location.hash}`,
  );
  }

async function applyAlertSettings(values) {
  const settings = parseAlertSettings(values);
  invalidateAlertRefreshes();
  fetchPeriod = settings.fetchPeriod;
  fetchStart = settings.fetchStart;
  nAlerts = settings.nAlerts;
  magMax = settings.magMax;
  rebuildStars();
  scheduleRefreshTimer();
  syncAlertSettingsToUrl(settings);
  updateStatusPanel();
  await refreshEnabledSurveys();
  }

paramsButton.addEventListener("click", () => {
  fillParameterForm();
  paramsDialog.showModal();
  parameterInputs.fetchPeriod.focus();
  });

paramsForm.addEventListener("submit", async event => {
  event.preventDefault();
  paramsError.hidden = true;
  paramsError.textContent = "";
  try {
    await applyAlertSettings(Object.fromEntries(
      Object.entries(parameterInputs).map(([name, input]) => [name, input.value]),
    ));
    paramsDialog.close();
    paramsButton.focus();
    }
  catch (error) {
    paramsError.textContent = error.message;
    paramsError.hidden = false;
    }
  });

paramsDialog.addEventListener("cancel", event => {
  event.preventDefault();
  paramsDialog.close();
  paramsButton.focus();
  });

document.getElementById("paramsCancel").addEventListener("click", () => {
  paramsDialog.close();
  paramsButton.focus();
  });

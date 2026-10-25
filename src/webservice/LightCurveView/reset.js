// Reset handlers update existing sliders 

let xTime = false;

function clearAutomaticAnalysis() {
  coeffs.offsetX = 0;
  coeffs.offsetY = 0;
  coeffs.bands = null;
  coeffs.interval = null;
  coeffs.source = "manual";
  if (typeof activeTrajectoryAnalysis !== "undefined") activeTrajectoryAnalysis = null;
  if (typeof document !== "undefined") {
    const analysis = document.getElementById("analysis-results");
    if (analysis) analysis.hidden = true;
    }
  }

function resetRandom() {
  clearAutomaticAnalysis();
  xTime = false;
  filters.forEach(f => {
    coeffs.x[f] = +(Math.random() * 2 - 1).toFixed(2);
    coeffs.y[f] = +(Math.random() * 2 - 1).toFixed(2);
  });
  update();
  if (typeof setLoadStatus === "function") setLoadStatus("Random projection.");
  }
  
function resetZero() {
  clearAutomaticAnalysis();
  xTime = false;
  filters.forEach(f => {
    coeffs.x[f] = 0;
    coeffs.y[f] = 0;
    });
  update();
  if (typeof setLoadStatus === "function") setLoadStatus("Zero projection.");
  }
  
function resetRainbow() {
  clearAutomaticAnalysis();
  xTime = true;
  const rainbow = rainbowCoefficients();
  filters.forEach(f => {
    coeffs.x[f] = rainbow[f];
    coeffs.y[f] = rainbow[f];
    });
  update();
  if (typeof setLoadStatus === "function") setLoadStatus("Time/wavelength projection.");
  }


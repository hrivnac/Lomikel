const SVG_NAMESPACE = "http://www.w3.org/2000/svg";
const COEFFICIENT_LIMIT = 2;
const COEFFICIENT_SIZE = 300;
const COEFFICIENT_LEFT = 40;
const COEFFICIENT_TOP = 20;

function svgElement(name, attributes = {}) {
  const element = document.createElementNS(SVG_NAMESPACE, name);
  for (const [key, value] of Object.entries(attributes)) {
    element.setAttribute(key, value);
    }
  return element;
  }

function coefficientToX(value) {
  return COEFFICIENT_LEFT + (value + COEFFICIENT_LIMIT) * COEFFICIENT_SIZE / (2 * COEFFICIENT_LIMIT);
  }

function coefficientToY(value) {
  return COEFFICIENT_TOP + (COEFFICIENT_LIMIT - value) * COEFFICIENT_SIZE / (2 * COEFFICIENT_LIMIT);
  }

function clampCoefficient(value) {
  return Math.max(-COEFFICIENT_LIMIT, Math.min(COEFFICIENT_LIMIT, value));
  }

function updateCoefficientHandle(handle) {
  const x = coefficientToX(coeffs.x[handle.band] || 0);
  const y = coefficientToY(coeffs.y[handle.band] || 0);
  handle.circle.setAttribute("cx", x);
  handle.circle.setAttribute("cy", y);
  handle.circle.setAttribute("aria-valuenow", coeffs.x[handle.band] || 0);
  handle.circle.setAttribute("aria-valuetext", `x ${(coeffs.x[handle.band] || 0).toFixed(2)}, y ${(coeffs.y[handle.band] || 0).toFixed(2)}`);
  handle.label.setAttribute("x", x + 11);
  handle.label.setAttribute("y", y - 11);
  }

function setCoefficient(handle, x, y) {
  const changedProjectionMode = coeffs.source !== "manual";
  coeffs.x[handle.band] = +clampCoefficient(x).toFixed(2);
  coeffs.y[handle.band] = +clampCoefficient(y).toFixed(2);
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
  updateCoefficientHandle(handle);
  updateFormulas();
  schedulePlotUpdate();
  if (changedProjectionMode && typeof setLoadStatus === "function") {
    setLoadStatus("Manual projection.");
    }
  }

function pointerCoefficients(svg, event) {
  const bounds = svg.getBoundingClientRect();
  const localX = (event.clientX - bounds.left) * 360 / bounds.width - COEFFICIENT_LEFT;
  const localY = (event.clientY - bounds.top) * 360 / bounds.height - COEFFICIENT_TOP;
  return {
    x: localX * 2 * COEFFICIENT_LIMIT / COEFFICIENT_SIZE - COEFFICIENT_LIMIT,
    y: COEFFICIENT_LIMIT - localY * 2 * COEFFICIENT_LIMIT / COEFFICIENT_SIZE,
    };
  }

function initSliders() {
  const container = document.getElementById("sliders");
  container.replaceChildren();
  const svg = svgElement("svg", {
    viewBox: "0 0 360 360",
    role: "group",
    "aria-label": "Two-dimensional LSST band coefficient editor",
    });
  container.appendChild(svg);

  svg.appendChild(svgElement("rect", {
    x: COEFFICIENT_LEFT, y: COEFFICIENT_TOP,
    width: COEFFICIENT_SIZE, height: COEFFICIENT_SIZE,
    fill: "#fdfdfd", stroke: "#777",
    }));

  for (const tick of [-2, -1, 0, 1, 2]) {
    const x = coefficientToX(tick);
    const y = coefficientToY(tick);
    svg.appendChild(svgElement("line", {x1: x, x2: x, y1: COEFFICIENT_TOP, y2: COEFFICIENT_TOP + COEFFICIENT_SIZE, class: "grid-line"}));
    svg.appendChild(svgElement("line", {x1: COEFFICIENT_LEFT, x2: COEFFICIENT_LEFT + COEFFICIENT_SIZE, y1: y, y2: y, class: "grid-line"}));
    const xText = svgElement("text", {x, y: 340, "text-anchor": "middle", class: "tick-label"});
    xText.textContent = tick;
    svg.appendChild(xText);
    const yText = svgElement("text", {x: 31, y: y + 4, "text-anchor": "end", class: "tick-label"});
    yText.textContent = tick;
    svg.appendChild(yText);
    }

  const xLabel = svgElement("text", {x: 190, y: 358, "text-anchor": "middle", class: "axis-label"});
  xLabel.textContent = "x coefficient";
  svg.appendChild(xLabel);
  const yLabel = svgElement("text", {x: 12, y: 170, transform: "rotate(-90 12 170)", "text-anchor": "middle", class: "axis-label"});
  yLabel.textContent = "y coefficient";
  svg.appendChild(yLabel);

  const handles = filters.map(band => {
    const circle = svgElement("circle", {
      r: 10,
      fill: bandColors[band],
      stroke: "#222",
      tabindex: "0",
      role: "slider",
      "aria-label": `${band} band x and y coefficients`,
      "aria-valuemin": -COEFFICIENT_LIMIT,
      "aria-valuemax": COEFFICIENT_LIMIT,
      });
    const label = svgElement("text", {class: "handle-label", "pointer-events": "none"});
    label.textContent = band;
    const handle = {band, circle, label};

    circle.addEventListener("pointerdown", event => {
      circle.setPointerCapture(event.pointerId);
      const point = pointerCoefficients(svg, event);
      setCoefficient(handle, point.x, point.y);
      });
    circle.addEventListener("pointermove", event => {
      if (!circle.hasPointerCapture(event.pointerId)) return;
      const point = pointerCoefficients(svg, event);
      setCoefficient(handle, point.x, point.y);
      });
    circle.addEventListener("keydown", event => {
      const step = event.shiftKey ? 0.25 : 0.05;
      let x = coeffs.x[band] || 0;
      let y = coeffs.y[band] || 0;
      if (event.key === "ArrowLeft") x -= step;
      else if (event.key === "ArrowRight") x += step;
      else if (event.key === "ArrowDown") y -= step;
      else if (event.key === "ArrowUp") y += step;
      else return;
      event.preventDefault();
      setCoefficient(handle, x, y);
      });

    svg.appendChild(circle);
    svg.appendChild(label);
    updateCoefficientHandle(handle);
    return handle;
    });

  window.sliderHandles = {handles};
  }

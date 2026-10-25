let tooltipHideTimer;

function hideTooltip(delay = 250) {
  clearTimeout(tooltipHideTimer);
  tooltipHideTimer = setTimeout(() => {
    document.getElementById("tooltip").hidden = true;
  }, delay);
}

function populateTooltip(id, classes) {
  const tooltip = document.getElementById("tooltip");
  tooltip.replaceChildren();
  const heading = document.createElement("strong");
  heading.textContent = id;
  tooltip.append(heading);
  tooltip.append(document.createElement("br"));
  appendClasses(tooltip, classes);
  tooltip.hidden = false;
}

async function showObjectNeighborhood(data, params, requestSerial) {
  const viz = document.getElementById("viz");
  const width = viz.clientWidth || window.innerWidth;
  const height = viz.clientHeight || window.innerHeight * 0.7;
  const radius = Math.max(90, Math.min(width, height) / 2 - 70);
  const centerX = width / 2;
  const centerY = height / 2;

  const allClasses = new Set(Object.keys(data.objectClassification || {}));
  for (const object of Object.values(data.objects || {})) {
    Object.keys(object.classes || {}).forEach((name) => allClasses.add(name));
  }
  const classList = [...allClasses];
  const classifier = params.reclassifier === "none"
    ? params.classifier
    : params.reclassifier;

  let classPositions;
  let warning = false;
  try {
    classPositions = await getOverlapPositions(
      params.survey,
      classifier,
      classList,
      radius,
      centerX,
      centerY,
    );
  } catch (_) {
    if (requestSerial !== neighborhoodRequestSerial) return null;
    classPositions = equidistantPositions(classList, radius, centerX, centerY);
    warning = true;
  }
  if (requestSerial !== neighborhoodRequestSerial) return null;

  const objectLayout = computeObjectLayout(data, classPositions, {
    centerX,
    centerY,
    radius,
  });

  viz.replaceChildren();
  const svg = d3.select(viz)
    .append("svg")
    .attr("viewBox", `0 0 ${width} ${height}`)
    .attr("preserveAspectRatio", "xMidYMid meet")
    .attr("role", "group")
    .attr(
      "aria-label",
      `Classification neighborhood for ${objectLayout.main.id} with ${objectLayout.neighbors.length} nearest alerts`,
    );
  const container = svg.append("g");
  const zoom = d3.zoom().scaleExtent([0.5, 12]).on("zoom", (event) => {
    container.attr("transform", event.transform);
    container.selectAll(".object-symbol")
      .attr("transform", (position) => (
        `translate(${position.x},${position.y}) scale(${1 / event.transform.k})`
      ));
  });
  svg.call(zoom).on("dblclick.zoom", null);
  const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  window.resetZoom = () => {
    const target = reducedMotion ? svg : svg.transition().duration(250);
    target.call(zoom.transform, d3.zoomIdentity);
  };
  document.getElementById("resetBtn").disabled = false;

  if (classList.length > 0) {
    container.append("circle")
      .attr("class", "link-line class-ring")
      .attr("cx", centerX)
      .attr("cy", centerY)
      .attr("r", radius)
      .attr("fill", "none")
      .attr("stroke", "#9c8fc4")
      .attr("stroke-width", 1.2)
      .attr("stroke-dasharray", "5 4");
  }
  for (const name of classList) {
    const position = classPositions[name];
    if (!position) continue;
    container.append("text")
      .attr("class", "class-label")
      .attr("x", position.x)
      .attr("y", position.y)
      .attr("text-anchor", "middle")
      .attr("dominant-baseline", "middle")
      .text(name);
  }

  for (const object of objectLayout.neighbors) {
    const labelPosition = edgeLabelPosition(objectLayout.main, object, 9);
    container.append("line")
      .attr("class", `link-line neighbor-link${object.visuallyOffset ? " zero-distance" : ""}`)
      .attr("x1", objectLayout.main.x)
      .attr("y1", objectLayout.main.y)
      .attr("x2", object.x)
      .attr("y2", object.y)
      .attr("stroke", "#8f9eb2")
      .attr("stroke-width", 1.15)
      .attr("stroke-dasharray", object.visuallyOffset ? "2 3" : null);
    container.append("text")
      .attr("class", "distance-label")
      .attr("x", labelPosition.x)
      .attr("y", labelPosition.y)
      .attr("text-anchor", "middle")
      .text(object.distance.toPrecision(4));
  }

  drawObject(container, objectLayout.main, true);
  for (const object of objectLayout.neighbors) {
    drawObject(container, object, false);
  }
  return { warning };
}

function drawObject(container, object, isMain) {
  const color = isMain ? "#d83a52" : "#1e70b7";
  const symbol = container.append("path")
    .datum(object)
    .attr("class", "object-symbol")
    .attr("d", d3.symbol().type(d3.symbolStar).size(isMain ? 230 : 130)())
    .attr("transform", `translate(${object.x},${object.y})`)
    .attr("fill", color)
    .attr("tabindex", 0)
    .attr("focusable", "true")
    .attr("role", "button")
    .attr(
      "aria-label",
      isMain
        ? `Selected alert ${object.id}. Press Enter to reload.`
        : `Neighbor alert ${object.id}, graph distance ${object.distance.toPrecision(4)}. Press Enter to center it.`,
    );

  const showTooltip = () => {
    clearTimeout(tooltipHideTimer);
    populateTooltip(object.id, object.classes);
  };
  const activate = (event) => {
    event.preventDefault();
    event.stopPropagation();
    loadNeighborhood(object.id);
  };

  symbol
    .on("pointerenter", showTooltip)
    .on("pointerleave", (event) => {
      if (event.pointerType === "mouse") hideTooltip();
    })
    .on("click", showTooltip)
    .on("focus", showTooltip)
    .on("blur", () => hideTooltip())
    .on("dblclick", activate)
    .on("keyup", (event) => {
      if (event.key === "Tab") showTooltip();
    })
    .on("keydown", (event) => {
      if (event.key === "Enter" || event.key === " ") activate(event);
      if (event.key === "Escape") hideTooltip(0);
    });
}

const tooltipElement = document.getElementById("tooltip");
tooltipElement.addEventListener("pointerenter", () => clearTimeout(tooltipHideTimer));
tooltipElement.addEventListener("pointerleave", () => hideTooltip());
const dismissTooltipOutside = (event) => {
  const target = event.target instanceof Element ? event.target : null;
  if (!target?.closest(".object-symbol, #tooltip")) hideTooltip(0);
};
document.addEventListener("pointerdown", dismissTooltipOutside);
document.addEventListener("click", dismissTooltipOutside);
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && !tooltipElement.hidden) hideTooltip(0);
});

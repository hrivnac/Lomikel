async function showObjectNeighborhood(data, survey, requestSerial) {
  const viz = document.getElementById("viz");
  viz.replaceChildren();
  const width = viz.clientWidth || window.innerWidth;
  const height = viz.clientHeight || window.innerHeight * 0.8;
  const radius = Math.min(width, height) / 3;
  const centerX = width / 2;
  const centerY = height / 2;
  const svg = d3.select(viz).append("svg").attr("width", width).attr("height", height)
    .attr("role", "img").attr("aria-label", "Classification neighborhood graph");
  const container = svg.append("g");
  const zoom = d3.zoom().scaleExtent([0.5, 20]).on("zoom", (event) => {
    container.attr("transform", event.transform);
  });
  svg.call(zoom);
  window.resetZoom = () => svg.transition().duration(300).call(zoom.transform, d3.zoomIdentity);

  const allClasses = new Set(Object.keys(data.objectClassification || {}));
  for (const object of Object.values(data.objects || {})) {
    Object.keys(object.classes || {}).forEach((name) => allClasses.add(name));
  }
  const classList = [...allClasses];
  const classifier = document.getElementById("reclassifier").value === "none"
    ? document.getElementById("classifier").value
    : document.getElementById("reclassifier").value;
  let classPositions;
  try {
    classPositions = await getOverlapPositions(survey, classifier, classList, radius, centerX, centerY);
  } catch (error) {
    if (requestSerial !== undefined && requestSerial !== neighborhoodRequestSerial) return;
    classPositions = equidistantPositions(classList, radius, centerX, centerY);
    document.getElementById("status").textContent = `Loaded data; overlap layout unavailable: ${error.message}`;
    document.getElementById("status").dataset.state = "warning";
  }
  if (requestSerial !== undefined && requestSerial !== neighborhoodRequestSerial) return;

  const classPath = classList.map((name) => classPositions[name]).sort((a, b) => a.angle - b.angle);
  container.append("path").datum(classPath).attr("class", "link-line")
    .attr("d", d3.line().x((point) => point.x).y((point) => point.y).curve(d3.curveLinearClosed))
    .attr("fill", "none").attr("stroke", "#bbb").attr("stroke-dasharray", "4 2");
  for (const name of classList) {
    const position = classPositions[name];
    container.append("text").attr("class", "class-label").attr("x", position.x).attr("y", position.y)
      .attr("text-anchor", "middle").attr("dominant-baseline", "middle").text(name);
  }

  function weightedPosition(classMap) {
    let x = 0; let y = 0; let total = 0;
    for (const [name, rawWeight] of Object.entries(classMap || {})) {
      const weight = Number(rawWeight);
      const position = classPositions[name];
      if (position && Number.isFinite(weight)) { x += position.x * weight; y += position.y * weight; total += weight; }
    }
    return total > 0 ? { x: x / total, y: y / total } : { x: centerX, y: centerY };
  }

  const objectPosition = weightedPosition(data.objectClassification);
  drawObject(container, String(data.objectId), objectPosition, "red", data.objectClassification, true, survey);
  for (const [rawId, object] of Object.entries(data.objects || {})) {
    const id = String(rawId);
    const position = weightedPosition(object.classes);
    container.append("line").attr("class", "link-line").attr("x1", objectPosition.x).attr("y1", objectPosition.y)
      .attr("x2", position.x).attr("y2", position.y).attr("stroke", "#aaa");
    container.append("text").attr("class", "distance-label").attr("x", (objectPosition.x + position.x) / 2)
      .attr("y", (objectPosition.y + position.y) / 2).attr("text-anchor", "middle")
      .text(Number(object.distance).toFixed(4));
    drawObject(container, id, position, "#1769aa", object.classes, false, survey);
  }
}

function drawObject(container, id, position, color, classes, isMain, survey) {
  const symbol = container.append("path").attr("class", "object-symbol")
    .attr("d", d3.symbol().type(d3.symbolStar).size(isMain ? 220 : 120)())
    .attr("transform", `translate(${position.x},${position.y})`).attr("fill", color)
    .attr("tabindex", 0).attr("role", "button").attr("aria-label", `Show details for ${id}`);
  const tooltip = d3.select("#tooltip");
  let hideTimer;
  const hide = () => { hideTimer = setTimeout(() => tooltip.style("display", "none"), 900); };
  const show = (event) => {
    clearTimeout(hideTimer);
    const node = tooltip.node();
    node.replaceChildren();
    const heading = document.createElement("strong"); heading.textContent = id; node.append(heading);
    const link = objectLink(survey, id); if (link) node.append(document.createElement("br"), link);
    node.append(document.createElement("br"));
    appendClasses(node, classes);
    node.style.display = "block";
    node.style.left = `${(event.pageX || position.x) + 10}px`;
    node.style.top = `${(event.pageY || position.y) - 20}px`;
  };
  symbol.on("pointerenter", show).on("pointermove", show).on("pointerleave", (event) => {
    if (event.pointerType === "mouse") hide();
  }).on("focus", show).on("blur", hide).on("dblclick", () => loadNeighborhood(id));
}

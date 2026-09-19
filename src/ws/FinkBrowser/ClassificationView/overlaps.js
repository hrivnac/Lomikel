const overlapCache = new Map();

function equidistantPositions(classList, radius, centerX, centerY) {
  const positions = {};
  classList.forEach((name, index) => {
    const angle = (2 * Math.PI * index) / Math.max(classList.length, 1);
    positions[name] = {
      angle,
      x: centerX + radius * Math.cos(angle),
      y: centerY + radius * Math.sin(angle),
    };
  });
  return positions;
}

async function getOverlapPositions(survey, classifier, classList, radius, centerX, centerY) {
  const endpoint = GRAPH_ENDPOINTS[survey];
  if (!endpoint) throw new Error(`${survey} graph endpoint is not configured`);
  const cacheKey = `${endpoint.graphUrl}|${survey}|${classifier}`;
  let overlaps = overlapCache.get(cacheKey);
  if (!overlaps) {
    showSpinner(true, "blue");
    try {
      overlaps = await LomikelGraph.overlaps2JSON(classifier, endpoint);
      if (!Array.isArray(overlaps)) throw new Error("overlap response is not an array");
      overlapCache.set(cacheKey, overlaps);
    } finally {
      showSpinner(false);
    }
  }

  const links = [];
  let maxOverlap = 0;
  for (const item of overlaps) {
    const first = String(item?.first?.class || "").trim();
    const second = String(item?.second?.class || "").trim();
    const value = Number(item?.overlap);
    if (!first || !second || first === second || !Number.isFinite(value)) continue;
    if (!classList.includes(first) || !classList.includes(second)) continue;
    links.push({ source: first, target: second, value });
    maxOverlap = Math.max(maxOverlap, value);
  }
  if (!links.length || !(maxOverlap > 0)) return equidistantPositions(classList, radius, centerX, centerY);

  const nodes = classList.map((id) => ({ id }));
  const simulation = d3.forceSimulation(nodes)
    .force("link", d3.forceLink(links).id((node) => node.id)
      .distance((link) => radius * (0.15 + 1 - link.value / maxOverlap))
      .strength((link) => 0.3 + 0.7 * link.value / maxOverlap))
    .force("charge", d3.forceManyBody().strength(-radius * 0.6))
    .force("center", d3.forceCenter(centerX, centerY))
    .stop();
  for (let tick = 0; tick < 300; tick += 1) simulation.tick();
  const positions = {};
  for (const node of nodes) {
    const angle = Math.atan2(node.y - centerY, node.x - centerX);
    positions[node.id] = {
      angle,
      x: centerX + radius * Math.cos(angle),
      y: centerY + radius * Math.sin(angle),
    };
  }
  return positions;
}

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

function normalizeOverlapLinks(overlaps, classList) {
  const visibleClasses = new Set(classList);
  const uniqueLinks = new Map();

  for (const item of overlaps) {
    const first = String(item?.first?.class || "").trim();
    const second = String(item?.second?.class || "").trim();
    const value = item?.overlap;
    if (
      !first ||
      !second ||
      first === second ||
      typeof value !== "number" ||
      !Number.isFinite(value) ||
      value <= 0 ||
      !visibleClasses.has(first) ||
      !visibleClasses.has(second)
    ) continue;

    const [source, target] = first < second ? [first, second] : [second, first];
    const key = `${source}\u0000${target}`;
    const previous = uniqueLinks.get(key);
    if (!previous || value > previous.value) {
      uniqueLinks.set(key, { source, target, value });
    }
  }

  const links = [...uniqueLinks.values()];
  const maxOverlap = links.reduce(
    (maximum, link) => Math.max(maximum, link.value),
    0,
  );
  return { links, maxOverlap };
}

function loadOverlaps(survey, classifier, endpoint) {
  const cacheKey = `${endpoint.graphUrl}|${survey}|${classifier}`;
  let overlapPromise = overlapCache.get(cacheKey);
  if (!overlapPromise) {
    overlapPromise = LomikelGraph.overlaps2JSON(classifier, {
      ...endpoint,
      timeoutMs: 90_000,
    }).then((overlaps) => {
      if (!Array.isArray(overlaps)) throw new Error("overlap response is not an array");
      return overlaps;
    }).catch((error) => {
      overlapCache.delete(cacheKey);
      throw error;
    });
    overlapCache.set(cacheKey, overlapPromise);
  }
  return overlapPromise;
}

async function getOverlapPositions(survey, classifier, classList, radius, centerX, centerY) {
  const endpoint = GRAPH_ENDPOINTS[survey];
  if (!endpoint) throw new Error(`${survey} graph endpoint is not configured`);
  const overlaps = await loadOverlaps(survey, classifier, endpoint);
  const { links, maxOverlap } = normalizeOverlapLinks(overlaps, classList);
  if (!links.length || !(maxOverlap > 0)) {
    return equidistantPositions(classList, radius, centerX, centerY);
  }

  const nodes = classList.map((id) => ({ id }));
  const simulation = d3.forceSimulation(nodes)
    .force("link", d3.forceLink(links)
      .id((node) => node.id)
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

if (typeof module === "object" && module.exports) {
  module.exports = { normalizeOverlapLinks };
}

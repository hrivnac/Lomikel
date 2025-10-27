// app.js
async function showNeighbors(data, objectId, objectClassification, classifier) {
  d3.select("#viz").select("svg").remove();

  const width = 800, height = 800, radius = 300;
  const centerX = width / 2, centerY = height / 2;

  const svg = d3.select("#viz")
    .append("svg")
    .attr("width", width)
    .attr("height", height);

  const container = svg.append("g");

  const zoom = d3.zoom()
    .scaleExtent([0.5, 10])
    .on("zoom", event => {
      container.attr("transform", event.transform);
      const scale = event.transform.k;
      // scale-dependent size for objects and text
      container.selectAll("path").attr("transform", function (d) {
        const t = d3.select(this).attr("data-transform");
        return `${t} scale(${1 / Math.sqrt(scale)})`;
      });
      container.selectAll("text.size-dynamic").style("font-size", `${10 / Math.sqrt(scale)}px`);
      container.selectAll("line").attr("stroke-width", 1 / scale);
    });

  svg.call(zoom);

  window.resetZoom = function () {
    svg.transition().duration(500).call(zoom.transform, d3.zoomIdentity);
  };

  const tooltip = d3.select("#tooltip");
  let hideTimeout = null;

  const allClasses = new Set(Object.keys(objectClassification));
  Object.values(data).forEach(obj =>
    Object.keys(obj.classes).forEach(c => allClasses.add(c))
  );
  const classList = [...allClasses];

  // --- fetch overlap-based class layout ---
  const classPositions = await getOverlapPositions(
    classifier,
    classList,
    radius,
    centerX,
    centerY
  );

  // Draw class labels and connecting lines
  const classKeys = Object.keys(classPositions);
  for (let i = 0; i < classKeys.length; i++) {
    const c1 = classPositions[classKeys[i]];
    const c2 = classPositions[classKeys[(i + 1) % classKeys.length]];
    container.append("line")
      .attr("x1", c1.x)
      .attr("y1", c1.y)
      .attr("x2", c2.x)
      .attr("y2", c2.y)
      .attr("stroke", "#ddd")
      .attr("stroke-width", 1);

    container.append("text")
      .attr("x", c1.x)
      .attr("y", c1.y)
      .attr("text-anchor", "middle")
      .attr("alignment-baseline", "middle")
      .attr("class", "size-dynamic")
      .text(classKeys[i])
      .style("font-size", "12px");
  }

  function weightedPosition(classMap) {
    let sumX = 0, sumY = 0, total = 0;
    for (const cls in classMap) {
      const weight = classMap[cls];
      if (classPositions[cls]) {
        sumX += classPositions[cls].x * weight;
        sumY += classPositions[cls].y * weight;
        total += weight;
      }
    }
    return { x: sumX / total, y: sumY / total };
  }

  const objectPos = weightedPosition(objectClassification);

  // Red star for main object
  const mainStar = container.append("path")
    .datum({ id: objectId })
    .attr("d", d3.symbol().type(d3.symbolStar).size(200)())
    .attr("data-transform", `translate(${objectPos.x},${objectPos.y})`)
    .attr("transform", `translate(${objectPos.x},${objectPos.y})`)
    .attr("fill", "red")
    .on("mouseover", function (event, d) {
      clearTimeout(hideTimeout);
      const classEntries = Object.entries(objectClassification)
        .map(([cls, wt]) => `<li>${cls}: ${wt.toFixed(4)}</li>`)
        .join("");
      tooltip
        .html(`<strong>${objectId}</strong><br>
               <a href="https://fink-portal.org/${objectId}" target="_blank">View on Fink Portal</a><br>
               <a href="#" class="show-link" data-id="${objectId}">Show</a><br>
               <strong>Classes:</strong><ul style="margin:4px 0; padding-left:16px;">${classEntries}</ul>`)
        .style("display", "block")
        .style("left", (event.pageX + 10) + "px")
        .style("top", (event.pageY - 20) + "px");
    })
    .on("mousemove", function (event) {
      tooltip.style("left", (event.pageX + 10) + "px").style("top", (event.pageY - 20) + "px");
    })
    .on("mouseout", function () {
      hideTimeout = setTimeout(() => tooltip.style("display", "none"), 600);
    })
    .on("dblclick", () => handleShowClick(objectId));

  // Blue stars for neighbors
  for (const [id, obj] of Object.entries(data)) {
    const pos = weightedPosition(obj.classes);
    container.append("line")
      .attr("x1", objectPos.x)
      .attr("y1", objectPos.y)
      .attr("x2", pos.x)
      .attr("y2", pos.y)
      .attr("stroke", "#aaa")
      .attr("stroke-dasharray", "2 2")
      .attr("stroke-width", 1);

    container.append("path")
      .datum({ id })
      .attr("d", d3.symbol().type(d3.symbolStar).size(100)())
      .attr("data-transform", `translate(${pos.x},${pos.y})`)
      .attr("transform", `translate(${pos.x},${pos.y})`)
      .attr("fill", "blue")
      .on("mouseover", function (event) {
        clearTimeout(hideTimeout);
        const classEntries = Object.entries(obj.classes)
          .map(([cls, wt]) => `<li>${cls}: ${wt.toFixed(4)}</li>`)
          .join("");
        tooltip
          .html(`<strong>${id}</strong><br>
                 <a href="https://fink-portal.org/${id}" target="_blank">View on Fink Portal</a><br>
                 <a href="#" class="show-link" data-id="${id}">Show</a><br>
                 <strong>Classes:</strong><ul style="margin:4px 0; padding-left:16px;">${classEntries}</ul>`)
          .style("display", "block")
          .style("left", (event.pageX + 10) + "px")
          .style("top", (event.pageY - 20) + "px");
      })
      .on("mousemove", function (event) {
        tooltip.style("left", (event.pageX + 10) + "px").style("top", (event.pageY - 20) + "px");
      })
      .on("mouseout", function () {
        hideTimeout = setTimeout(() => tooltip.style("display", "none"), 600);
      })
      .on("dblclick", () => handleShowClick(id));

    const labelX = (objectPos.x + pos.x) / 2;
    const labelY = (objectPos.y + pos.y) / 2;
    container.append("text")
      .attr("x", labelX)
      .attr("y", labelY)
      .attr("text-anchor", "middle")
      .attr("alignment-baseline", "middle")
      .attr("class", "size-dynamic")
      .text(obj.distance.toFixed(4))
      .style("font-size", "10px")
      .style("fill", "#666");
  }

  tooltip.on("mouseover", () => clearTimeout(hideTimeout))
    .on("mouseout", () => hideTimeout = setTimeout(() => tooltip.style("display", "none"), 600))
    .on("click", e => {
      const link = e.target.closest(".show-link");
      if (link) {
        e.preventDefault();
        const id = link.dataset.id;
        handleShowClick(id);
      }
    });
}

// --- helper: load overlaps and compute layout ---
async function getOverlapPositions(classifier, classList, radius, centerX, centerY) {
  try {
    const response = await fetch(`/FinkBrowser/Overlaps.jsp?${classifier}`);
    if (!response.ok) throw new Error("Failed to fetch overlaps");
    const text = await response.text();

    const overlaps = [];
    const regex = /OCol:[^:]+::(.+?) \* OCol:[^:]+::(.+?) = ([\d.]+)/g;
    let match;
    while ((match = regex.exec(text)) !== null) {
      const [, c1, c2, value] = match;
      const v = parseFloat(value);
      overlaps.push({ source: c1, target: c2, value: v });
    }

    const nodes = classList.map(c => ({ id: c }));
    const maxOverlap = d3.max(overlaps, d => d.value) || 1;

    const simulation = d3.forceSimulation(nodes)
      .force("link", d3.forceLink(overlaps)
        .id(d => d.id)
        .distance(d => radius * (1 - d.value / maxOverlap)))
      .force("charge", d3.forceManyBody().strength(-radius * 0.6))
      .force("radial", d3.forceRadial(radius, centerX, centerY))
      .stop();

    for (let i = 0; i < 200; i++) simulation.tick();

    const positions = {};
    nodes.forEach(n => {
      const angle = Math.atan2(n.y - centerY, n.x - centerX);
      positions[n.id] = {
        x: centerX + radius * Math.cos(angle),
        y: centerY + radius * Math.sin(angle)
      };
    });
    return positions;
  } catch (e) {
    console.warn("Overlap fetch failed:", e);
    const angleScale = d3.scaleLinear().domain([0, classList.length]).range([0, 2 * Math.PI]);
    const pos = {};
    classList.forEach((c, i) => {
      const a = angleScale(i);
      pos[c] = { x: centerX + radius * Math.cos(a), y: centerY + radius * Math.sin(a) };
    });
    return pos;
  }
}

// --- handle 'Show' button or double-click ---
async function handleShowClick(objectId) {
  const spinner = document.getElementById("spinner");
  spinner.style.display = "block";
  const params = getFormParams();
  params.objectId = objectId;
  const query = new URLSearchParams(params).toString();

  try {
    const res = await fetch(`/FinkBrowser/Neighborhood.jsp?${query}`);
    if (!res.ok) throw new Error("Fetch failed");
    const data = await res.json();
    spinner.style.display = "none";
    showNeighbors(data.data, data.objectId, data.objectClassification, params.classifier);
  } catch (e) {
    console.warn("Neighborhood fetch failed, using demo data");
    spinner.style.display = "none";
    showNeighbors(sampleData, "ZTF23abdlxeb", {"YSO_Candidate": 0.8333, "SN candidate": 0.1667}, params.classifier);
  }
}

// --- read top-right panel inputs ---
function getFormParams() {
  return {
    system: document.getElementById("system").value,
    objectId: document.getElementById("objectId").value,
    classifier: document.getElementById("classifier").value,
    alg: document.getElementById("alg").value,
    nmax: document.getElementById("nmax").value,
    climit: document.getElementById("climit").value
  };
}

// --- sample fallback data ---
const sampleData = {
  "ZTF19actbknb": { distance: 0.0023, classes: {"YSO_Candidate": 0.8571, "SN candidate": 0.1429}},
  "ZTF19actfogx": { distance: 0.0363, classes: {"Radio": 0.4707, "YSO_Candidate": 0.0608, "CataclyV*_Candidate": 0.1943, "CV*_Candidate": 0.2623}}
};

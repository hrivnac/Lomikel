// --- Utility: show/hide spinner ---
function showSpinner(show) {
  document.getElementById("loading-spinner").style.display = show ? "flex" : "none";
}

// --- Fetch data (unchanged) ---
async function fetchNeighborhood(params) {
  const query = new URLSearchParams(params).toString();
  const url = `/FinkBrowser/Neighborhood.jsp?${query}`;

  try {
    showSpinner(true);
    const response = await fetch(url);
    if (!response.ok) throw new Error("Network error");
    return await response.json();
  } catch (err) {
    console.warn("Neighborhood.jsp failed, using demo data:", err);
    return {
      objectId: "ZTF23abdlxeb",
      objects: {
        "ZTF19actbknb": {
          distance: 0.0023,
          classes: {"YSO_Candidate": 0.8571, "SN candidate": 0.1429}
        },
        "ZTF19actfogx": {
          distance: 0.0363,
          classes: {"Radio": 0.4707, "YSO_Candidate": 0.0608, "CataclyV*_Candidate": 0.1943, "CV*_Candidate": 0.2623}
        }
      },
      objectClassification: {"YSO_Candidate": 0.8333, "SN candidate": 0.1667}
    };
  } finally {
    showSpinner(false);
  }
}

// Replace your existing function with this improved one
async function getOverlapPositions(classifier, classList, radius, centerX, centerY) {
  try {
    const response = await fetch(`/FinkBrowser/Overlaps.jsp?${classifier}`);
    if (!response.ok) throw new Error("Failed to fetch overlaps");
    const text = await response.text();

    // parse overlaps into a map
    const overlapRaw = {}; // overlapRaw[a][b] = value (may be asymmetric)
    const regex = /OCol:[^:]+::(.+?) \* OCol:[^:]+::(.+?) = ([\d.]+)/g;
    let match;
    while ((match = regex.exec(text)) !== null) {
      const [, c1, c2, valueStr] = match;
      const v = parseFloat(valueStr);
      overlapRaw[c1] = overlapRaw[c1] || {};
      overlapRaw[c1][c2] = v;
    }

    // build symmetric links (average both directions), ignore self overlaps for max calculation
    const links = [];
    let maxOverlap = 0;
    for (let i = 0; i < classList.length; i++) {
      for (let j = i + 1; j < classList.length; j++) {
        const a = classList[i], b = classList[j];
        const v1 = (overlapRaw[a] && overlapRaw[a][b]) || 0;
        const v2 = (overlapRaw[b] && overlapRaw[b][a]) || 0;
        const avg = (v1 + v2) / ( (v1>0 && v2>0) ? 2 : (v1>0||v2>0) ? 1 : 0 ); // if both zero -> 0
        // if both zero, avg becomes NaN; handle:
        const value = Number.isFinite(avg) ? avg : (v1 || v2 || 0);
        if (value > 0) {
          links.push({ source: a, target: b, value });
          if (value > maxOverlap) maxOverlap = value;
        }
      }
    }

    // if nothing meaningful found, fallback to equidistant
    if (links.length === 0) throw new Error("No overlap links");

    // map value -> similarity in [0,1]
    if (maxOverlap <= 0) maxOverlap = 1;

    // distance range (tunable)
    const minDist = radius * 0.08;  // very close when overlap is maximal
    const maxDist = radius * 1.1;   // far when overlap is minimal

    // prepare nodes for simulation
    const nodes = classList.map(c => ({ id: c }));

    // create D3 link objects with normalized similarity
    const simLinks = links.map(l => {
      const s = l.value / maxOverlap; // similarity in 0..1
      return {
        source: l.source,
        target: l.target,
        value: l.value,
        sim: s,
        distance: minDist + (1 - s) * (maxDist - minDist),
        strength: 0.15 + 0.8 * s // stronger pull when similarity high
      };
    });

    // force simulation: links use computed distance & strength
    const simulation = d3.forceSimulation(nodes)
      .force("link", d3.forceLink(simLinks)
        .id(d => d.id)
        .distance(d => d.distance)
        .strength(d => d.strength))
      // charge: moderate repulsion (tune by number of classes)
      .force("charge", d3.forceManyBody().strength(-Math.max(20, radius * 0.3)))
      // a weak centering to keep them near circle center, radial keeps them roughly at radius
      .force("radial", d3.forceRadial(radius, centerX, centerY).strength(0.7))
      .stop();

    // run more ticks for stability
    const ticks = Math.max(300, classList.length * 60);
    for (let i = 0; i < ticks; i++) simulation.tick();

    // compute an angle for each node from the sim result and project to circle
    const positions = {};
    nodes.forEach(n => {
      // angle from sim position
      const angle = Math.atan2(n.y - centerY, n.x - centerX);
      // project onto exact circle to preserve ring layout
      positions[n.id] = {
        x: centerX + radius * Math.cos(angle),
        y: centerY + radius * Math.sin(angle)
      };
    });

    return positions;

  } catch (err) {
    console.warn("Overlap fetch/processing failed, using equidistant layout", err);
    // fallback: equidistant positions
    const angleScale = d3.scaleLinear()
      .domain([0, classList.length])
      .range([0, 2 * Math.PI]);
    const pos = {};
    classList.forEach((c, i) => {
      const a = angleScale(i);
      pos[c] = { x: centerX + radius * Math.cos(a), y: centerY + radius * Math.sin(a) };
    });
    return pos;
  }
}


// --- Visualization (unchanged except tooltip delay increased) ---
async function showObjectNeighborhood(data) {
  d3.select("#viz").selectAll("*").remove();

  const width = document.getElementById("viz").clientWidth;
  const height = document.getElementById("viz").clientHeight;
  const radius = Math.min(width, height) / 3;
  const centerX = width / 2, centerY = height / 2;

  const svg = d3.select("#viz").append("svg")
    .attr("width", width)
    .attr("height", height);
  const container = svg.append("g");

//  const zoom = d3.zoom()
//    .scaleExtent([0.5, 10])
//    .on("zoom", event => container.attr("transform", event.transform));
 
const zoom = d3.zoom()
  .scaleExtent([0.5, 20])
  .on("zoom", event => {
    const { k, x, y } = event.transform;
    container.attr("transform", `translate(${x},${y}) scale(${k})`);

    // Resize objects and text inversely to zoom level
    container.selectAll(".object-symbol")
      .attr("transform", d => `translate(${d.x},${d.y}) scale(${1 / k})`);

    container.selectAll(".distance-label")
      .style("font-size", `${10 / k}px`);

    container.selectAll(".class-label")
      .style("font-size", `${12 / k}px`);
      
    container.selectAll(".link-line")
      .style("stroke-width", `${1.5 / k}px`);
  });
    
  svg.call(zoom);
  window.resetZoom = () => svg.transition().duration(500).call(zoom.transform, d3.zoomIdentity);

  const tooltip = d3.select("#tooltip");
  let hideTimeout = null;

  const allClasses = new Set();
  Object.keys(data.objectClassification).forEach(c => allClasses.add(c));
  Object.values(data.objects).forEach(obj =>
    Object.keys(obj.classes).forEach(c => allClasses.add(c))
  );
  
  //const classList = [...new Set(Object.keys(data.objectClassification)
  //  .concat(...Object.values(data).map(d => Object.keys(d.classes))))];
 
  const classList = Array.from(allClasses);
  
  const classPositions = await getOverlapPositions(classifier, classList, radius, centerX, centerY);  
  
  
  //
  //const angleScale = d3.scaleLinear()
  //  .domain([0, classList.length])
  //  .range([0, 2 * Math.PI]);
  //
  //const classPositions = {};
  
  classList.forEach((cls, i) => {
    //const angle = angleScale(i) + (classList.length === 2 ? Math.PI / 2 : 0);
    //classPositions[cls] = {
    //  x: centerX + radius * Math.cos(angle),
    //  y: centerY + radius * Math.sin(angle)
    //};
    container.append("text")
      .attr("class", "class-label")
      .attr("x", classPositions[cls].x)
      .attr("y", classPositions[cls].y)
      .attr("text-anchor", "middle")
      .attr("alignment-baseline", "middle")
      .text(cls)
      .style("font-size", "12px");
  });

  const classLine = d3.line()
    .x(d => d.x)
    .y(d => d.y)
    .curve(d3.curveLinearClosed);

  container.append("path")
    .datum(classList.map(cls => classPositions[cls]))
    .attr("class", "link-line")
    .attr("d", classLine)
    .attr("fill", "none")
    .attr("stroke", "#ccc")
    .attr("stroke-dasharray", "4 2");

  function weightedPosition(classMap) {
    let sumX = 0, sumY = 0, total = 0;
    for (const cls in classMap) {
      const weight = classMap[cls];
      const pos = classPositions[cls];
      if (pos) {
        sumX += pos.x * weight;
        sumY += pos.y * weight;
        total += weight;
      }
    }
    return { x: sumX / total, y: sumY / total };
  }

  const objectPos = weightedPosition(data.objectClassification);

  drawObject(container, data.objectId, objectPos, "red", data.objectClassification, tooltip, hideTimeout, true);

  for (const [id, obj] of Object.entries(data.objects)) {
    const pos = weightedPosition(obj.classes);

    container.append("line")
      .attr("class", "link-line")
      .attr("x1", objectPos.x)
      .attr("y1", objectPos.y)
      .attr("x2", pos.x)
      .attr("y2", pos.y)
      .attr("stroke", "#aaa")
      .attr("stroke-dasharray", "2 2");

    const labelX = (objectPos.x + pos.x) / 2;
    const labelY = (objectPos.y + pos.y) / 2;
    container.append("text")
      .attr("class", "distance-label")
      .attr("x", labelX)
      .attr("y", labelY)
      .attr("text-anchor", "middle")
      .attr("alignment-baseline", "middle")
      .text(obj.distance.toFixed(4))
      .style("font-size", "10px")
      .style("fill", "#666");

    drawObject(container, id, pos, "blue", obj.classes, tooltip, hideTimeout, false);
  }

  tooltip
    .on("mouseover", () => clearTimeout(hideTimeout))
    .on("mouseout", () => {
      // increase wait so user can move into tooltip and click links
      hideTimeout = setTimeout(() => tooltip.style("display", "none"), 900);
    });
}

function drawObject(container, id, pos, color, classes, tooltip, hideTimeout, isMain) {
  const symbol = container.append("path")
    .datum({x: pos.x, y: pos.y})
    .attr("class", "object-symbol")
    .attr("d", d3.symbol().type(d3.symbolStar).size(isMain ? 200 : 100))
    .attr("transform", `translate(${pos.x},${pos.y})`)
    .attr("fill", color);

  const showDetails = (event) => {
    clearTimeout(hideTimeout);
    const classEntries = Object.entries(classes)
      .map(([cls, wt]) => `<li>${cls}: ${wt.toFixed(4)}</li>`)
      .join("");
    tooltip.html(`
      <strong>${id}</strong><br>
      <a href="https://fink-portal.org/${id}" target="_blank">View on Fink Portal</a><br>
      <a href="#" id="showObject-${id}">Show</a><br>
      <strong>Classes:</strong>
      <ul style="margin:4px 0; padding-left:16px;">${classEntries}</ul>
    `)
      .style("display", "block")
      .style("left", (event.pageX + 10) + "px")
      .style("top", (event.pageY - 20) + "px");

    setTimeout(() => {
      const link = document.getElementById(`showObject-${id}`);
      if (link) link.onclick = (e) => {
        e.preventDefault();
        tooltip.style("display", "none");
        loadNeighborhood(id);
      };
    }, 100);
  };

  symbol
    .on("mouseover", showDetails)
    .on("mousemove", event => {
      tooltip.style("left", (event.pageX + 10) + "px")
             .style("top", (event.pageY - 20) + "px");
    })
    .on("mouseout", () => {
      hideTimeout = setTimeout(() => tooltip.style("display", "none"), 900);
    })
    .on("dblclick", () => loadNeighborhood(id));
}

// --- Movable panel via header only ---
function makeDraggable(header, panel) {
  let offsetX, offsetY, isDown = false;
  header.addEventListener('mousedown', e => {
    isDown = true;
    offsetX = e.clientX - panel.offsetLeft;
    offsetY = e.clientY - panel.offsetTop;
  });
  window.addEventListener('mouseup', () => isDown = false);
  window.addEventListener('mousemove', e => {
    if (!isDown) return;
    panel.style.left = (e.clientX - offsetX) + 'px';
    panel.style.top = (e.clientY - offsetY) + 'px';
  });
}
makeDraggable(document.getElementById("panel-header"), document.getElementById("controls"));

// --- Help modal ---
document.getElementById("help-btn").onclick = () => document.getElementById("help-modal").style.display = "block";
document.getElementById("close-help").onclick = () => document.getElementById("help-modal").style.display = "none";
window.onclick = (event) => {
  if (event.target === document.getElementById("help-modal"))
    document.getElementById("help-modal").style.display = "none";
};

// --- nmax slider display (LOGICALLY CORRECT) ---
const nmaxSlider = document.getElementById("nmax");
function sliderToNmax(t) {
  // t in [0,1]
  if (t <= 0.5) {
    // left half maps linearly to 0..1
    return t * 2.0;
  } else {
    // right half maps logarithmically to 1..10
    const u = (t - 0.5) / 0.5; // 0..1
    return Math.pow(10, u);   // 10^0..10^1 -> 1..10
  }
}
nmaxSlider.oninput = () => {
  const t = parseFloat(nmaxSlider.value);
  let nmax = sliderToNmax(t);
  if (nmax > 1) nmax = Math.round(nmax); // integers in 1..10
  // format display
  const disp = (nmax > 1) ? String(nmax) : nmax.toFixed(2).replace(/\.?0+$/, '');
  document.getElementById("nmaxValue").textContent = disp;
};
// ensure initial correct display (we set slider value=0.25 in HTML so nmax=0.5)
nmaxSlider.dispatchEvent(new Event('input'));

// --- Load data ---
async function loadNeighborhood(objectId = null) {
  const nmaxText = document.getElementById("nmaxValue").textContent;
  const nmaxVal = parseFloat(nmaxText);
  const params = {
    system: document.getElementById("system").value,
    objectId: objectId || document.getElementById("objectId").value,
    classifier: document.getElementById("classifier").value,
    alg: document.getElementById("alg").value,
    nmax: nmaxVal,
    climit: document.getElementById("climit").value
  };

  const data = await fetchNeighborhood(params);
  showObjectNeighborhood(data);
}

document.getElementById("showBtn").onclick = () => loadNeighborhood();
document.getElementById("resetBtn").onclick = () => resetZoom();

// Initial load
loadNeighborhood();

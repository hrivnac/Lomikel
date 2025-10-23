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

// --- Visualization (unchanged except tooltip delay increased) ---
function showObjectNeighborhood(data) {
  d3.select("#viz").selectAll("*").remove();

  const width = document.getElementById("viz").clientWidth;
  const height = document.getElementById("viz").clientHeight;
  const radius = Math.min(width, height) / 3;
  const centerX = width / 2, centerY = height / 2;

  const svg = d3.select("#viz").append("svg")
    .attr("width", width)
    .attr("height", height);
  const container = svg.append("g");

  const zoom = d3.zoom()
    .scaleExtent([0.5, 10])
    .on("zoom", event => container.attr("transform", event.transform));
  svg.call(zoom);
  window.resetZoom = () => svg.transition().duration(500).call(zoom.transform, d3.zoomIdentity);

  const tooltip = d3.select("#tooltip");
  let hideTimeout = null;

  const allClasses = new Set();
  Object.keys(data.objectClassification).forEach(c => allClasses.add(c));
  Object.values(data.objects).forEach(obj =>
    Object.keys(obj.classes).forEach(c => allClasses.add(c))
  );
  const classList = Array.from(allClasses);

  const angleScale = d3.scaleLinear()
    .domain([0, classList.length])
    .range([0, 2 * Math.PI]);

  const classPositions = {};
  classList.forEach((cls, i) => {
    const angle = angleScale(i) + (classList.length === 2 ? Math.PI / 2 : 0);
    classPositions[cls] = {
      x: centerX + radius * Math.cos(angle),
      y: centerY + radius * Math.sin(angle)
    };
    container.append("text")
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
      .attr("x1", objectPos.x)
      .attr("y1", objectPos.y)
      .attr("x2", pos.x)
      .attr("y2", pos.y)
      .attr("stroke", "#aaa")
      .attr("stroke-dasharray", "2 2");

    const labelX = (objectPos.x + pos.x) / 2;
    const labelY = (objectPos.y + pos.y) / 2;
    container.append("text")
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
const nmaxSlide

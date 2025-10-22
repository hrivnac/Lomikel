function showNeighbors(data, sourceId, sourceClassification) {
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
    });

  svg.call(zoom);

  window.resetZoom = function() {
    svg.transition().duration(500).call(zoom.transform, d3.zoomIdentity);
  };

  const tooltip = d3.select("#tooltip");
  let hideTimeout = null;

  // Collect all classes
  const allClasses = new Set();
  Object.keys(sourceClassification).forEach(c => allClasses.add(c));
  Object.values(data).forEach(obj => {
    Object.keys(obj.classes).forEach(c => allClasses.add(c));
  });

  const classList = Array.from(allClasses);

  // Use full circle distribution (rotated if 2 classes)
  const angleScale = d3.scaleLinear()
    .domain([0, classList.length])
    .range([0, 2 * Math.PI]);

  const classPositions = {};
  classList.forEach((cls, i) => {
    // Rotate by 90° if only 2 classes (vertical layout)
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

  // Draw polygon connecting classes
  const classLine = d3.line()
    .x(d => d.x)
    .y(d => d.y)
    .curve(d3.curveLinearClosed);

  const classPoints = classList.map(cls => classPositions[cls]);
  container.append("path")
    .datum(classPoints)
    .attr("d", classLine)
    .attr("fill", "none")
    .attr("stroke", "#ccc")
    .attr("stroke-width", 1)
    .attr("stroke-dasharray", "4 2");

  // Weighted position of a source based on class weights
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

  // Main source (red star)
  const sourcePos = weightedPosition(sourceClassification);
  container.append("path")
    .attr("d", d3.symbol().type(d3.symbolStar).size(200))
    .attr("transform", `translate(${sourcePos.x},${sourcePos.y})`)
    .attr("fill", "red")
    .on("mouseover", function(event) {
      clearTimeout(hideTimeout);

      const classEntries = Object.entries(sourceClassification)
        .map(([cls, wt]) => `<li>${cls}: ${wt.toFixed(4)}</li>`)
        .join("");

      tooltip
        .html(`<strong>${sourceId}</strong><br>
               <a href="https://fink-portal.org/${sourceId}" target="_blank">View on Fink Portal</a><br>
               <strong>Classes:</strong><ul style="margin:4px 0; padding-left:16px;">${classEntries}</ul>`)
        .style("display", "block")
        .style("left", (event.pageX + 10) + "px")
        .style("top", (event.pageY - 20) + "px");
    })
    .on("mousemove", event => {
      tooltip.style("left", (event.pageX + 10) + "px")
             .style("top", (event.pageY - 20) + "px");
    })
    .on("mouseout", () => {
      hideTimeout = setTimeout(() => tooltip.style("display", "none"), 300);
    });

  // Neighbor sources
  for (const [id, obj] of Object.entries(data)) {
    const pos = weightedPosition(obj.classes);

    container.append("path")
      .attr("d", d3.symbol().type(d3.symbolStar).size(100))
      .attr("transform", `translate(${pos.x},${pos.y})`)
      .attr("fill", "blue")
      .on("mouseover", function(event) {
        clearTimeout(hideTimeout);

        const classEntries = Object.entries(obj.classes)
          .map(([cls, wt]) => `<li>${cls}: ${wt.toFixed(4)}</li>`)
          .join("");

        tooltip
          .html(`<strong>${id}</strong><br>
                 <a href="https://fink-portal.org/${id}" target="_blank">View on Fink Portal</a><br>
                 <strong>Classes:</strong><ul style="margin:4px 0; padding-left:16px;">${classEntries}</ul>`)
          .style("display", "block")
          .style("left", (event.pageX + 10) + "px")
          .style("top", (event.pageY - 20) + "px");
      })
      .on("mousemove", event => {
        tooltip.style("left", (event.pageX + 10) + "px")
               .style("top", (event.pageY - 20) + "px");
      })
      .on("mouseout", () => {
        hideTimeout = setTimeout(() => tooltip.style("display", "none"), 300);
      });

    // Line from source to neighbor
    container.append("line")
      .attr("x1", sourcePos.x)
      .attr("y1", sourcePos.y)
      .attr("x2", pos.x)
      .attr("y2", pos.y)
      .attr("stroke", "#aaa")
      .attr("stroke-dasharray", "2 2");

    // Distance label
    const labelX = (sourcePos.x + pos.x) / 2;
    const labelY = (sourcePos.y + pos.y) / 2;
    container.append("text")
      .attr("x", labelX)
      .attr("y", labelY)
      .attr("text-anchor", "middle")
      .attr("alignment-baseline", "middle")
      .text(obj.distance.toFixed(4))
      .style("font-size", "10px")
      .style("fill", "#666");
  }

  tooltip
    .on("mouseover", () => clearTimeout(hideTimeout))
    .on("mouseout", () => {
      hideTimeout = setTimeout(() => tooltip.style("display", "none"), 300);
    });
}

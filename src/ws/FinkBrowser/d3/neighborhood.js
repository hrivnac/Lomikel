function showNeighbors(data, sourceId, sourceClassification) {

/*
  data = {ZTF19actbknb: {distance: 0.0022675736961451087,
                         classes: {"YSO_Candidate": 0.8571,
                                   "SN candidate": 0.1429}},
          ZTF19actfogx: {distance: 0.03628117913832199,
                         classes: {"Radio": 0.4707,
                                   "YSO_Candidate": 0.0608,
                                   "Star": 0.007,
                                   "SN candidate": 0.0046,
                                   "CataclyV*_Candidate": 0.1943,
                                   "CV*_Candidate": 0.2623}},
          ZTF20abendxc: {distance: 0.044682398043492555,
                         classes: {"YSO_Candidate": 0.939,
                                   "SN candidate": 0.061}}};

    sourceId = "ZTF23abdlxeb";
    sourceClassification = {"YSO_Candidate": 0.8333,
                            "SN candidate": 0.1667};

*/
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

      
      
      const allClasses = new Set();
Object.keys(sourceClassification).forEach(c => allClasses.add(c));
Object.values(data).forEach(obj => {
  Object.keys(obj.classes).forEach(c => allClasses.add(c));
});
const classList = Array.from(allClasses);

      const angleScale = d3.scaleLinear()
        .domain([0, classList.length])
        .range([0, 2 * Math.PI]);

      const classPositions = {};
      classList.forEach((cls, i) => {
        const angle = angleScale(i);
        classPositions[cls] = {
          x: centerX + radius * Math.cos(angle),
          y: centerY + radius * Math.sin(angle)
        };

        
const jitter = 0.01; // radians

classList.forEach((cls, i) => {
  const angle = angleScale(i) + (Math.random() - 0.5) * jitter;
  classPositions[cls] = {
    x: centerX + radius * Math.cos(angle),
    y: centerY + radius * Math.sin(angle)
  };
});

        
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

const classPoints = classList.map(cls => classPositions[cls]);

container.append("path")
  .datum(classPoints)
  .attr("d", classLine)
  .attr("fill", "none")
  .attr("stroke", "#ccc")
  .attr("stroke-width", 1)
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

      const sourcePos = weightedPosition(sourceClassification);

      container.append("path")
        .attr("d", d3.symbol().type(d3.symbolStar).size(200))
        .attr("transform", `translate(${sourcePos.x},${sourcePos.y})`)
        .attr("fill", "red");

      for (const [id, obj] of Object.entries(data)) {
        const pos = weightedPosition(obj.classes);

        container.append("path")
          .attr("d", d3.symbol().type(d3.symbolStar).size(100))
          .attr("transform", `translate(${pos.x},${pos.y})`)
          .attr("fill", "blue")
          .on("mouseover", function(event) {
            clearTimeout(hideTimeout);
            tooltip
              .html(`<strong>${id}</strong><br><a href="https://fink-portal.org/${id}" target="_blank">View on Fink Portal</a>`)
              .style("display", "block")
              .style("left", (event.pageX + 10) + "px")
              .style("top", (event.pageY - 20) + "px");
          })
          .on("mousemove", function(event) {
            tooltip
              .style("left", (event.pageX + 10) + "px")
              .style("top", (event.pageY - 20) + "px");
          })
          .on("mouseout", function() {
            hideTimeout = setTimeout(() => {
              tooltip.style("display", "none");
            }, 300);
          });

        container.append("line")
          .attr("x1", sourcePos.x)
          .attr("y1", sourcePos.y)
          .attr("x2", pos.x)
          .attr("y2", pos.y)
          .attr("stroke", "#aaa")
          .attr("stroke-dasharray", "2 2");

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
          hideTimeout = setTimeout(() => {
            tooltip.style("display", "none");
          }, 300);
        });
    }
        
  
function resetZoom() {
  svg.transition()
     .duration(500)
     .call(zoom.transform, d3.zoomIdentity);
  }
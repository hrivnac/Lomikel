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

    const tooltip = d3.select("#tooltip");
    
    let hideTimeout = null;

    const mainClasses = new Set(Object.keys(sourceClassification));

    function mergeClasses(classes) {
      const merged = {};
      let othersWeight = 0;
      for (const [cls, weight] of Object.entries(classes)) {
        if (mainClasses.has(cls)) {
          merged[cls] = weight;
          }
        else {
          othersWeight += weight;
          }
        }
      if (othersWeight > 0) merged.others = othersWeight;
      return merged;
      }

    const processedData = {};
    for (const [id, info] of Object.entries(data)) {
      processedData[id] = {
        distance: info.distance,
        classes: mergeClasses(info.classes),
        };
      }

    const allClasses = new Set([...mainClasses]);
    for (const obj of Object.values(processedData)) {
      Object.keys(obj.classes).forEach((cls) => allClasses.add(cls));
      }
    const classList = Array.from(allClasses);
    const n = classList.length;
    const radius = 200;
    const centerX = 400;
    const centerY = 400;

    const classPositions = {};
    classList.forEach((cls, i) => {
      const angle = (2 * Math.PI * i) / n;
      classPositions[cls] = {
        x: centerX + radius * Math.cos(angle),
        y: centerY + radius * Math.sin(angle),
        };
      });

    function computePosition(classWeights) {
      let x = 0,
          y = 0,
          total = 0;
      for (const [cls, weight] of Object.entries(classWeights)) {
        const pos = classPositions[cls];
        x += weight * pos.x;
        y += weight * pos.y;
        total += weight;
        }
      return { x: x / total, y: y / total };
      }

    const nodes = [];
    const links = [];
    const central = computePosition(sourceClassification);
    nodes.push({ id: sourceId, ...central, color: "red" });

    for (const [id, obj] of Object.entries(processedData)) {
      const pos = computePosition(obj.classes);
      nodes.push({ id, ...pos, color: "blue" });
      links.push({ source: sourceId, target: id, distance: obj.distance });
      }

    const svg = d3.select("#viz")
                  .append("svg")
                  .attr("width", 800)
                  .attr("height", 800);

    svg.append("circle")
       .attr("cx", centerX)
       .attr("cy", centerY)
       .attr("r", radius)
       .attr("stroke", "gray")
       .attr("fill", "none")
       .attr("stroke-dasharray", "4 2");

    for (const [cls, pos] of Object.entries(classPositions)) {
      svg.append("text")
         .attr("x", pos.x)
         .attr("y", pos.y)
         .attr("text-anchor", "middle")
         .attr("dy", "-0.5em")
         .text(cls);
      }

    svg.selectAll("line")
       .data(links)
       .enter()
       .append("line")
       .attr("x1", (d) => nodes.find((n) => n.id === d.source).x)
       .attr("y1", (d) => nodes.find((n) => n.id === d.source).y)
       .attr("x2", (d) => nodes.find((n) => n.id === d.target).x)
       .attr("y2", (d) => nodes.find((n) => n.id === d.target).y)
       .attr("stroke", "gray");

    svg.selectAll(".dist-label")
       .data(links)
       .enter()
       .append("text")
       .attr("class", "dist-label")
       .attr("x",
             (d) => (nodes.find((n) => n.id === d.source).x +
                     nodes.find((n) => n.id === d.target).x) / 2)
       .attr("y",
             (d) => (nodes.find((n) => n.id === d.source).y +
                     nodes.find((n) => n.id === d.target).y) / 2)
       .attr("text-anchor", "middle")
       .attr("dy", "-0.3em")
       .attr("font-size", "10px")
       .attr("fill", "green")
       .text((d) => d.distance.toFixed(4));

    svg.selectAll(".node")
       .data(nodes)
       .enter()
       .append("path")
       .attr("class", "node")
       .attr("transform", (d) => `translate(${d.x},${d.y})`)
       .attr("d", d3.symbol().type(d3.symbolStar).size(100))
       .attr("fill", (d) => d.color)
       .on("mouseover", function(event, d) {clearTimeout(hideTimeout);
                                            tooltip.html(`<strong>${d.id}</strong><br><a href="https://fink-portal.org/${d.id}" target="_blank">View on Fink Portal</a>`)
                                                   .style("display", "block")
                                                   .style("left", (event.pageX + 10) + "px")
                                                   .style("top", (event.pageY - 20) + "px");})
       .on("mousemove", function(event) {tooltip.style("left", (event.pageX + 10) + "px")
                                                .style("top", (event.pageY - 20) + "px");})
       .on("mouseout", function() {hideTimeout = setTimeout(() => {tooltip.style("display", "none");}, 300);});
       
    svg.selectAll(".label")
       .data(nodes)
       .enter()
       .append("text")
       .attr("x", (d) => d.x)
       .attr("y", (d) => d.y + 10)
       .attr("text-anchor", "middle")
       .attr("font-size", "10px")
       .text((d) => d.id);
    
    tooltip.on("mouseover", () => {clearTimeout(hideTimeout);})
           .on("mouseout", () => {hideTimeout = setTimeout(() => {tooltip.style("display", "none");}, 300);
    });
           
  }

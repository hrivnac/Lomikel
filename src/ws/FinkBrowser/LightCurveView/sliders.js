function initSliders() {
  const container = d3.select("#sliders");
  container.html(""); // clear previous
  const size = 300; // pixels
  const axisRange = 5; // coeff range (-5 to +5)
  const scale = d3.scaleLinear().domain([-axisRange, axisRange]).range([0, size]);
  // SVG canvas
  const svg = container.append("svg")
                       .attr("width", size + 60)
                       .attr("height", size + 60)
                       .append("g")
                       .attr("transform", "translate(40, 20)");
  // background
  svg.append("rect")
     .attr("width", size)
     .attr("height", size)
     .attr("fill", "#fdfdfd")
     .attr("stroke", "#aaa");
  // grid lines
  const xAxis = d3.axisBottom(scale).tickValues([-2, -1, 0, 1, 2]);
  const yAxis = d3.axisLeft(  scale).tickValues([-2, -1, 0, 1, 2]);
  // horizontal grid
  svg.append("g")
     .attr("class", "grid")
     .call(yAxis.tickSize(-size)
                .tickFormat(""));
  // vertical grid
  svg.append("g")
    .attr("class", "grid")
    .attr("transform", `translate(0,${size})`)
    .call(xAxis.tickSize(-size)
               .tickFormat(""));
  // X axis with labels
  svg.append("g")
     .attr("transform", `translate(0,${size})`)
     .call(xAxis);
  // Y axis with labels
  svg.append("g")
     .call(yAxis);
  // axis labels
  svg.append("text")
     .attr("x", size / 2)
     .attr("y", size + 40)
     .attr("text-anchor", "middle")
     .attr("fill", "#333")
     .text("x coefficients");
  svg.append("text")
     .attr("transform", "rotate(-90)")
     .attr("x", -size / 2)
     .attr("y", -15)
     .attr("text-anchor", "middle")
     .attr("fill", "#333")
     .text("y coefficients");
  // draggable handles
  const drag = d3.drag()
                 .on("drag", function(event, d) {
                    const newX = Math.max(0, Math.min(size, event.x));
                    const newY = Math.max(0, Math.min(size, event.y));
                    d3.select(this).attr("cx", newX).attr("cy", newY);                    
                    coeffs.x[d.band] = +scale.invert(newX).toFixed(2);
                    coeffs.y[d.band] = -+scale.invert(newY).toFixed(2); // invert Y                    
                    updateFormulas();
                    updatePlot();
                    });
  const handles = svg.selectAll("circle")
                     .data(filters.map(f => ({ band: f })))
                     .enter()
                     .append("circle")
                     .attr("r", 8)
                     .attr("fill", d => bandColors[d.band])
                     .attr("stroke", "#333")
                     .attr("cx", d => scale(coeffs.x[d.band] || 0))
                     .attr("cy", d => scale(-(coeffs.y[d.band] || 0)))
                     .call(drag);
  window.sliderHandles = { handles, scale, size };
  }


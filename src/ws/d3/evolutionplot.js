function showEvolutionPlot(dataS, name, yS) {
  var w = 650;
  var h = 400;
  const colors = d3.schemeCategory10;
  var margin = {top:10, right:40, bottom:120, left:60},
               width =  w - margin.left - margin.right,
               height = h - margin.top  - margin.bottom;
  var svg = d3.select("#evolution_area")
              .append("svg")
              .attr("width",  width  + margin.left + margin.right)
              .attr("height", height + margin.top  + margin.bottom)
              .attr("class", "evolutionplot")
              .append("g")
              .attr("transform", "translate(" + margin.left + "," + margin.top + ")");
  svg.append("text")
     .attr("x", (width / 2))             
     .attr("y", 0 + (margin.top / 2))
     .attr("text-anchor", "middle")
     .style("font-size", "12px")
     .style("font-weight", "bold")
     .style("text-decoration", "underline")  
     .text(name);
  var data = JSON.parse(dataS.replace(/'/g, '"'));
  var t = d3.scaleLinear()
            .domain(d3.extent(data, d => d.t)).nice()
            .range([0, width]);      
  svg.append('g')
     .attr("transform", "translate(0," + height + ")")
     .call(d3.axisBottom(t)
             .tickFormat(d3.timeFormat("%d/%b/%y %H:%M:%S.%L")))
     .selectAll("text")	
     .style("text-anchor", "end")
     .attr("dx", "-.8em")
     .attr("dy", ".15em")
     .attr("transform", "rotate(-65)")
  var y = d3.scaleLinear()         
            .domain(d3.extent(data, d => d.y)).nice()
            .range([height, 0]);   
  svg.append('g')
     .call(d3.axisLeft(y))
     .call(g => g.append("text")
                 .attr("x", 4)
                 .attr("y", 4)
                 .attr("fill", "#000")
                 .attr("font-weight", "bold")
                 .attr("text-anchor", "start")
                 .text("↑ " + yS));
  svg.selectAll("whatever")
     .data(data)
     .enter()
     .append("circle")
     .attr("cx", d => t(d.t))
     .attr("cy", d => y(d.y))
     .attr("r", 2)
     .style("fill", d => (d.g || d.g === 0) ? colors[d.g] : 'black');
  }
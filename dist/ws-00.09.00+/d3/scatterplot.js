function showScatterPlot(dataS, name, xS, yS) {
  var w = 650;
  var h = 400;
  const colors = d3.schemeCategory10;
  var margin = {top:10, right:40, bottom:120, left:60},
               width =  w - margin.left - margin.right,
               height = h - margin.top  - margin.bottom;
  var svg = d3.select("#scatter_area")
              .append("svg")
              .attr("width",  width  + margin.left + margin.right)
              .attr("height", height + margin.top  + margin.bottom)
              .attr("class", "scatterplot")
              .append("g")
              .attr("transform", "translate(" + margin.left + "," + margin.top + ")");
  svg.append("text")
     .attr("x", (width / 2))             
     .attr("y", 0)
     .attr("text-anchor", "middle")
     .style("font-size", "12px")
     .style("font-weight", "bold")
     .style("text-decoration", "underline")  
     .text(name)
  var data = JSON.parse(dataS.replace(/'/g, '"'));
  var x;
  if (xS) {
    x = d3.scaleLinear()
          .domain(d3.extent(data, d => d.x)).nice()
          .range([0, width]);    
    svg.append('g')
       .attr("transform", "translate(0," + height + ")")
       .call(d3.axisBottom(x))
       .call(g => g.append("text")
                   .attr("x", w - margin.right)
                   .attr("y", -4)
                   .attr("fill", "#000")
                   .attr("font-weight", "bold")
                   .attr("text-anchor", "end")
                   .text(xS + " →"));
    }
  else {
    x = d3.scaleLinear()
          .domain(d3.extent(data, d => d.t)).nice()
          .range([0, width]);    
    svg.append('g')
       .attr("transform", "translate(0," + height + ")")
       .call(d3.axisBottom(x)
               .tickFormat(d3.timeFormat("%d/%b/%y %H:%M:%S.%L")))
       .selectAll("text")	
       .style("text-anchor", "end")
       .attr("dx", "-.8em")
       .attr("dy", ".15em")
       .attr("transform", "rotate(-65)");
    }
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
  var z = d3.scaleLinear()         
            .domain(d3.extent(data, d => d.z)).nice()
            .range([1, 5]);               
  var div = d3.select("body").append("div")	
              .attr("class", "tooltip")				
              .style("opacity", 0);
  if (xS) {
    svg.selectAll("whatever")
       .data(data)
       .enter()
       .append("circle")
       .attr("cx", d => x(d.x))
       .attr("cy", d => y(d.y))
       .attr("r",  d => d.z ? z(d.z) : 1)
       .attr("popup", d => d.k)
       .style("fill", d => (d.g || d.g === 0) ? colors[d.g] : 'black')
       .on("mouseover", function(d) {		
          div.transition()		
             .duration(200)		
             .style("opacity", 0.9);		
          div.html(d3.select(this).attr("popup") + "<br/>")	
             .style("left", (d3.select(this).attr("cx")) + "px")		
             .style("top",  (d3.select(this).attr("cy")) + "px");	
            })					
        .on("mouseout", function(d) {		
            div.transition()		
               .duration(500)		
               .style("opacity", 0);	
        });
    }
  else {
    svg.selectAll("whatever")
       .data(data)
       .enter()
       .append("circle")
       .attr("cx", d => x(d.t))
       .attr("cy", d => y(d.y))
       .attr("r",  d => d.z ? z(d.z) : 1)
       .attr("popup", d => d.k)
       .style("fill", d => (d.g || d.g === 0) ? colors[d.g] : 'black')
       .on("mouseover", function(d) {		
          div.transition()		
             .duration(200)		
             .style("opacity", 0.9);		
          div.html(d3.select(this).attr("popup") + "<br/>")	
             .style("left", (d3.select(this).attr("cx")) + "px")		
             .style("top",  (d3.select(this).attr("cy")) + "px");	
            })					
        .on("mouseout", function(d) {		
            div.transition()		
               .duration(500)		
               .style("opacity", 0);	
        });
    }
  }
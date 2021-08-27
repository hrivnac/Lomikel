function showScatterPlot(data, name, xS, yS, zS, sS, url) {
  
  var w = 650;
  var h = 400;
  const colors = d3.schemeSet1;
  var margin;
  if (xS) {
    margin = {top:20, right:60, bottom:60, left:60},
             width =  w - margin.left - margin.right,
             height = h - margin.top  - margin.bottom;
    }
  else {
    margin = {top:20, right:60, bottom:120, left:60},
             width =  w - margin.left - margin.right,
             height = h - margin.top  - margin.bottom;
    }
    
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
     .text(name + (zS ? ", z: " + zS : "") + (sS ? ", s: " + sS : ""))

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
            
  var g = d3.scaleOrdinal()
	          .domain(d3.extent(data, d => d.g))
	          .range(colors);
            
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
       .attr("r",  d => d.z ? z(d.z) : 5)
       .attr("key", d => d.k + " " + d.s)
       .attr("stroke-width", "1")
       .attr("info",       d => (d.k ? ("<b><u>" + d.k + "</u></b><br/>"            ) : "") +
                                (xS  ? ("<br/>x: " + xS + " = "             + d.x   ) : "") + 
                                (yS  ? ("<br/>y: " + yS + " = "             + d.y   ) : "") + 
                                (zS  ? ("<br/>z: " + zS + " = "             + d.z   ) : "") +
                                (d.g ? ("<br/>g: " + (sS ? (sS + "=") : "") + d.g   ) : ""))
       .style("stroke", d => d.g ? g(d.g) : 'black')
       .style("fill", 'white')
       .on("click", function(d) {
          window.parent.parent.feedback("Scatter Point: " + d3.select(this).attr("info"));
          window.parent.parent.commands(d3.select(this).attr("info"), actions(url, d3.select(this).attr("key")));
          })
       .on("mouseover", function(d) {		
          div.transition()		
             .duration(200)		
             .style("opacity", 0.9);		
          div.html(d3.select(this).attr("info"))	
             .style("left", (d3.select(this).attr("cx")) + "px")		
             .style("top",  (d3.select(this).attr("cy")) + "px");	
            })					
       .on("mouseout", function(d) {		
           div.transition()		
              .duration(2000)		
              .style("opacity", 0);	
       });
    }
  else {
    var formatTime = d3.timeFormat("%d/%b/%y %H:%M:%S.%L");
    svg.selectAll("whatever")
       .data(data)
       .enter()
       .append("circle")
       .attr("cx", d => x(d.t))
       .attr("cy", d => y(d.y))
       .attr("r",  d => d.z ? z(d.z) : 5)
       .attr("key", d => d.k + " " + d.t)
       .attr("stroke-width", "1")
       .attr("info",       d => (d.k ? ("<b><u>" + d.k + "</u></b><br/>"            ) : "") +
                                "<br/>t = " + formatTime(d.t) + " (" + d.t + ")" +
                                (yS  ? ("<br/>y: " + yS + " = "             + d.y   ) : "") + 
                                (zS  ? ("<br/>z: " + zS + " = "             + d.z   ) : "") +
                                (d.g ? ("<br/>g: " + (sS ? (sS + "=") : "") + d.g   ) : ""))
       .style("stroke", d => d.g ? g(d.g) : 'black')
       .style("fill", 'white')
       .on("click", function(d) {
          window.parent.parent.feedback("Evolution Point: " + d3.select(this).attr("info"));
          window.parent.parent.commands(d3.select(this).attr("info"), actions(url, d3.select(this).attr("key")));
          })
       .on("mouseover", function(d) {
          div.transition()		
             .duration(200)		
             .style("opacity", 0.9);		
          div.html(d3.select(this).attr("info"))	
             .style("left", (d3.select(this).attr("cx")) + "px")		
             .style("top",  (d3.select(this).attr("cy")) + "px");	
            })					
       .on("mouseout", function(d) {		
           div.transition()		
              .duration(2000)		
              .style("opacity", 0);	
       });
    }
    
  }
  
  
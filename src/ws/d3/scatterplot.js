function showScatterPlot(dataS, name, xS, yS, zS) {
  var w = 450;
  var h = 400;
  var margin = {top:10, right:40, bottom:30, left:30},
               width =  w - margin.left - margin.right,
               height = h - margin.top  - margin.bottom;
  var svg = d3.select("#scatter_area")
              .append("svg")
              .attr("width",  width  + margin.left + margin.right)
              .attr("height", height + margin.top  + margin.bottom)
              .append("g")
              .attr("transform", "translate(" + margin.left + "," + margin.top + ")");
  var data = JSON.parse(dataS.replace(/'/g, '"'));
  var x = d3.scaleLinear()
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
                 .text(xS));
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
                 .text(yS));
  svg.selectAll("whatever")
     .data(data)
     .enter()
     .append("circle")
     .attr("cx", function(d){return x(d.x)})
     .attr("cy", function(d){return y(d.y)})
     .attr("r",  function(d){return   d.z ? d.z : 3})
  }
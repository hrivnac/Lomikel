function showCorrelogram() {
  
  var otable = [{x:'a', y:'a', value:0.5},
                {x:'a', y:'b', value:0.6},
                {x:'a', y:'c', value:0.9},
                {x:'b', y:'a', value:0.6},
                {x:'b', y:'b', value:0.1},
                {x:'b', y:'c', value:0.4},
                {x:'c', y:'a', value:0.9},
                {x:'c', y:'b', value:0.4},
                {x:'c', y:'c', value:0.6}]
  

  const margin = {top:20, right:80, bottom:20, left:20},
                  width  = 430 - margin.left - margin.right,
                  height = 430 - margin.top  - margin.bottom
  
  const svg = d3.select("#corr_area")
                .append("svg")
                .attr("width",  width  + margin.left + margin.right)
                .attr("height", height + margin.top  + margin.bottom)
                .append("g")
                .attr("transform", `translate(${margin.left}, ${margin.top})`); 
  
  const domain = Array.from(new Set(otable.map(function(d) {return d.x })))
  const num = Math.sqrt(otable.length)

  const color = d3.scaleLinear()
                  .domain([-1, 0, 1])
                  .range(["#B22222", "#fff", "#000080"]);

  const size = d3.scaleSqrt()
                 .domain([0, 1])
                 .range([0, 9]);

  const x = d3.scalePoint()
              .range([0, width])
              .domain(domain)
  const y = d3.scalePoint()
              .range([0, height])
              .domain(domain)

  const cor = svg.selectAll(".cor")
                 .data(otable)
                 .join("g")
                 .attr("class", "cor")
                 .attr("transform", function(d) {return `translate(${x(d.x)}, ${y(d.y)})`});
  cor.filter(function(d) {const ypos = domain.indexOf(d.y);
                          const xpos = domain.indexOf(d.x);
                          return xpos <= ypos;
                          })
    .append("text")
    .attr("y", 5)
    .text(function(d) {if (d.x === d.y) {
                         return d.x;
                         }
                       else {
                         return d.value.toFixed(2);
                         }
                       })
    .style("font-size", 11)
    .style("text-align", "center")
    .style("fill", function(d) {if (d.x === d.y) {
                                  return "#000";
                                  }
                                else {
                                  return color(d.value);
                                  }
                                });

  cor.filter(function(d) {const ypos = domain.indexOf(d.y);
                          const xpos = domain.indexOf(d.x);
                          return xpos > ypos;
                          })
     .append("circle")
     .attr("r", function(d) {return size(Math.abs(d.value))})
     .style("fill", function(d) {if (d.x === d.y) {
                                   return "#000";
                                   }
                                 else {
                                   return color(d.value);
                                   }
                                 })
      .style("opacity", 0.8)

  var aS = d3.scaleLinear()
             .range([-margin.top + 5, height + margin.bottom - 5])
             .domain([1, -1]);

  var yA = d3.axisRight().scale(aS).tickPadding(7);

  var yWidth = width + margin.right / 2;
  var aG = svg.append('g')
              .attr('class', 'y axis')
              .call(yA)
              .attr('transform', `translate(${yWidth}, 0)`);

  var iR = d3.range(-1, 1.01, 0.01);
  var h = height / iR.length + 3;
  iR.forEach(function (d) {aG.append('rect').style('fill', color(d))
                                            .style('stroke-width', 0)
                                            .style('stoke', 'none')
                                            .attr('height', h)
                                            .attr('width', 10)
                                            .attr('x', 0)
                                            .attr('y', aS(d));
                           });      
      
  }
    
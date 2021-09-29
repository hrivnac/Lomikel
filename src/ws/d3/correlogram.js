function showCorrelogram(otable) {
      
  otable1 = [];
  var min = otable[0].value;
  var max = otable[0].value;
  for (o of otable) {
    otable1.push({x:o.y, y:o.x, value:o.value, info:o.info})
    //if (min > o.value) {
    //  min = o.value
    //  }
    if (max < o.value) {
      max = o.value
      }
    }
  min = 0;
  otable = otable.concat(otable1);
  med = (min + max ) / 2

  const margin = {top:40, right:100, bottom:40, left:40},
                  width  = 430 - margin.left - margin.right,
                  height = 430 - margin.top  - margin.bottom
  
  const svg = d3.select("#corr_area")
                .append("svg")
                .attr("width",  width  + margin.left + margin.right)
                .attr("height", height + margin.top  + margin.bottom)
                .append("g")
                .attr("transform", `translate(${margin.left}, ${margin.top})`); 
  
  const domain = Array.from(new Set(otable.map(function(d) {return d.x})));
  const num = domain.length;
  
  for (d of domain) {
    otable.push({x:d, y:d, value:0, info:""})
    }

  const color = d3.scaleLinear()
                  .domain([min, med, max])
                  .range(["#B22222", "#fff", "#000080"]);

  const size = d3.scaleSqrt()
                 .domain([0, max])
                 .range([0, width / (num * 4)]);

  const x = d3.scalePoint()
              .range([0, width])
              .domain(domain)
  const y = d3.scalePoint()
              .range([0, height])
              .domain(domain)
  const xSpace = x.range()[1] - x.range()[0]
  const ySpace = y.range()[1] - y.range()[0]

  const div = d3.select("body")
                .append("div")	
                .attr("class", "tooltip")				
                .style("opacity", 0);

  const cor = svg.selectAll(".cor")
                 .data(otable)
                 .join("g")
                 .attr("class", "cor")
                 .attr("transform", function(d) {return `translate(${x(d.x)}, ${y(d.y)})`});

  cor.append("rect")
     .attr("width",  xSpace / (num - 1))
     .attr("height", ySpace / (num - 1))
     .attr("x",     -xSpace / (num - 1) / 2)
     .attr("y",     -ySpace / (num - 1) / 2)                 
                            
  cor.filter(function(d) {const ypos = domain.indexOf(d.y);
                          const xpos = domain.indexOf(d.x);
                          return xpos <= ypos;
                          })
     .append("text")
     .attr("m1",    function(d) {return d.x})
     .attr("m2",    function(d) {return d.y})
     .attr("n1",    function(d) {return d.info.split("/")[0]})
     .attr("n2",    function(d) {return d.info.split("/")[1]})
     .attr("n12",   function(d) {return d.value})
     .attr("valid", function(d) {return domain.indexOf(d.x) != domain.indexOf(d.y) && d.info != ""})
     .attr("y", 5)
     .attr("info",  function(d) {return "<center><b><u>" + d.y + " => " + d.x + "</u></b></br>" +
                                       "intersection/sizeIn/sizeOut = " + d.value + "/" + d.info + "</center>"})
     .attr("popx",  function(d) {return x(d.x)})
     .attr("popy",  function(d) {return y(d.y)})
     .text(function(d) {if (d.x === d.y) {
                          return d.x;
                          }
                        else {
                          return d.value + "/"+ d.info;
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
                                 })
      .on("mouseover", function(d) {	
          if (d3.select(this).attr("valid")) {
            showVenn(d3.select(this).attr("n1"),
                     d3.select(this).attr("n2"),
                     d3.select(this).attr("n12"),
                     d3.select(this).attr("m1"),
                     d3.select(this).attr("m2"));
            document.getElementById("vennTxt").innerHTML = d3.select(this).attr("info");
            }
          });
      
  cor.filter(function(d) {const ypos = domain.indexOf(d.y);
                          const xpos = domain.indexOf(d.x);
                          return xpos > ypos;
                          })
     .append("circle")
     .attr("m1",    function(d) {return d.x})
     .attr("m2",    function(d) {return d.y})
     .attr("n1",    function(d) {return d.info.split("/")[0]})
     .attr("n2",    function(d) {return d.info.split("/")[1]})
     .attr("n12",   function(d) {return d.value})
     .attr("valid", function(d) {return domain.indexOf(d.x) != domain.indexOf(d.y) && d.info != ""})
     .attr("r",     function(d) {return size(Math.abs(d.value))})
     .attr("info",  function(d) {return "<center><b><u>" + d.x + " => " + d.y + "</u></b></br>" +
                                       "intersection/sizeIn/sizeOut = " + d.value + "/" + d.info + "</center>"})
     .attr("popx",  function(d) {return x(d.x)})
     .attr("popy",  function(d) {return y(d.y)})
     .style("fill", function(d) {if (d.x === d.y) {
                                   return "#000";
                                   }
                                 else {
                                   return color(d.value);
                                   }
                                 })
      .style("opacity", 0.8)
      .on("mouseover", function(d) {		
          if (d3.select(this).attr("valid")) {
            showVenn(d3.select(this).attr("n1"),
                     d3.select(this).attr("n2"),
                     d3.select(this).attr("n12"),
                     d3.select(this).attr("m1"),
                     d3.select(this).attr("m2"));
            document.getElementById("vennTxt").innerHTML = d3.select(this).attr("info");
            }
          });

  var aS = d3.scaleLinear()
             .range([-margin.top + 5, height + margin.bottom - 5])
             .domain([max, min]);

  var yA = d3.axisRight().scale(aS).tickPadding(7);

  var yWidth = width + margin.right / 2;
  var aG = svg.append('g')
              .attr('class', 'y axis')
              .call(yA)
              .attr('transform', `translate(${yWidth}, 0)`);

  var iR = d3.range(min, max * 1.01, max * 0.01);
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
    
function showCorrelogram(otable, vennPopupWindow) {

if (!vennPopupWindow || !vennPopupWindow.opener || vennPopupWindow.opener.closed) {
  vennPopupWindow = window.open("vennPopup.jsp",
                                "",
                                "height=700,width=1400,left=100,top=100,resizable=yes,scrollbars=yes,toolbar=yes,menubar=no,location=no,directories=no,status=yes");
  }
     
  tablemap = [];
  
  otable1 = [];
  var min = otable[0].value;
  var max = otable[0].value;
  for (o of otable) {
    otable1.push({x:o.y, y:o.x, value:o.value, info:o.info});
    tablemap.push(o.x + "-" + o.y);
    tablemap.push(o.y + "-" + o.x);
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
  
  const domain = Array.from(new Set(otable.map(function(d) {return d.x})));
  const num = domain.length;
  
  for (d of domain) {
    otable.push({x:d, y:d, value:0, info:""})
    tablemap.push(d + "-" + d);
    }
    
  console.log(tablemap);  
    
  for (d1 of domain) {
    for (d2 of domain) {
      console.log(d1 + " " + d2 + " " + tablemap.includes(d1 + "-" + d2));
      }
    }
    
  const width0  = num * 60 + 30; // 430
  const height0 = num * 60 + 30; // 430
  const margin = {top:(height0 / num / 2),
                  right:(width0 / num / 2 + 50),
                  bottom:(height0 / num / 2),
                  left:(width0 / num / 2)},
                 width  = width0  - margin.left - margin.right,
                 height = height0 - margin.top  - margin.bottom
  
  const svg = d3.select("#corr_area")
                .append("svg")
                .attr("width",  width  + margin.left + margin.right)
                .attr("height", height + margin.top  + margin.bottom)
                .append("g")
                .attr("transform", `translate(${margin.left}, ${margin.top})`); 

  const color = d3.scaleLinear()
                  .domain([0, 100])
                  .range(["black", "red"]);

  const size = d3.scaleSqrt()
                 .range([0, width / (num - 1) / 2])
                 .domain([0, 100]);
  const x = d3.scalePoint()
              .range([0, width])
              .domain(domain)
  const y = d3.scalePoint()
              .range([0, height])
              .domain(domain)
  const xSpace = x.range()[1] - x.range()[0]
  const ySpace = y.range()[1] - y.range()[0]
 
  const cor = svg.selectAll(".cor")
                 .data(otable)
                 .join("g")
                 .attr("class", "cor")
                 .attr("transform", function(d) {return `translate(${x(d.x)}, ${y(d.y)})`});
                 
  // rectangles for existing values
  cor.append("rect")
     .attr("width",  xSpace / (num - 1))
     .attr("height", ySpace / (num - 1))
     .attr("x",     -xSpace / (num - 1) / 2)
     .attr("y",     -ySpace / (num - 1) / 2) 
  
  // diagonal with names
  cor.filter(function(d) {const ypos = domain.indexOf(d.y);
                          const xpos = domain.indexOf(d.x);
                          return xpos == ypos;
                          })
     .append("text")
     .text(function(d) {return d.x})
     .style("font-size", 8)
     .style("font-weight", "bold")
     .style("text-align", "center")
     .style("fill", "black")

  // upper right half with circles
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
     .attr("p12",   function(d) {return 100 * parseInt(d.value) / (parseInt(d3.select(this).attr("n1")) + parseInt(d3.select(this).attr("n2")) - parseInt(d.value))})
     .attr("valid", function(d) {return domain.indexOf(d.x) != domain.indexOf(d.y) && d.info != ""})
     .attr("r",     function(d) {return size(d3.select(this).attr("p12"))})
     .attr("info",  function(d) {return "<center><b><u>" + d.x + "<br/>=><br/>" + d.y + "</u></b></br>" +
                                        "intersection/sizeIn/sizeOut = " + d.value + "/" + d.info + "</center>"})
     .attr("popx",  function(d) {return x(d.x)})
     .attr("popy",  function(d) {return y(d.y)})
     .style("fill", function(d) {return color(d3.select(this).attr("p12"))})
     .style("opacity", 0.8)
     .on("mouseover", function(d) {		
         if (d3.select(this).attr("valid")) {
           info = showVenn(d3.select(this).attr("n1"),
                           d3.select(this).attr("n2"),
                           d3.select(this).attr("n12"),
                           d3.select(this).attr("m1").replaceAll('.', ' '),
                           d3.select(this).attr("m2").replaceAll('.', ' '),
                           vennPopupWindow);
           vennPopupWindow.document.getElementById("vennPopupTxt").innerHTML = info;
           }
         });

  // lower left half with text
  cor.filter(function(d) {const ypos = domain.indexOf(d.y);
                          const xpos = domain.indexOf(d.x);
                          return xpos < ypos;
                          })
     .append("text")
     .attr("dy", "0em")
     .attr("m1",    function(d) {return d.x})
     .attr("m2",    function(d) {return d.y})
     .attr("n1",    function(d) {return d.info.split("/")[0]})
     .attr("n2",    function(d) {return d.info.split("/")[1]})
     .attr("n12",   function(d) {return d.value})
     .attr("p12",   function(d) {return 100 * parseInt(d.value) / (parseInt(d3.select(this).attr("n1")) + parseInt(d3.select(this).attr("n2")) - parseInt(d.value))})
     .attr("valid", function(d) {return domain.indexOf(d.x) != domain.indexOf(d.y) && d.info != ""})
     .attr("info",  function(d) {return "<center><b><u>" + d.y + "<br/>=><br/>" + d.x + "</u></b></br>" +
                                        "intersection/sizeIn/sizeOut = " + d.value + "/" + d.info + "</center>"})
     .attr("popx",  function(d) {return x(d.x)})
     .attr("popy",  function(d) {return y(d.y)})
     .text(function(d) {return d.value})
     .style("font-size", 8)
     .style("text-align", "center")
     .style("fill", function(d) {return color(d3.select(this).attr("p12"))})
     .on("mouseover", function(d) {		
         if (d3.select(this).attr("valid")) {
           info = showVenn(d3.select(this).attr("n1"),
                           d3.select(this).attr("n2"),
                           d3.select(this).attr("n12"),
                           d3.select(this).attr("m1").replaceAll('.', ' '),
                           d3.select(this).attr("m2").replaceAll('.', ' '),
                           vennPopupWindow);
           vennPopupWindow.document.getElementById("vennPopupTxt").innerHTML = info;
           }
         });
  cor.filter(function(d) {const ypos = domain.indexOf(d.y);
                          const xpos = domain.indexOf(d.x);
                          return xpos < ypos;
                          })
     .append("text")
     .attr("dy", "1em")
     .attr("m1",    function(d) {return d.x})
     .attr("m2",    function(d) {return d.y})
     .attr("n1",    function(d) {return d.info.split("/")[0]})
     .attr("n2",    function(d) {return d.info.split("/")[1]})
     .attr("n12",   function(d) {return d.value})
     .attr("p12",   function(d) {return 100 * parseInt(d.value) / (parseInt(d3.select(this).attr("n1")) + parseInt(d3.select(this).attr("n2")) - parseInt(d.value))})
     .attr("valid", function(d) {return domain.indexOf(d.x) != domain.indexOf(d.y) && d.info != ""})
     .attr("info",  function(d) {return "<center><b><u>" + d.y + "<br/>=><br/>" + d.x + "</u></b></br>" +
                                        "intersection/sizeIn/sizeOut = " + d.value + "/" + d.info + "</center>"})
     .attr("popx",  function(d) {return x(d.x)})
     .attr("popy",  function(d) {return y(d.y)})
     .text(function(d) {return d.info})
     .style("font-size", 8)
     .style("text-align", "center")
     .style("fill", function(d) {return color(d3.select(this).attr("p12"))})
     .on("mouseover", function(d) {		
         if (d3.select(this).attr("valid")) {
           info = showVenn(d3.select(this).attr("n1"),
                           d3.select(this).attr("n2"),
                           d3.select(this).attr("n12"),
                           d3.select(this).attr("m1").replaceAll('.', ' '),
                           d3.select(this).attr("m2").replaceAll('.', ' '),
                           vennPopupWindow);
           vennPopupWindow.document.getElementById("vennPopupTxt").innerHTML = info;
           }
         });
         
  // scaler   
     
  var aS = d3.scaleLinear()
             .range([-margin.top + 5, height + margin.bottom - 5])
             .domain([100, 0]);

  var yA = d3.axisRight().scale(aS).tickPadding(7);

  var yWidth = width + margin.right / 2;
  var aG = svg.append('g')
              .attr('class', 'y axis')
              .call(yA)
              .attr('transform', `translate(${yWidth}, 0)`);

  var iR = d3.range(0, 101, 1);
  var h = height / iR.length + 3;
  iR.forEach(function (d) {aG.append('rect').style('fill', color(d))
                                            .style('stroke-width', 0)
                                            .style('stroke', 'none')
                                            .attr('height', h)
                                            .attr('width', 10)
                                            .attr('x', 0)
                                            .attr('y', aS(d));
                           });      
      
  }
    

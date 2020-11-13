<div id="scatter_area"></div>

<script src="../d3-v6.0.0/d3.js"></script>

<script>
  var margin = {top:10, right:40, bottom:30, left:30},
               width =  450 - margin.left - margin.right,
               height = 400 - margin.top  - margin.bottom;
  var svg = d3.select("#scatter_area")
              .append("svg")
              .attr("width", width + margin.left + margin.right)
              .attr("height", height + margin.top + margin.bottom)
              .append("g")
              .attr("transform", "translate(" + margin.left + "," + margin.top + ")");
  var data = [{x:10, y:20}, {x:40, y:90}, {x:80, y:50}]
  var x = d3.scaleLinear()
            .domain([0, 100])        
            .range([0, width]);      
  svg.append('g')
     .attr("transform", "translate(0," + height + ")")
     .call(d3.axisBottom(x));
  var y = d3.scaleLinear()         
            .domain([0, 100])      
            .range([height, 0]);   
  svg.append('g')
     .call(d3.axisLeft(y));
  svg.selectAll("whatever")
     .data(data)
     .enter()
     .append("circle")
     .attr("cx", function(d){return x(d.x)})
     .attr("cy", function(d){return y(d.y)})
     .attr("r", 7)
</script>
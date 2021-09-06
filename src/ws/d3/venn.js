function showVenn(n1, n2, n12) {
  var sets = [{sets:['A'],      size:n1 },
              {sets:['B'],      size:n2 },
              {sets:['A', 'B'], size:n12}];
  var chart = venn.VennDiagram();
  d3.select("#venn").datum(sets).call(chart);
  }

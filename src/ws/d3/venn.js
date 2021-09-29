function showVenn(n1, n2, n12, m1, m2) {
  if (!m1) {
    m1 = "A";
    }
  if (!m2) {
    m2 = "B";
    }
  var sets = [{sets:[m1],     size:n1 },
              {sets:[m2],     size:n2 },
              {sets:[m1, m2], size:n12}];
  var chart = venn.VennDiagram();
  d3.select("#venn").datum(sets).call(chart);
  }

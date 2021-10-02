function showVenn(n1, n2, n12, m1, m2) {

  if (!m1) {
    m1 = "A";
    }
  if (!m2) {
    m2 = "B";
    }
 
  var n1d  = parseInt(n1);  
  var n2d  = parseInt(n2);  
  var n12d = parseInt(n12);  
    
  var p1  = 100.0 * (n1d          / (n1d + n2d - n12d)).toFixed(2);
  var p2  = 100.0 * (n2d          / (n1d + n2d - n12d)).toFixed(2);
  var p12 = 100.0 * (n12d         / (n1d + n2d - n12d)).toFixed(2);
  var q1  = 100.0 * ((n1d - n12d) / (n1d + n2d - n12d)).toFixed(2);
  var q2  = 100.0 * ((n2d - n12d) / (n1d + n2d - n12d)).toFixed(2);
    
  var info = "";
  if (m1 != "A" || m2 != "B") {
    info += "<b>A = " + m1 + "<br/> B = " + m2 + "</b>";
    }
  info += "<hr/>";
  info += "<table>";
  info += "  <tr><td><b>A</b>    </td><td>" + (n1d             ) + "</td><td>(" + p1  + "%)</td></tr>";
  info += "  <tr><td><b>B</b>    </td><td>" + (n2d             ) + "</td><td>(" + p2  + "%)</td></tr>";
  info += "  <tr><td><b>A^B</b>  </td><td>" + (n12d            ) + "</td><td>(" + p12 + "%)</td></tr>";
  info += "  <tr><td><b>A-A^B</b></td><td>" + (n1d - n12d      ) + "</td><td>(" + q1  + "%)</td></tr>";
  info += "  <tr><td><b>B-A^B</b></td><td>" + (n1d - n12d      ) + "</td><td>(" + q2  + "%)</td></tr>";
  info += "  <tr><td><b>AvB</b>  </td><td>" + (n1d + n2d - n12d) + "</td><td>(100%)        </td></tr>";
  info += "  </table>";
    
  var sets = [{sets:[m1],     size:n1 },
              {sets:[m2],     size:n2 },
              {sets:[m1, m2], size:n12}];
  var chart = venn.VennDiagram();
  d3.select("#venn").datum(sets).call(chart);
  
  return info;
  
  }

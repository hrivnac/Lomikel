var container = document.getElementById('vis');
var timeline;

// Construct VIS hist
function showHist(itemsS, yS) {
  
  var items = JSON.parse(itemsS);  
  var groups = new vis.DataSet();
  
  for (i in yS.split(" ")) {
    groups.add({
      id: yS[i],
      content: yS[i],
      options: {
        drawPoints: {
          style: "circle", 
          },
        shaded: {
          orientation: "bottom",
          },
        },
      });
    };
    
  timeline = new vis.Graph2d(container, items, groups, options4Hist);
  }

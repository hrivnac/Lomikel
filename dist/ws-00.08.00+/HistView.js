var container = document.getElementById('vis');
var timeline;

function showHist(itemsS, yS) {
  
  var items = JSON.parse(itemsS);  
  var groups = new vis.DataSet();
  
  var y = yS.split(" ");
  for (var i = 0; i < y.length; i++) {
    groups.add({
      id: y[i],
      content: y[i],
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

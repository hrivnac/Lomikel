var container = document.getElementById('visnetwork');
var timeline;

// Construct VIS hist
function showHist(itemsS, yS) {

  var items = JSON.parse(itemsS);
  
  var groups = new vis.DataSet();
  
  for (y in yS.split(" ")) {
    console.log(y);
    groups.add({
      id: y,
      content: y,
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

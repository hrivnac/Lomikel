// Graph options
var options4Graph = {
  autoResize:true,
  height:"100%",
  width:"100%",
  physics: {
    forceAtlas2Based: {
      gravitationalConstant:-500,
      centralGravity:0.05,
      springLength:20,
      damping: 0.5,
      springConstant:0.18
      },
    maxVelocity:100,
    solver:"forceAtlas2Based",
    timestep:0.25,
    stabilization: {
      iterations:50,
      fit:true
      },
    adaptiveTimestep:true
    },
  layout: {
    improvedLayout:true,
    hierarchical:false
    },
  interaction: {
    dragNodes:true
    },
  nodes: {
    shape:"dot",
    scaling: {
      customScalingFunction: function (min, max, total, value) {
        if (value == 0) {
          return 0.5;
          }
        return value / max;
        },
      }
    },
  edges: {
    smooth: {
      type:"dynamic",
      forceDirection:"none",
      roundness:0.5
      },
    scaling: {
      customScalingFunction: function (min, max, total, value) {
        if (min === max) {
          return 0.5;
          }
        var scale = 1 / (max - min);
        return Math.max(0, (value - min) * scale);
        }
      }
    },
  groups: {
    " ":{color:{background:"white", border:"black"}}
    }
  };
  
// Hist Options
var options4Hist = {
  //sort: false,
  //sampling:false,
  //style:'points',
  dataAxis: {
    showMinorLabels: true,
    icons: false,
    },
  legend: {
    left: {
      position: "bottom-left"
      }
    },
  };

  
  
function showSkyView(dataS, gMapS, name, zS, sS) {
    
  var config = {
    form: true,
    formFields: {download: true},
    datapath: "../d3-celestial-0.7.32/data/",
    projection: "aitoff",
    transform: "galactic",
    background: {fill: "#fff", stroke: "#000", opacity: 1, width: 1},
    stars: {
      colors: false,
      names: false,
      propername: false,
      style: {fill: "#000", opacity:1},
      limit: 6,
      size: 5
      },
    dsos: {show: false, size: 10},
    mw: {style: {fill:"#996", opacity: 0.1}},
    };
  
  var pointStyle = {
        stroke: "rgba(255, 0, 204, 1)",
        fill:   "rgba(255, 0, 204, 0.15)"
        },
      textStyle = {
        fill: "rgba(255, 0, 204, 1)",
        font: "normal bold 15px Helvetica, Arial, sans-serif",
        align: "left",
        baseline: "bottom"
        };
  
  var jsonSnr = {
    "type": "FeatureCollection",
    "features": [
      {"type": "type1",
       "id": "id1",
       "properties": {"name": "name1", "dim": 10},
       "geometry": {"type": "Point", "coordinates": [-80.7653, 38.7837]}
       },
      {"type": "type2",
       "id": "id2",
       "properties": {"name": "name2", "dim": 20},
       "geometry": {"type": "Point", "coordinates": [-90.7653, 48.7837]}
       }
    ]};
  
  var PROXIMITY_LIMIT = 20;
  
  Celestial.add({
    type: "point",      
    callback: function(error, json) {
      if (error) return console.warn(error);
      var dsn = Celestial.getData(jsonSnr, config.transform);
      Celestial.container
               .selectAll(".snrs")
               .data(dsn.features)
               .enter()
               .append("path")
               .attr("class", "snr"); 
      Celestial.redraw();
      },   
    redraw: function() {  
      var m = Celestial.metrics(),
          quadtree = d3.geom.quadtree().extent([[-1, -1], [m.width + 1, m. height + 1]])([]);
      Celestial.container.selectAll(".snr").each(function(d) {
        if (Celestial.clip(d.geometry.coordinates)) {
          var pt = Celestial.mapProjection(d.geometry.coordinates);
          var r = Math.pow(parseInt(d.properties.dim) * 0.25, 0.5);
          Celestial.setStyle(pointStyle);
          Celestial.context.beginPath();
          Celestial.context.arc(pt[0], pt[1], r, 0, 2 * Math.PI);
          Celestial.context.closePath();
          Celestial.context.stroke();
          Celestial.context.fill();
          var nearest = quadtree.find(pt);
          if (!nearest || distance(nearest, pt) > PROXIMITY_LIMIT) {
            quadtree.add(pt)
            Celestial.setTextStyle(textStyle);
            Celestial.context.fillText(d.properties.name, pt[0] + r + 2, pt[1] + r + 2);
            }
          }
        });
      }
    });
  
  function distance(p1, p2) {
    var d1 = p2[0] - p1[0],
        d2 = p2[1] - p1[1];
    return Math.sqrt(d1 * d1 + d2 * d2);
    }
  
  Celestial.display(config);
   
  }
    
  //  Celestial.display({
  //    });
 
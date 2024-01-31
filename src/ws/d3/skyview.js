function showSkyView(data, name, url) {

  // d3-scale-chromatic/src/categorical/Category10.js
  function colorsF(specifier) {
    var n = specifier.length / 6 | 0, colors = new Array(n), i = 0;
    while (i < n) colors[i] = "#" + specifier.slice(i * 6, ++i * 6);
    return colors;
    }
  const set1 = colorsF("1f77b4ff7f0e2ca02cd627289467bd8c564be377c27f7f7fbcbd2217becf");
  const colors = set1;
    
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
    
  var features = [];      
  
  var zmin;
  var zmax;
  if (data[0].z) {
    zmin = data[0].z;
    zmax = zmin;
    for (i in data) {
      d = data[i];
      if (d.z < zmin) {
        zmin = d.z;
        }
      if (d.z > zmax) {
        zmax = d.z;
        }
      }
    }
    
  for (i in data) {
    var d = data[i];
    var info = name + " " + (d.k ? d.k : "");
    var size = (d.z ? 100 - (200 - 100) * (d.z - zmin) / (zmax - zmin) : 100); // TBD: better
    var color = (d.g ? colors[d.g % 10 | 0] : 'red');
    features.push({"properties": {"info": info, "dim": size, "color": color},
                   "geometry": {"type": "Point", "coordinates": [d.x, d.y]}});
    };
     
  var jsonSnr = {
    "type": "FeatureCollection",
    "features": features
    };
    
  var PROXIMITY_LIMIT = 20;
  
  function getXY(canvas, event){
    const rect = canvas.getBoundingClientRect();
    const y = event.clientY - rect.top;
    const x = event.clientX - rect.left;
    return [x, y];
    };
  
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
          Celestial.setStyle({stroke: 'black',
                              fill:   d.properties.color});
          Celestial.context.beginPath();
          Celestial.context.arc(pt[0], pt[1], r, 0, 2 * Math.PI);
          Celestial.context.closePath();
          Celestial.context.stroke();
          Celestial.context.fill();
          var nearest = quadtree.find(pt);
          if (!nearest || distance(nearest, pt) > PROXIMITY_LIMIT) {
            quadtree.add(pt)
            Celestial.setTextStyle({fill:     d.properties.color,
                                    font:     "normal 16px Helvetica, Arial, sans-serif",
                                    align:    "left",
                                    baseline: "bottom"
                                    });
            Celestial.context.fillText(d.properties.info, pt[0] + r + 2, pt[1] + r + 2);
            }
          const path = new Path2D();
          path.arc(pt[0], pt[1], r, 0, 2 * Math.PI);
          path.closePath()
          document.addEventListener("click",  function (e) {
            if (distance(pt, getXY(Celestial.context.canvas, e)) < 5) {

              window.parent.parent.feedback("Sky Point: <b><u>" + d.properties.info + "</u></b>");
              window.parent.parent.commands("<b><u>" + d.properties.info + "</u></b>", actions(url, d.properties.info));
              }
            },
            false);
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
 

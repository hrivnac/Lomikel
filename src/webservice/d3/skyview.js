/* Opt-in graph-wide SkyView, keeping object IDs as text and positions as numbers. */
(function (root) {
  'use strict';
  function prepare(values) {
    if (!Array.isArray(values)) return [];
    return values.filter(function (p) {
      return p && typeof p.objectId === 'string' && Number.isFinite(p.ra) &&
             p.ra >= 0 && p.ra < 360 && Number.isFinite(p.dec) && p.dec >= -90 && p.dec <= 90;
    });
  }
  root.skyPrepare = prepare;

  function render(values, requested, missing) {
    var points = prepare(values);
    var summary = document.getElementById('sky-summary');
    var list = document.getElementById('sky-list');
    var count = new Set(points.map(function (point) { return point.objectId; })).size;
    summary.textContent = count + ' of ' + requested + ' graph objects plotted (' + points.length +
      ' sky position' + (points.length === 1 ? '' : 's') + '). ' + missing + ' without coordinates.';
    if (!points.length) { list.textContent = 'No coordinates were found for objects in this graph.'; return; }
    var fragment = document.createDocumentFragment();
    points.forEach(function (point) {
      var row = document.createElement('div');
      row.className = 'sky-coordinate';
      row.textContent = point.objectId + '  ·  RA ' + point.ra.toFixed(5) + '°  ·  Dec ' + point.dec.toFixed(5) + '°';
      fragment.appendChild(row);
    });
    list.appendChild(fragment);
    var config = {
      form: true, formFields: {download: true},
      datapath: '../../d3-celestial-0.7.32/data/',
      projection: 'aitoff', transform: 'galactic',
      background: {fill:'#fff', stroke:'#607584', opacity:1, width:1},
      stars: {colors:false, names:false, propername:false, style:{fill:'#bcc8d2',opacity:1},limit:6,size:3},
      dsos: {show:false}, mw: {style:{fill:'#996',opacity:.08}}
    };
    var features = points.map(function (point) {
      return {type:'Feature', properties:{objectId:point.objectId, color:/^ZTF/.test(point.objectId)?'#df6e35':'#266cac'},
        geometry:{type:'Point',coordinates:[point.ra,point.dec]}};
    });
    var collection = {type:'FeatureCollection',features:features};
    Celestial.add({type:'point',
      callback: function (error) {
        if (error) { summary.textContent += ' Background sky data could not be loaded.'; return; }
        var transformed = Celestial.getData(collection, config.transform);
        Celestial.container.selectAll('.sky-objects').data(transformed.features).enter()
          .append('path').attr('class','sky-objects');
        Celestial.redraw();
      },
      redraw: function () {
        Celestial.container.selectAll('.sky-objects').each(function (point) {
          if (!Celestial.clip(point.geometry.coordinates)) return;
          var xy = Celestial.mapProjection(point.geometry.coordinates);
          if (!xy || !Number.isFinite(xy[0]) || !Number.isFinite(xy[1])) return;
          Celestial.setStyle({stroke:'#173450',fill:point.properties.color});
          Celestial.context.beginPath();
          Celestial.context.arc(xy[0],xy[1],4,0,2*Math.PI);
          Celestial.context.closePath();
          Celestial.context.stroke(); Celestial.context.fill();
        });
      }
    });
    Celestial.display(config);
  }
  root.skyRender = render;
  var element = document.getElementById('sky-points');
  if (element) {
    try { render(JSON.parse(element.textContent), root.skyRequested, root.skyMissing); }
    catch (error) { document.getElementById('sky-summary').textContent = 'SkyView could not display the positions.'; }
  }
})(window);

/* Opt-in object SkyView, keeping object IDs as text and positions as numbers. */
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
  function fitWidth(width, height) {
    // The Aitoff canvas is about twice as wide as it is high. Reserve room for
    // its border and fit both dimensions rather than clipping the bottom.
    return Math.max(140, Math.floor(Math.min(width - 34, (height - 8) * 2)));
  }
  root.skyFitWidth = fitWidth;
  function mapSize(canvas) {
    // Celestial projects into CSS-sized coordinates, then scales the backing
    // bitmap by devicePixelRatio. The backing width is not a hit-test width.
    var scale = root.devicePixelRatio || 1;
    return {width: parseFloat(canvas.style.width) || canvas.width / scale,
      height: parseFloat(canvas.style.height) || canvas.height / scale};
  }

  function render(values, requested, missing) {
    var points = prepare(values);
    var summary = document.getElementById('sky-summary');
    var list = document.getElementById('sky-list');
    var count = new Set(points.map(function (point) { return point.objectId; })).size;
    summary.textContent = count + ' of ' + requested + ' objects plotted (' + points.length +
      ' sky position' + (points.length === 1 ? '' : 's') + '). ' + missing + ' without coordinates.';
    var legend = document.getElementById('sky-legend');
    ['ZTF', 'LSST'].forEach(function (survey) {
      if (!points.some(function (point) { return /^ZTF/.test(point.objectId) === (survey === 'ZTF'); })) return;
      var key = document.createElement('span');
      key.className = 'sky-legend-' + survey.toLowerCase();
      key.textContent = survey;
      legend.appendChild(key);
    });
    var settings = document.getElementById('sky-settings-toggle');
    if (!points.length) {
      settings.hidden = true;
      list.textContent = 'No coordinates were found for these objects.';
      return;
    }
    settings.addEventListener('click', function () {
      var expanded = document.body.classList.toggle('sky-settings-open');
      settings.setAttribute('aria-expanded', String(expanded));
      resizeMap();
    });
    var fragment = document.createDocumentFragment();
    points.forEach(function (point) {
      var row = document.createElement('div');
      row.className = 'sky-coordinate';
      row.textContent = point.objectId + '  ·  RA ' + point.ra.toFixed(5) + '°  ·  Dec ' + point.dec.toFixed(5) + '°';
      fragment.appendChild(row);
    });
    list.appendChild(fragment);
    var config = {
      form: true, controls: true, formFields: {download:true},
      datapath: '../../d3-celestial-0.7.32/data/',
      projection: 'aitoff', transform: 'galactic',
      background: {fill:'#fff', stroke:'#607584', opacity:1, width:1},
      stars: {colors:false, names:false, propername:false, style:{fill:'#bcc8d2',opacity:1},limit:6,size:3},
      dsos: {show:false}, mw: {style:{fill:'#996',opacity:.08}}
    };
    var stage = document.getElementById('celestial-map');
    config.width = fitWidth(stage.clientWidth, stage.clientHeight);
    var features = points.map(function (point) {
      return {type:'Feature', properties:{objectId:point.objectId, color:/^ZTF/.test(point.objectId)?'#df6e35':'#266cac'},
        geometry:{type:'Point',coordinates:[point.ra,point.dec]}};
    });
    var collection = {type:'FeatureCollection',features:features};
    var hitPoints = [];
    var tooltip = document.createElement('div');
    tooltip.className = 'sky-hover';
    tooltip.hidden = true;
    stage.appendChild(tooltip);
    function hideTooltip() { tooltip.hidden = true; }
    function installHover() {
      var canvas = stage.querySelector('canvas');
      if (!canvas) return;
      canvas.addEventListener('pointermove', function (event) {
        var rect = canvas.getBoundingClientRect();
        if (!rect.width || !rect.height) return hideTooltip();
        var size = mapSize(canvas);
        var x = (event.clientX - rect.left) * size.width / rect.width;
        var y = (event.clientY - rect.top) * size.height / rect.height;
        var nearest = null, distance = Infinity;
        hitPoints.forEach(function (point) {
          var d = Math.hypot(point.x - x, point.y - y);
          if (d < distance) { nearest = point; distance = d; }
        });
        if (!nearest || distance > 8 * size.width / rect.width) return hideTooltip();
        tooltip.textContent = nearest.objectId;
        var stageRect = stage.getBoundingClientRect();
        tooltip.style.left = (rect.left - stageRect.left + nearest.x * rect.width / size.width) + 'px';
        tooltip.style.top = (rect.top - stageRect.top + nearest.y * rect.height / size.height - 10) + 'px';
        tooltip.hidden = false;
      });
      canvas.addEventListener('pointerleave', hideTooltip);
    }
    Celestial.add({type:'point',
      callback: function (error) {
        if (error) { summary.textContent += ' Background sky data could not be loaded.'; return; }
        var transformed = Celestial.getData(collection, config.transform);
        Celestial.container.selectAll('.sky-objects').data(transformed.features).enter()
          .append('path').attr('class','sky-objects');
        Celestial.redraw();
        installHover();
      },
      redraw: function () {
        hitPoints = [];
        // Celestial replaces its internal projection when changing map types;
        // the exported mapProjection keeps referring to the original Aitoff.
        var projection = Celestial.map.projection();
        Celestial.container.selectAll('.sky-objects').each(function (point) {
          if (!Celestial.clip(point.geometry.coordinates)) return;
          var xy = projection(point.geometry.coordinates);
          if (!xy || !Number.isFinite(xy[0]) || !Number.isFinite(xy[1])) return;
          Celestial.setStyle({stroke:'#173450',fill:point.properties.color});
          Celestial.context.beginPath();
          Celestial.context.arc(xy[0],xy[1],4,0,2*Math.PI);
          Celestial.context.closePath();
          Celestial.context.stroke(); Celestial.context.fill();
          hitPoints.push({x:xy[0],y:xy[1],objectId:point.properties.objectId});
          Celestial.context.save();
          Celestial.context.font = '9px system-ui, sans-serif';
          Celestial.context.textAlign = 'left';
          Celestial.context.textBaseline = 'middle';
          Celestial.context.lineWidth = 2.5;
          Celestial.context.strokeStyle = '#fff';
          Celestial.context.fillStyle = point.properties.color;
          var canvasSize = mapSize(Celestial.context.canvas);
          var textWidth = Celestial.context.measureText(point.properties.objectId).width;
          var labelX = xy[0] + 7;
          if (labelX + textWidth > canvasSize.width - 4) labelX = xy[0] - textWidth - 7;
          labelX = Math.max(4, labelX);
          var labelY = Math.max(7, Math.min(canvasSize.height - 7, xy[1] - 7));
          Celestial.context.strokeText(point.properties.objectId, labelX, labelY);
          Celestial.context.fillText(point.properties.objectId, labelX, labelY);
          Celestial.context.restore();
        });
      }
    });
    Celestial.display(config);
    var lastWidth = config.width;
    function resizeMap() {
      var width = fitWidth(stage.clientWidth, stage.clientHeight);
      if (width === lastWidth) return;
      lastWidth = width;
      Celestial.resize({width:width});
    }
    if (root.ResizeObserver) {
      root.skyResizeObserver = new root.ResizeObserver(resizeMap);
      root.skyResizeObserver.observe(stage);
      if (root.frameElement) root.skyResizeObserver.observe(root.frameElement);
    }
    document.getElementById('sky-coordinates').addEventListener('toggle', resizeMap);
    root.addEventListener('resize', resizeMap);
  }
  root.skyRender = render;
  var element = document.getElementById('sky-points');
  if (element) {
    try { render(JSON.parse(element.textContent), root.skyRequested, root.skyMissing); }
    catch (error) { document.getElementById('sky-summary').textContent = 'SkyView could not display the positions.'; }
  }
})(window);

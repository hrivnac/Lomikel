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
  function prepareTrajectories(values) {
    if (!Array.isArray(values)) throw new TypeError('Invalid SS trajectories');
    return values.map(function (track) {
      if (!track || typeof track.objectId !== 'string' || !/^\d{1,24}$/.test(track.objectId) ||
          !Array.isArray(track.sources) || !track.sources.length) throw new TypeError('Invalid SS trajectory');
      var seen = new Set();
      var sources = track.sources.map(function (source) {
        if (!source || typeof source.sourceId !== 'string' || !/^\d{1,64}$/.test(source.sourceId) ||
            seen.has(source.sourceId) || !Number.isFinite(source.mjd) || source.mjd <= 0 ||
            !Number.isFinite(source.ra) || source.ra < 0 || source.ra >= 360 ||
            !Number.isFinite(source.dec) || source.dec < -90 || source.dec > 90)
          throw new TypeError('Invalid SS source');
        seen.add(source.sourceId);
        return {sourceId:source.sourceId,mjd:source.mjd,ra:source.ra,dec:source.dec};
      });
      sources.sort(function (a,b) { return a.mjd - b.mjd || a.sourceId.localeCompare(b.sourceId); });
      return {objectId:track.objectId,sources:sources};
    });
  }
  root.skyPrepareTrajectories = prepareTrajectories;
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

  function render(values, requested, missing, histories) {
    var points = prepare(values);
    var trajectories = prepareTrajectories(histories || []);
    var allSources = trajectories.reduce(function (n, track) { return n + track.sources.length; }, 0);
    var summary = document.getElementById('sky-summary');
    var list = document.getElementById('sky-list');
    var count = new Set(points.map(function (point) { return point.objectId; })
      .concat(trajectories.map(function (track) { return track.objectId; }))).size;
    var total = points.length + allSources;
    summary.textContent = count + ' of ' + requested + ' objects plotted (' + total +
      ' sky position' + (total === 1 ? '' : 's') +
      (trajectories.length ? ', ' + trajectories.length + ' trajector' + (trajectories.length === 1 ? 'y' : 'ies') : '') +
      '). ' + missing + ' without coordinates.';
    var legend = document.getElementById('sky-legend');
    ['ZTF', 'LSST'].forEach(function (survey) {
      if (!points.some(function (point) { return /^ZTF/.test(point.objectId) === (survey === 'ZTF'); }) &&
          !(survey === 'LSST' && trajectories.length)) return;
      var key = document.createElement('span');
      key.className = 'sky-legend-' + survey.toLowerCase();
      key.textContent = survey;
      legend.appendChild(key);
    });
    var settings = document.getElementById('sky-settings-toggle');
    if (!total) {
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
    trajectories.forEach(function (track) {
      track.sources.forEach(function (source) {
        var row = document.createElement('div');
        row.className = 'sky-coordinate';
        row.textContent = track.objectId + '  ·  source ' + source.sourceId +
          '  ·  MJD (TAI) ' + source.mjd + '  ·  RA ' + source.ra.toFixed(5) +
          '°  ·  Dec ' + source.dec.toFixed(5) + '°';
        fragment.appendChild(row);
      });
    });
    list.appendChild(fragment);
    var config = {
      form: true, controls: true, formFields: {download:true},
      datapath: '../d3-celestial-0.7.32/data/',
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
    trajectories.forEach(function (track) {
      track.sources.forEach(function (source,index) {
        features.push({type:'Feature',properties:{objectId:track.objectId,color:'#266cac',
          sourceId:source.sourceId,mjd:source.mjd,order:index,latest:index===track.sources.length-1},
          geometry:{type:'Point',coordinates:[source.ra,source.dec]}});
      });
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
          if (d <= distance) { nearest = point; distance = d; }
        });
        if (!nearest || distance > 8 * size.width / rect.width) return hideTooltip();
        tooltip.textContent = nearest.sourceId ?
          nearest.objectId + ' · source ' + nearest.sourceId + ' · MJD (TAI) ' + nearest.mjd :
          nearest.objectId;
        var stageRect = stage.getBoundingClientRect();
        tooltip.style.left = (rect.left - stageRect.left + nearest.x * rect.width / size.width) + 'px';
        tooltip.style.top = (rect.top - stageRect.top + nearest.y * rect.height / size.height - 10) + 'px';
        tooltip.hidden = false;
      });
      canvas.addEventListener('pointerleave', hideTooltip);
    }
    function neighborDistance(a, b, width) {
      if (!a || !b || !Number.isFinite(a.x) || !Number.isFinite(a.y) ||
          !Number.isFinite(b.x) || !Number.isFinite(b.y)) return Infinity;
      var dx = Math.abs(a.x-b.x);
      return Math.hypot(Math.min(dx,Math.abs(width-dx)),a.y-b.y);
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
        var size = mapSize(Celestial.context.canvas);
        var visible = [], tracks = new Map();
        Celestial.container.selectAll('.sky-objects').each(function (point) {
          var xy = Celestial.clip(point.geometry.coordinates) ? projection(point.geometry.coordinates) : null;
          var position = xy && Number.isFinite(xy[0]) && Number.isFinite(xy[1]) ?
            {x:xy[0],y:xy[1],point:point} : null;
          if (point.properties.sourceId) {
            if (!tracks.has(point.properties.objectId)) tracks.set(point.properties.objectId, []);
            // Keep hidden endpoints for geoPath to clip a crossing segment.
            // Only projected, visible endpoints become markers or hover targets.
            tracks.get(point.properties.objectId)[point.properties.order] = position || {point:point};
          }
          if (position) visible.push(position);
        });
        // Draw paths before points so even densely sampled histories remain visible.
        Celestial.context.save();
        Celestial.context.strokeStyle = '#266cac';
        Celestial.context.lineWidth = 1.35;
        tracks.forEach(function (positions) {
          for (var i=1;i<positions.length;i++) {
            if (!positions[i-1] || !positions[i]) continue;
            // Celestial's active D3 geoPath clips/interpolates at this
            // projection's seam (including after changing map projection).
            Celestial.context.beginPath();
            Celestial.map({type:'LineString',coordinates:[
              positions[i-1].point.geometry.coordinates, positions[i].point.geometry.coordinates]});
            Celestial.context.stroke();
          }
        });
        Celestial.context.restore();
        visible.forEach(function (position) {
          var point = position.point, xy = [position.x,position.y];
          var radius = 4;
          if (point.properties.sourceId) {
            var positions = tracks.get(point.properties.objectId), order = point.properties.order;
            var gap = Math.min(neighborDistance(position, positions[order-1], size.width),
              neighborDistance(position, positions[order+1], size.width));
            radius = Number.isFinite(gap) ? Math.max(0.65,Math.min(3.5,gap*0.28)) : 3.5;
            if (point.properties.latest) radius = Number.isFinite(gap) ?
              Math.max(radius,Math.min(3.5,Math.max(1.15,gap*0.36))) : 4;
          }
          Celestial.setStyle({stroke:'#173450',fill:point.properties.color});
          Celestial.context.beginPath();
          Celestial.context.arc(xy[0],xy[1],radius,0,2*Math.PI);
          Celestial.context.closePath();
          Celestial.context.stroke(); Celestial.context.fill();
          hitPoints.push({x:xy[0],y:xy[1],objectId:point.properties.objectId,
            sourceId:point.properties.sourceId,mjd:point.properties.mjd});
          if (point.properties.sourceId && !point.properties.latest) return;
          Celestial.context.save();
          Celestial.context.font = '9px system-ui, sans-serif';
          Celestial.context.textAlign = 'left';
          Celestial.context.textBaseline = 'middle';
          Celestial.context.lineWidth = 2.5;
          Celestial.context.strokeStyle = '#fff';
          Celestial.context.fillStyle = point.properties.color;
          var textWidth = Celestial.context.measureText(point.properties.objectId).width;
          var labelX = xy[0] + 7;
          if (labelX + textWidth > size.width - 4) labelX = xy[0] - textWidth - 7;
          labelX = Math.max(4, labelX);
          var labelY = Math.max(7, Math.min(size.height - 7, xy[1] - 7));
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
    try {
      var historyElement = document.getElementById('sky-trajectories');
      render(JSON.parse(element.textContent), root.skyRequested, root.skyMissing,
        historyElement ? JSON.parse(historyElement.textContent) : []);
    }
    catch (error) { document.getElementById('sky-summary').textContent = 'SkyView could not display the positions.'; }
  }
})(window);

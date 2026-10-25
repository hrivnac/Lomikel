/* Bounded single-object D3 light curves for the preview Plot tab. */
(function (root) {
  'use strict';
  var colors = {u:'#8c54a1',g:'#27834c',r:'#c94e46',i:'#d08125',z:'#874356',y:'#55748a'};
  var sequence = 0;
  var controller;
  function prepare(data, expectedId) {
    if (!data || data.objectId !== expectedId || !/^(?:ZTF[A-Za-z0-9_-]{1,80}|[0-9]{1,24})$/.test(expectedId) ||
        (data.survey !== 'ZTF' && data.survey !== 'LSST') ||
        (data.survey === 'ZTF') !== expectedId.startsWith('ZTF')) throw new Error('Invalid light curve object');
    if (!Array.isArray(data.points) || data.points.length > 10000 ||
        !Number.isSafeInteger(data.totalRows) || data.totalRows > 10000 || data.totalRows < 0 ||
        !Number.isSafeInteger(data.excluded) || data.excluded < 0 ||
        data.points.length + data.excluded !== data.totalRows) throw new Error('Invalid light curve count or limit');
    var allowed = data.survey === 'ZTF' ? 'gr' : 'ugrizy';
    if (!data.points.every(function (point) {
      return point && typeof point.sourceId === 'string' && /^[0-9]{1,24}$/.test(point.sourceId) &&
        typeof point.band === 'string' && point.band.length === 1 && allowed.includes(point.band) &&
        Number.isFinite(point.mjd) && Number.isFinite(point.magnitude) &&
        Number.isFinite(point.magnitudeError) && point.magnitudeError >= 0;
    })) throw new Error('Invalid light curve point or band');
    var sorted = data.points.slice().sort(function (a,b) { return a.mjd - b.mjd; });
    return {survey:data.survey,objectId:expectedId,points:sorted,totalRows:data.totalRows,
      excluded:data.excluded,bands:Array.from(new Set(sorted.map(function (point) { return point.band; })))
        .sort(function (a,b) { return allowed.indexOf(a)-allowed.indexOf(b); })};
  }
  root.lightCurvePrepare = prepare;
  function chart(data) {
    var area = document.getElementById('lightcurve-chart');
    var legend = document.getElementById('lightcurve-legend');
    area.replaceChildren(); legend.replaceChildren();
    var count = {};
    data.bands.forEach(function (band) {
      count[band] = data.points.filter(function (point) { return point.band === band; }).length;
      var label = document.createElement('span');
      label.className = 'lightcurve-band';
      label.style.borderColor = colors[band];
      label.textContent = band + ' · ' + count[band];
      legend.appendChild(label);
    });
    var status = document.getElementById('lightcurve-status');
    status.textContent = data.survey + ' ' + data.objectId + ': ' + data.points.length +
      ' plotted observation' + (data.points.length === 1 ? '' : 's') +
      (data.excluded ? ' (' + data.excluded + ' excluded: no usable magnitude or band)' : '') + '.';
    document.getElementById('lightcurve-note').textContent = data.survey === 'LSST' ?
      'LSST difference-image PSF flux converted from nJy to AB magnitude (31.4 − 2.5 log₁₀ flux); nonpositive flux is excluded. Time: MJD TAI. Brighter magnitudes are higher.' :
      'ZTF valid detections (g/r), PSF magnitude versus MJD converted from JD. Brighter magnitudes are higher.';
    if (!data.points.length) {
      area.textContent = data.totalRows ? 'No observations have a usable magnitude.' : 'No observations found for this object.';
      return;
    }
    var width = Math.max(360, area.clientWidth || 720);
    var height = Math.max(290, Math.min(520, Math.round((root.innerHeight || 560)*.69)));
    var margin = {top:20,right:28,bottom:55,left:68};
    var innerWidth = width-margin.left-margin.right, innerHeight = height-margin.top-margin.bottom;
    var mjd = d3.extent(data.points,function (p) { return p.mjd; });
    var span = Math.max(1,(mjd[1]-mjd[0])*.03);
    var x = d3.scaleLinear().domain([mjd[0]-span,mjd[1]+span]).range([0,innerWidth]);
    var lower = d3.min(data.points,function (p) { return p.magnitude-p.magnitudeError; });
    var upper = d3.max(data.points,function (p) { return p.magnitude+p.magnitudeError; });
    var magPad = Math.max(.2,(upper-lower)*.08);
    var y = d3.scaleLinear().domain([upper+magPad,lower-magPad]).range([innerHeight,0]);
    var svg = d3.select(area).append('svg').attr('viewBox','0 0 '+width+' '+height)
      .attr('width','100%').attr('height',height).attr('preserveAspectRatio','xMinYMin meet')
      .attr('aria-label','Magnitude by time for '+data.objectId);
    var plot = svg.append('g').attr('transform','translate('+margin.left+','+margin.top+')');
    plot.append('g').attr('transform','translate(0,'+innerHeight+')').call(d3.axisBottom(x).ticks(6));
    plot.append('g').call(d3.axisLeft(y).ticks(6));
    svg.append('text').attr('x',margin.left+innerWidth/2).attr('y',height-8)
      .attr('text-anchor','middle').text('Modified Julian Date (MJD)');
    svg.append('text').attr('transform','translate(17,'+(margin.top+innerHeight/2)+') rotate(-90)')
      .attr('text-anchor','middle').text('PSF magnitude (brighter ↑)');
    var tooltip = document.createElement('div');
    tooltip.className = 'lightcurve-tooltip'; tooltip.hidden = true;
    area.appendChild(tooltip);
    function show(event, point) {
      tooltip.textContent = point.band + ' · MJD ' + point.mjd.toFixed(5) + ' · mag ' +
        point.magnitude.toFixed(3) + ' ± ' + point.magnitudeError.toFixed(3) + ' · source ' + point.sourceId;
      var bounds = area.getBoundingClientRect();
      var svgBounds = svg.node().getBoundingClientRect();
      var scale = Math.min(svgBounds.width/width,svgBounds.height/height);
      var px = svgBounds.left-bounds.left + (margin.left + x(point.mjd))*scale;
      var py = svgBounds.top-bounds.top + (margin.top + y(point.magnitude))*scale;
      tooltip.style.left = Math.max(6,Math.min(bounds.width-210,px+10))+'px';
      tooltip.style.top = Math.max(4,py-38)+'px';
      tooltip.hidden = false;
    }
    function hide() { tooltip.hidden = true; }
    data.bands.forEach(function (band) {
      var points = data.points.filter(function (p) { return p.band === band; });
      plot.append('path').datum(points).attr('class','lightcurve-line')
        .attr('stroke',colors[band]).attr('d',d3.line().x(function (p) { return x(p.mjd); })
          .y(function (p) { return y(p.magnitude); }));
      var bars = plot.selectAll('.lightcurve-error-'+band).data(points).enter().append('g');
      bars.append('line').attr('class','lightcurve-error').attr('stroke',colors[band])
        .attr('x1',function (p) { return x(p.mjd); }).attr('x2',function (p) { return x(p.mjd); })
        .attr('y1',function (p) { return y(p.magnitude-p.magnitudeError); })
        .attr('y2',function (p) { return y(p.magnitude+p.magnitudeError); });
      plot.selectAll('.lightcurve-point-'+band).data(points).enter().append('circle')
        .attr('class','lightcurve-point lightcurve-point-'+band).attr('r',3.4)
        .attr('cx',function (p) { return x(p.mjd); }).attr('cy',function (p) { return y(p.magnitude); })
        .attr('fill',colors[band]).attr('tabindex',0)
        .attr('aria-label',function (p) { return p.band+' magnitude '+p.magnitude.toFixed(3)+' at MJD '+p.mjd.toFixed(5); })
        .on('pointerenter',show).on('pointermove',show).on('pointerleave',hide)
        .on('focus',show).on('blur',hide);
    });
  }
  root.lightCurveRender = function (payload, expectedId) { chart(prepare(payload,expectedId)); };
  root.lightCurveLoad = async function (objectId) {
    var current = ++sequence;
    if (controller) controller.abort();
    controller = new AbortController();
    var active = controller;
    try {
      var response = await fetch('../LightCurveData.jsp', {
        method:'POST',credentials:'same-origin',signal:active.signal,
        headers:{'Content-Type':'application/json'},body:JSON.stringify({objectId:objectId})
      });
      if (!response.ok) throw new Error('Observation lookup failed (HTTP '+response.status+').');
      var payload = await response.json();
      if (current !== sequence || active.signal.aborted) return;
      root.lightCurveRender(payload,objectId);
    } catch (error) {
      if (current === sequence && !active.signal.aborted)
        document.getElementById('lightcurve-status').textContent = 'LightCurve: '+error.message;
    } finally { if (controller === active) controller = null; }
  };
  root.addEventListener('pagehide',function () { sequence++; if (controller) controller.abort(); });
})(window);

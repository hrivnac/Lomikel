// A responsive, keyboard-accessible chart for table-derived scatter and evolution data.
function scatterPlotPrepare(data, hasX) {
  if (!Array.isArray(data)) return [];
  return data.reduce(function (points, entry) {
    var xv = hasX ? entry.x : entry.t;
    var yv = entry.y;
    if (xv == null || yv == null || String(xv).trim() === '' || String(yv).trim() === '') return points;
    var x = Number(xv), y = Number(yv);
    if (!Number.isFinite(x) || !Number.isFinite(y)) return points;
    var zv = entry.z;
    var z = zv != null && String(zv).trim() !== '' && Number.isFinite(Number(zv)) ? Number(zv) : null;
    points.push({x: x, y: y, z: z, g: String(entry.g == null ? '' : entry.g),
                 k: String(entry.k == null ? '' : entry.k), raw: entry});
    return points;
  }, []);
}

function scatterPlotActionKey(point, hasX) {
  return encodeURIComponent(point.k + ' ' + (hasX ? point.g : point.x));
}

function scatterPlotEscape(value) {
  return String(value).replace(/[&<>"']/g, function (character) {
    return {'&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;'}[character];
  });
}

function showScatterPlot(data, name, xS, yS, zS, sS, url) {
  var root = d3.select('#scatter_area');
  root.selectAll('*').remove();
  var hasX = Boolean(xS);
  var points = scatterPlotPrepare(data, hasX);
  var title = name || (hasX ? 'Scatter plot' : 'Evolution plot');
  var header = root.append('div').attr('class', 'scatter-header');
  header.append('strong').text(title);
  header.append('span').text(points.length + ' point' + (points.length === 1 ? '' : 's') +
    ' · ' + (hasX ? xS : 'Time') + ' vs ' + (yS || 'Value') +
    (zS ? ' · Size: ' + zS : '') + (sS ? ' · Color: ' + sS : ''));
  if (!points.length) {
    root.append('p').attr('class', 'scatter-empty').attr('role', 'status')
      .text('No valid points for these coordinates. Select numeric x and y properties.');
    return;
  }

  var w = 700, h = 470;
  var margin = {top: 24, right: 28, bottom: 62, left: 76};
  var width = w - margin.left - margin.right;
  var height = h - margin.top - margin.bottom;
  var svg = root.append('svg').attr('class', 'scatterplot')
    .attr('viewBox', '0 0 ' + w + ' ' + h).attr('role', 'img')
    .attr('aria-label', title + ': ' + points.length + ' plotted points');
  var plot = svg.append('g').attr('transform', 'translate(' + margin.left + ',' + margin.top + ')');
  var domainX = d3.extent(points, function (p) { return p.x; });
  var x = hasX ? d3.scaleLinear().domain(domainX).nice().range([0, width]) :
    d3.scaleTime().domain(domainX.map(function (v) { return new Date(v); })).nice().range([0, width]);
  var y = d3.scaleLinear().domain(d3.extent(points, function (p) { return p.y; })).nice().range([height, 0]);
  plot.append('g').attr('class', 'scatter-grid').call(d3.axisLeft(y).tickSize(-width).tickFormat(''));
  var xAxis = hasX ? d3.axisBottom(x).ticks(6) : d3.axisBottom(x).ticks(5).tickFormat(d3.timeFormat('%d %b %y'));
  plot.append('g').attr('class', 'scatter-axis').attr('transform', 'translate(0,' + height + ')').call(xAxis);
  plot.append('g').attr('class', 'scatter-axis').call(d3.axisLeft(y).ticks(6));
  plot.append('text').attr('class', 'scatter-axis-title').attr('x', width / 2)
    .attr('y', height + 48).attr('text-anchor', 'middle').text(hasX ? xS : 'Time');
  plot.append('text').attr('class', 'scatter-axis-title').attr('transform', 'rotate(-90)')
    .attr('x', -height / 2).attr('y', -55).attr('text-anchor', 'middle').text(yS || 'Value');

  var groups = Array.from(new Set(points.map(function (p) { return p.g; }))).filter(Boolean);
  var color = d3.scaleOrdinal().domain(groups).range(d3.schemeTableau10);
  var sizes = points.map(function (p) { return p.z; }).filter(function (v) { return v !== null; });
  var radius = sizes.length && d3.min(sizes) !== d3.max(sizes) ?
    d3.scaleSqrt().domain(d3.extent(sizes)).range([5, 13]) : function () { return sizes.length ? 7 : 6; };
  var tip = root.append('div').attr('class', 'scatter-tip').attr('role', 'status').attr('aria-live', 'polite');
  function info(p) {
    return (p.k ? p.k + ' · ' : '') + (hasX ? xS : 'Time') + ': ' +
      (hasX ? p.x : d3.timeFormat('%d %b %Y %H:%M')(new Date(p.x))) +
      ' · ' + (yS || 'Value') + ': ' + p.y +
      (p.z !== null && zS ? ' · ' + zS + ': ' + p.z : '') +
      (p.g ? ' · ' + (sS || 'Group') + ': ' + p.g : '');
  }
  function showTip(event, p) {
    var position = d3.pointer(event, root.node());
    tip.text(info(p)).style('left', Math.min(position[0] + 12, Math.max(8, root.node().clientWidth - 240)) + 'px')
      .style('top', Math.max(4, position[1] - 42) + 'px').classed('is-visible', true);
  }
  function activate(p) {
    var message = (hasX ? 'Scatter Point: ' : 'Evolution Point: ') + info(p);
    // Both shells have HTML-reading callbacks; keep point data inert in them.
    var safeMessage = scatterPlotEscape(message);
    if (window.parent && window.parent.parent) {
      var host = window.parent.parent;
      if (typeof host.feedback === 'function') host.feedback(safeMessage);
      if (typeof host.commands === 'function' && typeof actions === 'function')
        host.commands(safeMessage, actions(url, scatterPlotActionKey(p, hasX)));
    }
  }
  plot.selectAll('.scatter-point').data(points).enter().append('circle')
    .attr('class', 'scatter-point').attr('cx', function (p) { return x(p.x); })
    .attr('cy', function (p) { return y(p.y); })
    .attr('r', function (p) { return p.z === null ? 6 : radius(p.z); })
    .attr('fill', function (p) { return p.g ? color(p.g) : '#3778ae'; })
    .attr('tabindex', 0).attr('role', 'button')
    .attr('aria-label', info)
    .on('mouseover focus', showTip)
    .on('mousemove', showTip)
    .on('mouseout blur', function () { tip.classed('is-visible', false); })
    .on('click', function (event, p) { activate(p); })
    .on('keydown', function (event, p) {
      if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); activate(p); }
    });
  if (groups.length) {
    var legend = root.append('div').attr('class', 'scatter-legend').attr('aria-label', 'Color groups');
    groups.slice(0, 12).forEach(function (group) {
      var item = legend.append('span').attr('class', 'scatter-legend-item');
      item.append('i').style('background-color', color(group));
      item.append('span').text(group);
    });
    if (groups.length > 12) legend.append('span').text('+' + (groups.length - 12) + ' more groups');
  }
}

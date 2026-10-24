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

function scatterPlotInfoLines(p, hasX, xS, yS, zS, sS) {
  var fields = new Map();
  function add(name, value) {
    if (name && value != null && !fields.has(name)) fields.set(name, String(value));
  }
  var notes = String(p.k || '');
  var matches = Array.from(notes.matchAll(/(?:^|\s)([^\s=]+)=/g));
  matches.forEach(function (match, index) {
    var end = index + 1 < matches.length ? matches[index + 1].index : notes.length;
    add(match[1], notes.slice(match.index + match[0].length, end).trim());
  });
  if (!matches.length && notes.trim()) add('Details', notes.trim());
  var xName = p.raw && p.raw.xField || (xS.trim().includes(' ') ? 'x' : xS.trim());
  var yName = p.raw && p.raw.yField || (yS && !yS.trim().includes(' ') ? yS.trim() : 'y');
  add(hasX ? xName : 'Time',
      hasX ? p.x : d3.timeFormat('%d %b %Y %H:%M')(new Date(p.x)));
  add(yName, p.y);
  if (zS && p.z !== null) add(zS.trim(), p.z);
  if (sS && p.g) add(sS.trim(), p.g);
  return Array.from(fields, function (pair) { return pair[0] + ' - ' + pair[1]; });
}

function scatterPlotSeriesDash(field, fields) {
  var index = fields.indexOf(field);
  return index <= 0 ? '' : ['3 2', '1 2', '5 2 1 2'][(index - 1) % 3];
}
function showScatterPlot(data, name, xS, yS, zS, sS, url) {
  var root = d3.select('#scatter_area');
  root.selectAll('*').remove();
  var hasX = Boolean(xS);
  var points = scatterPlotPrepare(data, hasX);
  var series = hasX ? [] : Array.from(new Set(points.map(function (p) {
    return p.raw.yField || yS;
  })));
  var title = name || (hasX ? 'Scatter plot' : 'Evolution plot');
  var header = root.append('div').attr('class', 'scatter-header');
  header.append('strong').text(title);
  header.append('span').text(points.length + ' point' + (points.length === 1 ? '' : 's') +
    ' · ' + (hasX ? xS : 'Time') + ' vs ' + (yS || 'Value') +
    (zS ? ' · Size: ' + zS : '') + (sS ? ' · Color: ' + sS : ''));
  if (series.length > 1) header.append('span').text('Series: ' + series.map(function (field, index) {
    return field + (index ? ' (dashed)' : ' (solid)');
  }).join(' · '));
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
  function info(p) { return scatterPlotInfoLines(p, hasX, xS, yS, zS, sS).join('\n'); }
  function showTip(event, p) {
    var position = d3.pointer(event, root.node());
    tip.text(info(p)).style('left', Math.min(position[0] + 12, Math.max(8, root.node().clientWidth - 240)) + 'px')
      .style('top', Math.max(4, position[1] - 42) + 'px').classed('is-visible', true);
  }
  function activate(p) {
    var message = (hasX ? 'Scatter Point:\n' : 'Evolution Point:\n') + info(p);
    // Both shells have HTML-reading callbacks; keep point data inert in them.
    var safeMessage = scatterPlotEscape(message);
    if (window.parent && window.parent.parent) {
      var host = window.parent.parent;
      if (typeof host.feedback === 'function') host.feedback(safeMessage);
      if (typeof host.commands === 'function' && typeof actions === 'function')
        host.commands(safeMessage.replace(/\n/g, '<br>'), actions(url, scatterPlotActionKey(p, hasX)));
    }
  }
  plot.selectAll('.scatter-point').data(points).enter().append('circle')
    .attr('class', 'scatter-point').attr('cx', function (p) { return x(p.x); })
    .attr('cy', function (p) { return y(p.y); })
    .attr('r', function (p) { return p.z === null ? 6 : radius(p.z); })
    .attr('fill', function (p) { return p.g ? color(p.g) : '#3778ae'; })
    .attr('stroke', series.length > 1 ? '#1f2937' : 'none')
    .attr('stroke-width', series.length > 1 ? 2 : 0)
    .attr('stroke-dasharray', function (p) { return scatterPlotSeriesDash(p.raw.yField || yS, series); })
    .attr('tabindex', 0).attr('role', 'button')
    .attr('aria-label', info)
    .on('mouseover focus', showTip)
    .on('mousemove', showTip)
    .on('mouseout blur', function () { tip.classed('is-visible', false); })
    .on('click', function (event, p) { activate(p); })
    .on('keydown', function (event, p) {
      if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); activate(p); }
    });
}

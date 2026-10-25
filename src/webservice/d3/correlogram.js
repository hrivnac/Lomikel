// Opt-in correlogram: exact counts in tooltips, legible labels and compressed circle sizes.
function corrPrepare(rows) {
  var names = [], cells = new Map();
  if (!Array.isArray(rows)) return {names: names, cells: cells};
  rows.forEach(function (row) {
    if (!row || row.x == null || row.y == null) return;
    var a = String(row.x), b = String(row.y);
    if (!names.includes(a)) names.push(a);
    if (!names.includes(b)) names.push(b);
    var rawValue = String(row.value == null ? '' : row.value).trim();
    var parts = String(row.info == null ? '' : row.info).split('/');
    var value = Number(rawValue), counts = parts.map(Number);
    var valid = rawValue !== '' && parts.length === 2 && parts.every(function (part) {
      return part.trim() !== '';
    }) && Number.isFinite(value) && value >= 0 &&
      counts.every(function (n) { return Number.isFinite(n) && n >= 0; }) &&
      value <= Math.min(counts[0], counts[1]);
    var union = valid ? counts[0] + counts[1] - value : 0;
    valid = valid && Number.isFinite(union) && union >= 0;
    var percentage = valid && union > 0 ? 100 * (value / union) : 0;
    valid = valid && Number.isFinite(percentage);
    var cell = {value: valid ? value : null, info: String(row.info == null ? '' : row.info),
                first: counts[0], second: counts[1], firstName: a, secondName: b,
                percent: percentage, valid: valid};
    cells.set(a + '\u0000' + b, cell);
    cells.set(b + '\u0000' + a, cell);
  });
  return {names: names, cells: cells};
}

// Log compression lets low but nonzero overlaps remain visible without changing their color values.
function corrRadius(percent) {
  var p = Number(percent);
  return Number.isFinite(p) && p > 0 ? 4 + 18 * Math.log1p(Math.min(p, 100)) / Math.log1p(100) : 0;
}

function corrCompact(value) {
  var n = Number(value);
  if (!Number.isFinite(n)) return '—';
  if (n !== 0 && Math.abs(n) < 0.0001) return n.toExponential(0).replace('e-0', 'e-');
  return new Intl.NumberFormat('en-US', Math.abs(n) >= 10000 ?
    {notation:'compact', maximumSignificantDigits:3} : {maximumFractionDigits:2}).format(n);
}

function corrFontSize(name, cell) {
  return Math.max(5, Math.min(11, (cell - 10) / Math.max(1, String(name).length * 0.6)));
}

function corrTooltipPoint(event, container) {
  if (Number.isFinite(event.clientX) && Number.isFinite(event.clientY)) {
    return d3.pointer(event, container);
  }
  var box = event.target.getBoundingClientRect();
  var root = container.getBoundingClientRect();
  return [box.left - root.left + box.width / 2, box.top - root.top + box.height / 2];
}

function corrTooltipPosition(at, container, tip) {
  return {
    left: Math.max(4, Math.min(at[0] + 12, container.clientWidth - tip.offsetWidth - 4)),
    top: Math.max(4, Math.min(at[1] - 20, container.clientHeight - tip.offsetHeight - 4))
  };
}

function corrEmptyMessage(invalidInput) {
  return invalidInput ? 'Cannot display malformed correlogram data.' : 'No class pairs to display.';
}

var corrVennPopup = null;
function corrGetPopup(open) {
  if (corrVennPopup && !corrVennPopup.closed) return {window: corrVennPopup, fresh: false};
  corrVennPopup = open();
  return {window: corrVennPopup, fresh: true};
}

function showCorrelogram(rows, invalidInput) {
  var root = d3.select('#corr_area');
  root.selectAll('*').remove();
  var prepared = corrPrepare(rows), names = prepared.names, cells = prepared.cells;
  if (!names.length) {
    root.append('p').attr('role','status').text(corrEmptyMessage(invalidInput));
    return;
  }
  var cell = 64, n = names.length, side = n * cell;
  var w = side + 105, h = side + 68;
  root.append('p').attr('class','corr-intro')
    .text('Overlap matrix · circles show shared fraction of union (compressed for legibility); color shows the exact percentage. Select a filled cell for its Venn diagram.');
  var svg = root.append('svg').attr('class','correlogram').attr('viewBox','0 0 ' + w + ' ' + h)
    .attr('role','group').attr('aria-label','Overlap matrix for ' + n + ' classes');
  var plot = svg.append('g').attr('transform','translate(28,30)');
  var tooltip = root.append('div').attr('class','corr-tooltip').attr('role','status')
    .attr('aria-live','polite');
  var color = d3.scaleLinear().domain([0,100]).range(['#dbeafe','#ba3036']);
  var entries = [];
  names.forEach(function (y, row) {
    names.forEach(function (x, col) {
      entries.push({x:x, y:y, row:row, col:col,
        data:cells.get(x + '\u0000' + y) || {value:null, info:'', percent:0, valid:false}});
    });
  });
  function fullText(d) {
    if (d.row === d.col) return d.x;
    var v = d.data;
    return d.y + ' → ' + d.x + '\nIntersection: ' + (v.value == null ? '—' : v.value) +
      '\nSizes: ' + (v.valid ? v.info : 'unavailable') +
      '\nShared fraction: ' + (v.valid ? v.percent.toFixed(2) + '%' : 'unavailable');
  }
  function showTip(event,d) {
    var at = corrTooltipPoint(event,root.node());
    tooltip.text(fullText(d));
    var position = corrTooltipPosition(at, root.node(), tooltip.node());
    tooltip.style('left',position.left+'px').style('top',position.top+'px')
      .classed('is-visible',true);
  }
  function hideTip() { tooltip.classed('is-visible',false); }
  function openVenn(d) {
    if (!d.data.valid) return;
    var opened = corrGetPopup(function () {
      return window.open('../../d3/vennPopup.jsp','correlogram-venn',
        'height=720,width=900,resizable=yes,scrollbars=yes');
    });
    var popup = opened.window;
    if (!popup) return;
    function render() {
      if (popup.closed || !popup.document.getElementById('vennPopup')) return;
      var info = showVenn(d.data.first,d.data.second,d.data.value,
        d.data.firstName.replaceAll('.',' '),d.data.secondName.replaceAll('.',' '),popup);
      popup.document.getElementById('vennPopupTxt').innerHTML = info;
    }
    if (opened.fresh) popup.addEventListener('load',render,{once:true});
    else if (popup.document.getElementById('vennPopup')) render();
    else popup.addEventListener('load',render,{once:true});
    popup.focus();
  }
  var groups = plot.selectAll('.corr-cell').data(entries).enter().append('g')
    .attr('class',function(d){return 'corr-cell ' + (d.row===d.col?'corr-diagonal':d.col>d.row?'corr-upper':'corr-lower');})
    .attr('transform',function(d){return 'translate('+(d.col*cell)+','+(d.row*cell)+')';})
    .attr('tabindex',0).attr('aria-label',fullText)
    .on('mouseover focus',showTip).on('mousemove',showTip)
    .on('mouseout blur',hideTip)
    .on('click',function(event,d){openVenn(d);})
    .on('keydown',function(event,d){if(event.key==='Enter'||event.key===' '){event.preventDefault();openVenn(d);}});
  groups.append('rect').attr('width',cell).attr('height',cell);
  groups.filter(function(d){return d.row===d.col;}).append('text')
    .attr('class','corr-name').attr('x',cell/2).attr('y',cell/2+3)
    .style('font-size',function(d){return corrFontSize(d.x,cell)+'px';})
    .attr('textLength',function(d){return d.x.length*0.6*corrFontSize(d.x,cell)>cell-10?cell-10:null;})
    .attr('lengthAdjust','spacingAndGlyphs').text(function(d){return d.x;});
  groups.filter(function(d){return d.col>d.row;}).append('circle')
    .attr('class','corr-circle').attr('cx',cell/2).attr('cy',cell/2)
    .attr('r',function(d){return corrRadius(d.data.percent);})
    .attr('fill',function(d){return color(d.data.percent);});
  var lower = groups.filter(function(d){return d.col<d.row;});
  lower.append('text').attr('class','corr-number corr-intersection').attr('x',cell/2).attr('y',cell/2-5)
    .text(function(d){return d.data.value==null?'—':corrCompact(d.data.value);});
  lower.append('text').attr('class','corr-number corr-sizes').attr('x',cell/2).attr('y',cell/2+11)
    .text(function(d){return d.data.valid?d.data.info.split('/').map(corrCompact).join('/'):'—';})
    .attr('textLength',function(d){var text=d.data.valid?d.data.info.split('/').map(corrCompact).join('/'):'';
      return text.length*5.5>cell-9?cell-9:null;}).attr('lengthAdjust','spacingAndGlyphs');
  var legend = svg.append('g').attr('class','corr-scale').attr('transform','translate('+(side+46)+',30)');
  var gradient = svg.append('defs').append('linearGradient').attr('id','corr-gradient').attr('x1','0').attr('y1','1').attr('x2','0').attr('y2','0');
  gradient.append('stop').attr('offset','0%').attr('stop-color',color(0));
  gradient.append('stop').attr('offset','100%').attr('stop-color',color(100));
  legend.append('rect').attr('width',13).attr('height',side).attr('fill','url(#corr-gradient)');
  [0,25,50,75,100].forEach(function(p){legend.append('text').attr('x',19).attr('y',side*(1-p/100)+4).text(p+'%');});
}

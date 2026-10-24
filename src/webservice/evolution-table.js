/* Build graph-table evolution points from the selected properties, not legacy JSON. */
(function () {
  'use strict';
  function names(role) {
    var selected = document.getElementsByClassName(role);
    return Array.from(new Set(Array.from(selected).filter(function (input) { return input.checked; })
      .map(function (input) { return input.getAttribute('data-plot-field'); }).filter(Boolean)));
  }
  function milliseconds(value) {
    var text = String(window.plotTableScalar(value)).trim();
    if (!text) return null;
    if (/^\d{13}$/.test(text)) return Number(text);
    if (/^\d{10}$/.test(text)) return Number(text) * 1000;
    // Graph imports commonly use an ISO date or a space-separated UTC date.
    if (!/^\d{4}[- ]\d{2}[- ]\d{2}/.test(text)) return null;
    var iso = text.replace(/^(\d{4}) (\d{2}) (\d{2})/, '$1-$2-$3')
      .replace(/^(\d{4}-\d{2}-\d{2})\s+/, '$1T');
    if (!/(?:Z|[+-]\d{2}:?\d{2})$/i.test(iso)) iso += 'Z';
    var time = Date.parse(iso);
    return Number.isFinite(time) ? time : null;
  }
  window.wsTableEvolutionURL = function (url, rows) {
    if (!Array.isArray(rows) || !rows.length || !rows.some(function (row) {
      return row && Object.hasOwn(row, 'time');
    })) return null;
    var query = new URL(url, document.baseURI).searchParams;
    var fields = Array.from(new Set(names('x').concat(names('y')))).filter(function (field) {
      return field !== 'time';
    });
    var z = names('z')[0] || '', s = names('s')[0] || '', k = names('k');
    var points = [];
    rows.forEach(function (row) {
      if (!row || row.time == null) return;
      var t = milliseconds(row.time);
      if (t == null || t === '') return;
      fields.forEach(function (field) {
        if (row[field] == null || row[field] === '') return;
        var value = window.plotTableScalar(row[field]);
        var group = s && row[s] != null && row[s] !== '' ? window.plotTableScalar(row[s]) : field;
        var point = {t: t, y: value, yField: field, g: group};
        if (z && row[z] != null && row[z] !== '') point.z = window.plotTableScalar(row[z]);
        var notes = k.filter(function (name) { return row[name] != null && row[name] !== ''; })
          .map(function (name) { return name + '=' + window.plotTableScalar(row[name]); }).join(' ');
        if (notes) point.k = notes;
        points.push(point);
      });
    });
    var output = new URLSearchParams();
    [['name', query.get('name') || 'Evolution plot'], ['url', query.get('url') || ''],
     ['x', ''], ['y', fields.join(' ')], ['z', z], ['s', s],
     ['timeUnit', 'ms'], ['tdata', JSON.stringify(points)]].forEach(function (pair) {
      output.set(pair[0], pair[1]);
    });
    return 'webservice/d3/scatterplot.jsp?' + output.toString();
  };
})();

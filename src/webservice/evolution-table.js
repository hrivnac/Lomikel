/* Build graph-table evolution points from the selected properties, not legacy JSON. */
(function () {
  'use strict';
  function names(role) {
    var selected = document.getElementsByClassName(role);
    return Array.from(new Set(Array.from(selected).filter(function (input) { return input.checked; })
      .map(function (input) { return input.getAttribute('data-plot-field'); }).filter(Boolean)));
  }
  // Parse a table value as UTC epoch milliseconds, or null. Accepts epoch ms/s
  // (only when `dateOnly` is false) and dates such as '2026-03-17 15:43:10.456',
  // '2026-03-17T15:43:10Z', '2026/03/17 15:43', '2026-03-17' with optional
  // fraction, 'Z'/'UTC'/'GMT' or a +hh:mm offset. Dates without a zone are UTC.
  var DATE = /^(\d{4})([-\/])(\d{1,2})\2(\d{1,2})(?:[T ](\d{1,2}):(\d{2})(?::(\d{2})(?:[.,](\d+))?)?)?\s*(Z|UTC|GMT|[+-]\d{2}:?\d{2})?$/i;
  function milliseconds(value, dateOnly) {
    var scalar = window.plotTableScalar(value);
    var text = String(scalar == null ? '' : scalar).trim();
    if (!text) return null;
    if (!dateOnly && /^\d{13}$/.test(text)) return Number(text);
    if (!dateOnly && /^\d{10}$/.test(text)) return Number(text) * 1000;
    var m = DATE.exec(text);
    if (!m) return null;
    var f = function (i) { return m[i] === undefined ? 0 : Number(m[i]); };
    var year = f(1), month = f(3), day = f(4), hour = f(5), minute = f(6), second = f(7);
    var milli = m[8] === undefined ? 0 : Number((m[8] + '00').slice(0, 3));
    var time = Date.UTC(year, month - 1, day, hour, minute, second, milli);
    var check = new Date(time);
    // Reject impossible dates rather than letting Date roll them over (2026-02-30).
    if (check.getUTCFullYear() !== year || check.getUTCMonth() !== month - 1 || check.getUTCDate() !== day ||
        hour > 23 || minute > 59 || second > 59) return null;
    var zone = m[9] ? m[9].toUpperCase() : 'Z';
    if (zone !== 'Z' && zone !== 'UTC' && zone !== 'GMT') {
      var sign = zone[0] === '-' ? -1 : 1, digits = zone.slice(1).replace(':', '');
      var zh = Number(digits.slice(0, 2)), zm = Number(digits.slice(2));
      if (zh > 14 || zm > 59) return null;
      time -= sign * (zh * 60 + zm) * 60000;
    }
    return Number.isFinite(time) ? time : null;
  }
  window.wsEvolutionTime = milliseconds;
  function present(value) {
    return value != null && String(window.plotTableScalar(value)).trim() !== '';
  }
  // The time axis: `time` when present. Tables with the legacy `jd` keep the JSP
  // conversion route. Otherwise a column whose every non-empty value is a date:
  // the one filled in most rows, date/time-like names (importDate...) on ties.
  function timeField(rows) {
    var has = function (name) { return rows.some(function (row) { return row && Object.hasOwn(row, name); }); };
    if (has('time')) return 'time';
    if (has('jd')) return null;
    var names = [];
    rows.forEach(function (row) {
      if (row && typeof row === 'object') Object.keys(row).forEach(function (name) {
        if (!names.includes(name)) names.push(name);
      });
    });
    var best = null, bestCount = 0, bestNamed = false;
    names.forEach(function (name) {
      var values = rows.filter(function (row) { return row && present(row[name]); });
      if (!values.length || !values.every(function (row) { return milliseconds(row[name], true) !== null; })) return;
      var named = /date|time|stamp|epoch/i.test(name);
      if (values.length > bestCount || (values.length === bestCount && named && !bestNamed)) {
        best = name; bestCount = values.length; bestNamed = named;
      }
    });
    return best;
  }
  window.wsTableEvolutionURL = function (url, rows) {
    if (!Array.isArray(rows) || !rows.length) return null;
    var tField = timeField(rows);
    if (!tField) return null;
    var dateOnly = tField !== 'time';
    var query = new URL(url, document.baseURI).searchParams;
    var fields = Array.from(new Set(names('x').concat(names('y')))).filter(function (field) {
      return field !== tField;
    });
    var z = names('z')[0] || '', s = names('s')[0] || '', k = names('k');
    var points = [];
    rows.forEach(function (row) {
      if (!row || row[tField] == null) return;
      var t = milliseconds(row[tField], dateOnly);
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
    var title = query.get('name') || 'Evolution plot';
    if (tField !== 'time') title += ' \u00b7 time: ' + tField;
    var output = new URLSearchParams();
    [['name', title], ['url', query.get('url') || ''],
     ['x', ''], ['y', fields.join(' ')], ['z', z], ['s', s],
     ['timeUnit', 'ms'], ['tdata', JSON.stringify(points)]].forEach(function (pair) {
      output.set(pair[0], pair[1]);
    });
    return 'd3/scatterplot.jsp?' + output.toString();
  };
})();

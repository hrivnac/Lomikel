// Shared expanded-row controls for the graph vertex and edge tables.
function plotTableEscape(value) {
  return String(value == null ? '' : value).replace(/[&<>"']/g, function (character) {
    return {'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[character];
  });
}

function plotTableFieldName(input) {
  return input.getAttribute('data-plot-field');
}

function plotTableHasField(selection, field) {
  return selection.trim().split(/\s+/).includes(field);
}

// Graph valueMap() renders single-value properties as [value].
function plotTableScalar(value) {
  if (typeof value === 'string' && /^\[[^\[\],;]*\]$/.test(value.trim())) {
    return value.trim().slice(1, -1);
  }
  return value;
}

function plotTableScatterURL(name, x, y, z, s, k, rows) {
  var points = [];
  var xFields = x.trim().split(/\s+/);
  var yFields = y.trim().split(/\s+/);
  var kFields = k.trim() ? k.trim().split(/\s+/) : [];
  rows.forEach(function (row) {
    xFields.forEach(function (xx) {
      yFields.forEach(function (yy) {
        if (row[xx] == null || row[yy] == null || row[xx] === '' || row[yy] === '') return;
        var point = {x: plotTableScalar(row[xx]), y: plotTableScalar(row[yy]),
                     g: s && row[s] != null && row[s] !== '' ? plotTableScalar(row[s]) : xx + '/' + yy};
        if (z && row[z] != null && row[z] !== '') point.z = plotTableScalar(row[z]);
        var info = kFields.filter(function (field) {
          return row[field] != null && row[field] !== '';
        }).map(function (field) { return field + '=' + plotTableScalar(row[field]); }).join(' ');
        if (info) point.k = info;
        points.push(point);
      });
    });
  });
  return 'd3/scatterplot.jsp?' + [
    ['name', name], ['url', ''], ['x', x], ['y', y], ['z', z], ['s', s],
    ['tdata', JSON.stringify(points)]
  ].map(function (pair) {
    return encodeURIComponent(pair[0]) + '=' + encodeURIComponent(pair[1]);
  }).join('&');
}

function plotTableDetailFormatter(index, row) {
  var roles = [
    ['x', 'Horizontal axis'], ['y', 'Vertical axis'], ['z', 'Symbol size'],
    ['s', 'Color group'], ['k', 'Point details']
  ];
  return '<div class="plot-properties" aria-label="Plot properties">' +
    Object.keys(row).map(function (key) {
      var field = plotTableEscape(key);
      var controls = roles.map(function (role) {
        var id = role[0] + '1_' + index + '_' + encodeURIComponent(key);
        return '<input type="checkbox" class="' + role[0] + '" name="' + plotTableEscape(id) + '" id="' + plotTableEscape(id) +
          '" data-plot-field="' + field + '" aria-label="' + role[1] + ' for ' + field + '">' +
          '<label for="' + plotTableEscape(id) + '" title="' + role[1] + '">' + role[0] + '</label>';
      }).join('');
      return '<div class="plot-property"><div class="plot-roles" role="group" aria-label="Plot roles for ' + field + '">' +
        controls + '</div><span class="plot-field-name">' + field + '</span><span class="plot-field-value">' +
        plotTableEscape(row[key]) + '</span></div>';
    }).join('') + '</div>';
}

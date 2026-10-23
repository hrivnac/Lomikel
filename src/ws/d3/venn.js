// Render a two-set overlap diagram and return its accessible statistics panel.
function showVenn(n1, n2, n12, m1, m2, vennPopupWindow) {
  var a = Number(n1);
  var b = Number(n2);
  var shared = Number(n12);
  var union = a + b - shared;
  var valid = Number.isFinite(a) && Number.isFinite(b) && Number.isFinite(shared) &&
              Number.isFinite(union) && a >= 0 && b >= 0 && shared >= 0 &&
              shared <= Math.min(a, b) && union >= 0;
  var target = vennPopupWindow ? vennPopupWindow.document.getElementById('vennPopup') : '#venn';
  var selection = d3.select(target);
  if (!valid) {
    selection.classed('venn-contained', false).selectAll('svg').remove();
    return '<p class="venn-empty" role="status">Overlap counts are unavailable.</p>';
    }

  var contained = shared > 0 && shared === Math.min(a, b);
  selection.classed('venn-contained', contained);
  var relationship = contained ?
    '<p class="venn-relationship">' + (a === b ? 'A and B contain the same objects.' :
      a < b ? 'A is contained in B.' : 'B is contained in A.') + '</p>' : '';
  var nameA = m1 || 'A';
  var nameB = m2 || 'B';
  var countFormatter = new Intl.NumberFormat('en-US', {maximumFractionDigits: 6});
  function formatCount(value) {
    return value !== 0 && Math.abs(value) < 0.000001 ? value.toExponential(2) :
           countFormatter.format(value);
    }
  function percent(value) {
    return union > 0 ? (100 * (value / union)).toFixed(1) + '%' : '—';
    }
  function escapeHTML(value) {
    return String(value).replace(/[&<>"']/g, function (character) {
      return {'&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;'}[character];
      });
    }
  function row(label, count) {
    return '<tr><th scope="row">' + label + '</th><td>' + formatCount(count) +
           '</td><td>' + percent(count) + '</td></tr>';
    }

  var info = '<div class="venn-summary">' +
    '<div class="venn-eyebrow">Overlap summary</div>' +
    '<div class="venn-legend" aria-label="Sets">' +
    '<div class="venn-legend-item"><span class="venn-swatch venn-swatch-a">A</span><span>' + escapeHTML(nameA) + '</span></div>' +
    '<div class="venn-legend-item"><span class="venn-swatch venn-swatch-b">B</span><span>' + escapeHTML(nameB) + '</span></div>' +
    '</div>' + relationship + '<table class="venn-stats"><caption>Set sizes and share of union</caption>' +
    '<thead><tr><th scope="col">Region</th><th scope="col">Objects</th><th scope="col">Share</th></tr></thead><tbody>' +
    row('A', a) + row('B', b) + row('A ∩ B', shared) +
    row('A only', a - shared) + row('B only', b - shared) + row('A ∪ B', union) +
    '</tbody></table></div>';

  // The long classifier names live in the legend; one-letter circle labels remain legible.
  var sets = [{sets:['A'], size:a}, {sets:['B'], size:b},
              {sets:['A','B'], size:shared}];
  // A vertical layout grants each set its own label space in a narrow Plot pane.
  var chart = venn.VennDiagram().orientation(0).width(340).height(430)
                                .padding(20).fontSize('16px');
  selection.datum(sets).call(chart);
  selection.select('svg').attr('viewBox', '0 0 340 430')
                         .attr('role', 'img')
                         .attr('aria-label', 'Venn diagram of A and B');
  return info;
  }

'use strict';
// Scatter and Evolution plots must fill the ANALYSIS/Plot panel instead of a fixed
// 480/720 px iframe that shows only part of the chart and needs vertical scrolling.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../../webservice');
const read = name => fs.readFileSync(path.join(root, name), 'utf8');

// Routing: every iframe Plot request for d3/scatterplot.jsp gets the fill class and 100% height.
const classes = new Set();
const pane = {classList: {add: n => classes.add(n), remove: n => classes.delete(n), contains: n => classes.has(n)}};
const calls = [];
const window = {loadPane: (...args) => { calls.push(args); return 'loaded'; }, lightcurveGraphState: {cancel() {}},
  plotTableScalar: v => Array.isArray(v) ? v[0] : v};
const document = {baseURI: 'http://localhost/FinkBrowser/', getElementById: id => id === 'plot' ? pane : null};
vm.runInNewContext(read('workspace.js'), {window, document, $() {}, URL, URLSearchParams});
const data = encodeURIComponent(JSON.stringify([{t: 1767225600000, y: 18.1, yField: 'mag'}]));
for (const [legacyHeight, encoded] of [[480, true], [720, true], [480, false]]) {
  classes.clear();
  window.loadPane('plot', 'd3/scatterplot.jsp?name=e&x=&y=mag&timeUnit=ms&tdata=' + data, true, legacyHeight, encoded);
  assert.equal(classes.has('ws-scatterplot'), true, 'Scatter/Evolution fills the Plot pane');
  assert.match(calls.at(-1)[1], /^d3\/scatterplot\.jsp\?/);
  assert.equal(calls.at(-1)[3], '100%', `do not keep the legacy fixed ${legacyHeight}px iframe`);
}
window.loadPane('plot', 'Plot.jsp');
assert.equal(classes.has('ws-scatterplot'), false, 'Reset clears the scatter-only sizing');
window.loadPane('plot', 'd3/scatterplot.jsp?name=e', true, 480, true);
window.loadPane('plot', 'd3/correlogram.jsp?tdata=' + encodeURIComponent('[]'), true, 480, true);
assert.equal(classes.has('ws-scatterplot'), false, 'other Plot routes drop the scatter class');
window.loadPane('result', 'd3/scatterplot.jsp?name=e', true, 480, true);
assert.equal(calls.at(-1)[3], 480, 'non-Plot panes keep their height');

// Host and frame CSS.
const host = read('graph.css');
assert.match(host, /body\.workspace #plot\.ws-scatterplot\s*\{[^}]*height:\s*100%/);
assert.match(host, /body\.workspace #plot\.ws-scatterplot iframe\[name="dynamicFrame"\]\s*\{[^}]*height:\s*100%/);
const css = read('d3/scatterplot.css');
assert.match(css, /html\.scatter-fill,\s*html\.scatter-fill body\s*\{[^}]*height:\s*100%/);
assert.match(css, /html\.scatter-fill #scatter_area\s*\{[^}]*height:\s*100%[^}]*display:\s*flex/s);
assert.match(css, /html\.scatter-fill \.scatterplot\s*\{[^}]*flex:\s*1/);

// Renderer: chart size follows the available area inside the frame, with a readable minimum.
const ctx = {};
vm.runInNewContext(read('d3/scatterplot.js'), ctx);
assert.deepEqual({...ctx.scatterPlotSize(false, 900, 600)}, {w: 700, h: 470}, 'non-filling pages keep 700x470');
assert.deepEqual({...ctx.scatterPlotSize(true, 640.6, 560.2)}, {w: 641, h: 560}, 'filling frame uses the panel area');
assert.deepEqual({...ctx.scatterPlotSize(true, 100, 50)}, {w: 360, h: 260}, 'tiny panes keep a readable minimum');
assert.deepEqual({...ctx.scatterPlotSize(true, 0, NaN)}, {w: 700, h: 470}, 'unmeasurable areas fall back');

// Tooltip stays inside the clipped plot area, including at the bottom edge.
const bottom = ctx.scatterPlotTipPosition([600, 410], {clientWidth: 640, clientHeight: 420}, {offsetWidth: 200, offsetHeight: 90});
assert.equal(bottom.left, 436, 'tooltip stays inside at right');
assert.equal(bottom.top, 326, 'tooltip stays inside at bottom');
const corner = ctx.scatterPlotTipPosition([0, 0], {clientWidth: 640, clientHeight: 420}, {offsetWidth: 200, offsetHeight: 90});
assert.deepEqual({...corner}, {left: 12, top: 4});

// Cache-busting of every changed asset.
assert.match(read('index.jsp'), /workspace\.js\?v=canonical-3/);
assert.match(read('index.jsp'), /graph\.css\?v=query-activity-2/);
assert.match(read('d3/scatterplot.jsp'), /scatterplot\.js\?v=fill-1/);
assert.match(read('d3/scatterplot.jsp'), /scatterplot\.css\?v=fill-1/);
console.log('Scatter and Evolution plots fill the Plot panel');

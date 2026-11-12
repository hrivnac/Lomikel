'use strict';
// Evolution Plot: recognise a time column other than `time` (e.g. object `importDate`
// '2026-03-17 15:43:10.456') and common date-time formats.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const base = path.resolve(__dirname, '../../webservice');
const calls = [];
const checked = {x: [], y: ['mag'], z: [], s: [], k: []};
const scalar = v => typeof v === 'string' && /^\[[^\[\],;]*\]$/.test(v.trim()) ? v.trim().slice(1, -1) : v;
const win = {loadPane: (...args) => calls.push(args), tdata: [], plotTableScalar: scalar,
  addEventListener() {}, setTimeout() {}, innerWidth: 800, innerHeight: 600};
const doc = {baseURI: 'http://local/FinkBrowser/', getElementById: id => id === 'tbl' ? {} : null,
  getElementsByClassName: role => (checked[role] || []).map(field => ({checked: true, getAttribute: () => field}))};
const ctx = {window: win, document: doc, URL, URLSearchParams, $: () => {}, Date};
vm.runInNewContext(fs.readFileSync(path.join(base, 'evolution-table.js'), 'utf8'), ctx);
vm.runInNewContext(fs.readFileSync(path.join(base, 'workspace.js'), 'utf8'), ctx);
function plot(rows) {
  calls.length = 0;
  win.tdata = rows;
  win.loadPane('plot', 'd3/scatterplot.jsp?name=Objects&url=&x=&y=mag&tdata=[]', true, 480, false);
  return new URL(calls.at(-1)[1], doc.baseURI).searchParams;
}
const utc = (...a) => Date.UTC(...a);

// Parser: formats seen in graph imports.
const ms = win.wsEvolutionTime;
assert.equal(typeof ms, 'function');
for (const [text, expected] of [
  ['2026-03-17 15:43:10.456', utc(2026, 2, 17, 15, 43, 10, 456)],
  ['[2026-03-17 15:43:10.456]', utc(2026, 2, 17, 15, 43, 10, 456)],
  ['2026-03-17T15:43:10Z', utc(2026, 2, 17, 15, 43, 10)],
  ['2026-03-17T15:43:10.123456', utc(2026, 2, 17, 15, 43, 10, 123)],
  ['2026-03-17 15:43', utc(2026, 2, 17, 15, 43)],
  ['2026/03/17 15:43:10', utc(2026, 2, 17, 15, 43, 10)],
  ['2026-03-17', utc(2026, 2, 17)],
  ['2026-03-17 15:43:10 UTC', utc(2026, 2, 17, 15, 43, 10)],
  ['2026-03-17T16:43:10+01:00', utc(2026, 2, 17, 15, 43, 10)],
  ['2026-03-17T10:43:10-0500', utc(2026, 2, 17, 15, 43, 10)],
  ['1773762190456', 1773762190456],
  ['1773762190', 1773762190000]]) {
  assert.equal(ms(text), expected, text);
}
for (const bad of ['', 'ZTF21aaxtctv', '2026-13-01', '2026-02-30 10:00', '2026-03-17 25:00', '17/03/2026', '42', '18.5', '2026 10 24', '2026-03/17', '2026-03-17T10:00+99:99']) {
  assert.equal(ms(bad), null, `not a time: ${bad}`);
}
assert.equal(ms('1773762190', true), null, 'other columns accept dates, not bare epoch numbers');
assert.equal(ms('2026-03-17 15:43:10.456', true), utc(2026, 2, 17, 15, 43, 10, 456));

// Object vertices: importDate is the time axis, not a plotted series.
const objects = [
  {lbl: '[object]', objectId: '[ZTF26aaaaaaa]', importDate: '[2026-03-17 15:43:10.456]', mag: '[18.2]', ndet: '[1773762190]'},
  {lbl: '[object]', objectId: '[ZTF26aaaaaab]', importDate: '[2026-03-18 09:01:02.003]', mag: '[18.4]', ndet: '[1773762191]'}];
let query = plot(objects);
let points = JSON.parse(query.get('tdata'));
assert.deepEqual(points.map(p => p.t), [utc(2026, 2, 17, 15, 43, 10, 456), utc(2026, 2, 18, 9, 1, 2, 3)]);
assert.deepEqual(points.map(p => p.y), ['18.2', '18.4']);
assert.equal(query.get('timeUnit'), 'ms');
assert.equal(query.get('name'), 'Objects · time: importDate', 'the plot names the chosen time column');
checked.y = ['mag', 'importDate'];
points = JSON.parse(plot(objects).get('tdata'));
assert.deepEqual(points.map(p => p.yField), ['mag', 'mag'], 'the time column is never a series');
checked.y = ['mag'];

// `time` keeps priority; a date-like name is preferred over another date column.
query = plot([{time: '2026-01-01T00:00:00Z', importDate: '2026-03-17 15:43:10', mag: '1'}]);
assert.equal(JSON.parse(query.get('tdata'))[0].t, utc(2026, 0, 1));
assert.equal(query.get('name'), 'Objects', 'unchanged name for the classic time column');
query = plot([{note: '2025-12-31', importDate: '2026-03-17 15:43:10', mag: '1'}]);
assert.equal(JSON.parse(query.get('tdata'))[0].t, utc(2026, 2, 17, 15, 43, 10));
query = plot([{obs: '2026-02-01 10:00:00', mag: '1'}, {obs: '2026/02/02 10:00:00', mag: '2'}]);
assert.equal(query.get('name'), 'Objects · time: obs', 'any column of valid dates qualifies');
// A column is a time axis only if every non-empty value is a date; rows without one are skipped.
query = plot([{importDate: '2026-03-17 15:43:10', mag: '1'}, {importDate: '', mag: '2'}]);
assert.deepEqual(JSON.parse(query.get('tdata')).map(p => p.y), ['1']);
calls.length = 0;
win.tdata = [{importDate: '2026-03-17 15:43:10', mag: '1'}, {importDate: 'unknown', mag: '2'}];
win.loadPane('plot', 'd3/scatterplot.jsp?x=&y=mag&tdata=[]', true, 480, false);
assert.equal(calls[0][1], 'd3/scatterplot.jsp?x=&y=mag&tdata=[]', 'no reliable time column keeps the old route');
// The most complete date column wins; a date-like name breaks ties; jd tables stay legacy.
query = plot([{tagDate: '2026-01-05 10:00:00', mag: '1'}, {importDate: '2026-03-17 15:43:10', mag: '2'},
  {importDate: '2026-03-18 15:43:10', mag: '3'}]);
assert.equal(query.get('name'), 'Objects · time: importDate', 'sparse tagDate does not drop rows');
assert.deepEqual(JSON.parse(query.get('tdata')).map(p => p.y), ['2', '3']);
query = plot([{code: '2026-01-05', importDate: '2026-03-17 15:43:10', mag: '1'}]);
assert.equal(query.get('name'), 'Objects · time: importDate', 'date-like name wins a tie');
calls.length = 0;
win.tdata = [{jd: '[2460973.5]', importDate: '[2026-03-17 15:43:10.456]', mag: '[5]'}];
win.loadPane('plot', 'd3/scatterplot.jsp?x=&y=mag&tdata=[]', true, 480, false);
assert.equal(calls[0][1], 'd3/scatterplot.jsp?x=&y=mag&tdata=[]', 'jd plus a date column keeps the jd route');
// Legacy jd tables keep their existing JSP conversion.
calls.length = 0;
win.tdata = [{jd: '[2460973.0]', mag: '[5]'}];
win.loadPane('plot', 'd3/scatterplot.jsp?x=&y=mag&tdata=[]', true, 480, false);
assert.equal(calls[0][1], 'd3/scatterplot.jsp?x=&y=mag&tdata=[]');

// The plot shows the stored clock time (zone-less values as UTC), independent of the browser's time zone.
const d3ctx = {};
d3ctx.window = d3ctx;
vm.runInNewContext(fs.readFileSync(path.resolve(base, '../../extlib/webservice/d3-v7.9.2/d3.min.js'), 'utf8'), d3ctx);
vm.runInNewContext(fs.readFileSync(path.join(base, 'd3/scatterplot.js'), 'utf8'), d3ctx);
const line = d3ctx.scatterPlotInfoLines({x: utc(2026, 2, 17, 15, 43, 10, 456), y: 18.2, z: null, g: '', k: '', raw: {}},
  false, '', 'mag', '', '')[0];
assert.equal(line, 'Time - 17 Mar 2026 15:43:10');
const renderer = fs.readFileSync(path.join(base, 'd3/scatterplot.js'), 'utf8');
assert.match(renderer, /d3\.scaleUtc\(\)/, 'time axis uses UTC');
assert.doesNotMatch(renderer, /d3\.scaleTime\(|d3\.timeFormat\(/, 'no local-time formatting');
assert.match(fs.readFileSync(path.join(base, 'd3/scatterplot.jsp'), 'utf8'), /scatterplot\.js\?v=utc-1/);
assert.match(fs.readFileSync(path.join(base, 'index.jsp'), 'utf8'), /evolution-table\.js\?v=evolution-2/);
console.log('Evolution Plot recognises importDate and other date-time columns and formats');

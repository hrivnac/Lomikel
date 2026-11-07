'use strict';
// Ready/Working box for the remaining long actions: double-click expansion,
// node/edge selection (stylesheet lookups), Show, Describe, SkyView, Cutouts,
// LightCurve, Tag and the table plots.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../../webservice');
const read = name => fs.readFileSync(path.join(root, name), 'utf8');
const tick = (ms = 0) => new Promise(resolve => setTimeout(resolve, ms));

function makeFrame() {
  const listeners = [];
  return {
    tagName: 'IFRAME', isConnected: true,
    addEventListener(event, fn) { if (event === 'load') listeners.push(fn); },
    removeEventListener(event, fn) { const i = listeners.indexOf(fn); if (i >= 0) listeners.splice(i, 1); },
    fireLoad() { listeners.slice().forEach(fn => fn({})); }
  };
}

function context() {
  const classes = new Set(['ws-busy-ready']);
  const box = {textContent: 'Ready', attrs: {},
    classList: {toggle(name, on) { if (on) classes.add(name); else classes.delete(name); }},
    setAttribute(name, value) { this.attrs[name] = value; }};
  const panes = {};
  const pane = id => panes[id] || (panes[id] = {frame: null, querySelector(sel) {
    return sel === 'iframe' && this.frame && this.frame.isConnected ? this.frame : null; }});
  const handlers = new Set();
  const $ = () => ({on: (e, fn) => handlers.add(fn), off: (e, fn) => handlers.delete(fn)});
  $.complete = url => Array.from(handlers).forEach(fn => fn({}, {}, {url}));
  const ctx = {console, setTimeout, clearTimeout, setInterval, clearInterval, Promise, encodeURI,
    jQuery: $, requestAnimationFrame: fn => setTimeout(fn, 1),
    document: {getElementById: id => id === 'busy-indicator' ? box : pane(id)}};
  ctx.window = ctx;
  vm.createContext(ctx);
  return {ctx, box, pane, working: () => box.textContent === 'Working' && classes.has('ws-busy-working')};
}

function load(t) { vm.runInContext(read('busy.js'), t.ctx); }

async function testDoubleClickAndSelectionAreTrackedButNestedCallsStaySynchronous() {
  const t = context();
  const order = [];
  t.ctx.expand = id => { order.push(['expand', id, t.working()]); };
  t.ctx.expandEdge = id => { order.push(['expandEdge', id, t.working()]); };
  t.ctx.showGraphCommands = item => { order.push(['commands', item.id, t.working()]); };
  t.ctx.describeNode = id => { order.push(['describeNode', id, t.working()]); };
  t.ctx.describeEdge = id => { order.push(['describeEdge', id, t.working()]); };
  // expandNodes() inside a Search calls expand() in a loop and reads the graph after each call.
  t.ctx.bootstrap = async function () { window.expand(1); window.expand(2); order.push(['after-expands', order.length]); };
  const window = t.ctx;
  load(t);
  for (const [name, arg] of [['expand', 7], ['expandEdge', 'e1'], ['showGraphCommands', {id: 8}],
                             ['describeNode', 9], ['describeEdge', 'e2']]) {
    const run = t.ctx[name](arg);
    assert.ok(t.working(), name + ' turns the box red at once');
    assert.equal(order.length, 0, name + ' waits for a paint before its synchronous Gremlin calls');
    await run;
    assert.equal(t.working(), false, name + ' returns to Ready');
    order.length = 0;
  }
  await t.ctx.bootstrap('text');
  assert.deepEqual(order, [['expand', 1, true], ['expand', 2, true], ['after-expands', 2]],
    'expansions inside a running operation stay synchronous and in order');
}

async function testShowLoadsAreTrackedUntilTheirOwnRequestCompletes() {
  const t = context();
  t.ctx.loadPane = async function () {};
  load(t);
  for (const url of ['Node.jsp?id=12', 'Edge.jsp?id=a1']) {
    await t.ctx.loadPane('result', url);
    assert.ok(t.working(), url + ' keeps Working while it loads');
    t.ctx.jQuery.complete(url);
    await tick(5);
    assert.equal(t.working(), false, url + ' ends at ajaxComplete');
  }
  await t.ctx.loadPane('result', 'Result.jsp');
  assert.equal(t.working(), false, 'other result pages are not tracked');
}

async function testIframePlotsWaitForTheirFrame() {
  const t = context();
  let frame;
  t.ctx.loadPane = async function (pane) { frame = makeFrame(); t.pane(pane).frame = frame; };
  load(t);
  await t.ctx.loadPane('plot', 'd3/scatterplot.jsp?tdata=%5B%5D', true, '100%', true);
  assert.ok(t.working(), 'a plot iframe is still loading');
  frame.fireLoad();
  await tick();
  assert.equal(t.working(), false, 'the plot loaded');

  await t.ctx.loadPane('plot', 'd3/correlogram.jsp?tdata=%5B%5D', true, '100%', true);
  assert.ok(t.working());
  frame.isConnected = false;  // replaced by another view before it loaded
  await tick(700);
  assert.equal(t.working(), false, 'a removed frame cannot keep the box red');
}

async function testGraphViewsAreTracked() {
  const t = context();
  let skyFrame, curveFrame, finishCutouts;
  const calls = [];
  t.ctx.skyGraphState = {
    open: async () => { calls.push('sky'); skyFrame = makeFrame(); t.pane('skyview').frame = skyFrame; },
    // Like showStatus(): an error replaces the pane content with text.
    openIds: async ids => { calls.push(['skyIds', ids]); t.pane('skyview').frame = null; }
  };
  t.ctx.lightcurveGraphState = {open: () => { calls.push('curve'); curveFrame = makeFrame(); t.pane('plot').frame = curveFrame; },
                                cancel() {}, resize() {}};
  t.ctx.cutoutsGraphState = {open: () => new Promise(resolve => { calls.push('cutouts'); finishCutouts = resolve; }),
                             cancel() {}, clear() {}};
  load(t);
  const sky = t.ctx.skyGraphState.open();
  assert.ok(t.working());
  assert.deepEqual(calls, [], 'SkyView starts after the paint');
  await sky;
  assert.ok(t.working(), 'the sky map frame is still loading');
  skyFrame.fireLoad();
  await tick();
  assert.equal(t.working(), false);
  await t.ctx.skyGraphState.openIds(['ZTF1']);
  assert.equal(t.working(), false, 'a table SkyView without a frame (error text) ends');

  await t.ctx.lightcurveGraphState.open();
  assert.ok(t.working(), 'light curve frame loading');
  t.ctx.wsBusy.during(new Promise(resolve => setTimeout(resolve, 20)));  // its data request, reported by lightcurve.js
  curveFrame.fireLoad();
  await tick();
  assert.ok(t.working(), 'still working while the light-curve data request runs');
  await tick(40);
  assert.equal(t.working(), false);

  const cutouts = t.ctx.cutoutsGraphState.open();
  await tick(10);
  assert.ok(t.working(), 'cutout source lookups running');
  finishCutouts();
  await cutouts;
  assert.equal(t.working(), false);
  assert.deepEqual(calls, ['sky', ['skyIds', ['ZTF1']], 'curve', 'cutouts']);
}

async function testResetReturnsToReady() {
  const t = context();
  t.ctx.loadPane = async function () {};
  let resets = 0;
  t.ctx.reset = () => { resets++; };
  load(t);
  await t.ctx.loadPane('result', 'Node.jsp?id=1');  // its request never completes
  assert.ok(t.working());
  t.ctx.reset();
  assert.equal(resets, 1);
  assert.equal(t.working(), false, 'Reset workspace abandons running work');
  t.ctx.jQuery.complete('Node.jsp?id=1');
  await tick(5);
  const end = t.ctx.wsBusy.begin();
  assert.ok(t.working(), 'a late completion from before Reset does not cancel new work');
  end();
  assert.equal(t.working(), false);
}

// Tag: see test_webservice_tag_action.js (Working for its requests, not while the dialog waits).

function testLightCurvePageReportsItsDataRequest() {
  const src = fs.readFileSync(path.join(root, 'd3/lightcurve.js'), 'utf8');
  assert.match(src, /parent\.wsBusy/, 'the light-curve page reports its data request to the workspace box');
  assert.match(fs.readFileSync(path.join(root, 'd3/lightcurve.jsp'), 'utf8'), /lightcurve\.js\?v=science-date-2/);
}

function testVersions() {
  assert.match(read('index.jsp'), /busy\.js\?v=busy-2/);
  assert.match(read('GraphView.jsp'), /busy\.js\?v=busy-2/);
  assert.match(read('GraphView.jsp'), /tag-graph\.js\?v=tag-dialog-4/);
}

(async () => {
  await testDoubleClickAndSelectionAreTrackedButNestedCallsStaySynchronous();
  await testShowLoadsAreTrackedUntilTheirOwnRequestCompletes();
  await testIframePlotsWaitForTheirFrame();
  await testGraphViewsAreTracked();
  await testResetReturnsToReady();
  testLightCurvePageReportsItsDataRequest();
  testVersions();
  console.log('Busy indicator covers expansion, selection, Show, Describe, SkyView, Cutouts, LightCurve, Tag and plots');
})().catch(error => { console.error(error); process.exitCode = 1; });

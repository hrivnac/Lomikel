'use strict';
// Busy indicator: Ready (green) / Working (red) next to the Version button.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../../webservice');
const busyFile = path.join(root, 'busy.js');
const source = () => fs.readFileSync(busyFile, 'utf8');

function indicator() {
  const classes = new Set(['ws-busy', 'ws-busy-ready']);
  return {
    textContent: 'Ready', attrs: {},
    classList: {
      toggle(name, on) { if (on) classes.add(name); else classes.delete(name); },
      contains: name => classes.has(name)
    },
    setAttribute(name, value) { this.attrs[name] = value; },
    has: name => classes.has(name)
  };
}

function fakeJQuery() {
  const handlers = new Set();
  const $ = () => ({
    on: (event, fn) => { assert.equal(event, 'ajaxComplete'); handlers.add(fn); },
    off: (event, fn) => { handlers.delete(fn); }
  });
  $.complete = url => Array.from(handlers).forEach(fn => fn({}, {}, {url}));
  $.handlerCount = () => handlers.size;
  return $;
}

function context() {
  const el = indicator();
  const frames = [];
  const ctx = {
    console, setTimeout, clearTimeout, Promise, encodeURI,
    document: {getElementById: id => id === 'busy-indicator' ? el : null},
    requestAnimationFrame: fn => { frames.push(fn); return frames.length; }
  };
  ctx.window = ctx;
  ctx.jQuery = fakeJQuery();
  vm.createContext(ctx);
  return {ctx, el, frames, flushFrames() { frames.splice(0).forEach(fn => fn(0)); }};
}

const tick = (ms = 0) => new Promise(resolve => setTimeout(resolve, ms));
const state = el => ({text: el.textContent, working: el.has('ws-busy-working'),
                      ready: el.has('ws-busy-ready'), busy: el.attrs['aria-busy']});
const READY = {text: 'Ready', working: false, ready: true, busy: 'false'};
const WORKING = {text: 'Working', working: true, ready: false, busy: 'true'};

async function testSynchronousWorkStartsOnlyAfterWorkingIsPainted() {
  const t = context();
  const seen = [];
  // Legacy graph calls use synchronous XHR: the red box must already be on screen.
  t.ctx.fillEdges = function () { seen.push({state: state(t.el), framesPending: t.frames.length}); return 'done'; };
  t.ctx.bootstrap = async function (button, level) { seen.push({button, level, state: state(t.el)}); };
  t.ctx.callGremlinGraph = async function () {};
  vm.runInContext(source(), t.ctx);
  const edges = t.ctx.fillEdges();
  assert.deepEqual(state(t.el), WORKING, 'Show all edges turns the box red immediately');
  assert.equal(seen.length, 0, 'synchronous work must wait for a paint');
  t.flushFrames();
  assert.equal(await edges, 'done');
  assert.deepEqual(seen[0], {state: WORKING, framesPending: 0});
  assert.deepEqual(state(t.el), READY);
  await t.ctx.bootstrap('selection', 0);  // the 100 ms fallback covers a tab without animation frames
  assert.deepEqual(seen[1], {button: 'selection', level: 0, state: WORKING});
  assert.deepEqual(state(t.el), READY, 'Search returns to Ready');
}

async function testExploreStaysWorkingUntilTheGraphRequestFinishes() {
  const t = context();
  let finishRequest;
  t.ctx.callGremlinGraph = () => new Promise(resolve => { finishRequest = resolve; });
  // Like GraphView.js: bootstrap starts the request but does not await it.
  t.ctx.bootstrap = async function () { window.callGremlinGraph('g.V()'); };
  t.ctx.fillEdges = function () {};
  const window = t.ctx;
  vm.runInContext(source(), t.ctx);
  const explore = t.ctx.bootstrap('text', 0);
  t.flushFrames();
  await explore;
  assert.deepEqual(state(t.el), WORKING, 'a pending graph request keeps the box red');
  finishRequest();
  await tick();
  assert.deepEqual(state(t.el), READY);
  t.ctx.callGremlinGraph('g.V(1)');  // double-click expansion is not tracked yet
  assert.deepEqual(state(t.el), READY);
}

async function testFailureReturnsToReady() {
  const t = context();
  t.ctx.fillEdges = function () { throw new Error('boom'); };
  t.ctx.bootstrap = async function () {};
  t.ctx.callGremlinGraph = async function () {};
  vm.runInContext(source(), t.ctx);
  const run = t.ctx.fillEdges();
  t.flushFrames();
  await assert.rejects(run, /boom/);
  assert.deepEqual(state(t.el), READY);
}

async function testTableWorksUntilItsOwnPaneLoadCompletes() {
  const t = context();
  const loaded = [];
  t.ctx.loadPane = async function (pane, url) { loaded.push([pane, url, state(t.el).text]); };
  vm.runInContext(source(), t.ctx);
  const table = t.ctx.loadPane('table', 'Nodes.jsp?id=7');
  assert.deepEqual(state(t.el), WORKING);
  t.flushFrames();
  await table;
  assert.deepEqual(loaded, [['table', 'Nodes.jsp?id=7', 'Working']]);
  t.ctx.jQuery.complete('Result.jsp');
  await tick();
  assert.deepEqual(state(t.el), WORKING, 'an unrelated request does not end the Table');
  t.ctx.jQuery.complete('Nodes.jsp?id=7');  // fires after the table's inline scripts ran
  await tick(5);
  assert.deepEqual(state(t.el), READY);
  assert.equal(t.ctx.jQuery.handlerCount(), 0);

  const edgeTable = t.ctx.loadPane('table', 'Edges.jsp?id=a b');
  t.flushFrames();
  await edgeTable;
  t.ctx.jQuery.complete('Edges.jsp?id=a%20b');
  await tick(5);
  assert.deepEqual(state(t.el), READY, 'edge tables are tracked with the encoded URL');

  await t.ctx.loadPane('result', 'Result.jsp');
  assert.deepEqual(state(t.el), READY, 'other pages are not tracked');
  assert.deepEqual(loaded.at(-1), ['result', 'Result.jsp', 'Ready']);
}

async function testOverlappingOperationsAndReevaluation() {
  const t = context();
  let finishFirst;
  t.ctx.loadPane = async function () {};
  t.ctx.bootstrap = () => new Promise(resolve => { finishFirst = resolve; });
  t.ctx.fillEdges = function () {};
  t.ctx.callGremlinGraph = async function () {};
  vm.runInContext(source(), t.ctx);
  const first = t.ctx.bootstrap('text');
  const second = t.ctx.fillEdges();  // the box is already red: runs at once
  t.flushFrames();
  await second;
  await tick();
  assert.deepEqual(state(t.el), WORKING, 'still working while another operation runs');
  finishFirst();
  await first;
  assert.deepEqual(state(t.el), READY);

  // Reset reloads GraphView.jsp: GraphView.js redefines globals, busy.js rewraps them once.
  const wrappedLoadPane = t.ctx.loadPane;
  let calls = 0;
  t.ctx.fillEdges = function () { calls++; };
  vm.runInContext(source(), t.ctx);
  vm.runInContext(source(), t.ctx);
  assert.equal(t.ctx.loadPane, wrappedLoadPane, 'loadPane is wrapped only once');
  const again = t.ctx.fillEdges();
  t.flushFrames();
  await again;
  assert.equal(calls, 1, 'the new fillEdges is wrapped exactly once');
}

function testPagesIncludeTheIndicator() {
  const index = fs.readFileSync(path.join(root, 'index.jsp'), 'utf8');
  const box = index.indexOf('id="busy-indicator"');
  assert.ok(box > 0 && box < index.indexOf('id="version-button"'), 'the box is left of Version');
  assert.match(index, /<span id="busy-indicator" class="ws-busy ws-busy-ready" role="status" aria-live="polite" aria-busy="false">Ready<\/span>/);
  assert.ok(index.indexOf('busy.js?v=busy-2') > index.indexOf('workspace.js?v='), 'busy.js wraps the workspace loadPane');
  const graph = fs.readFileSync(path.join(root, 'GraphView.jsp'), 'utf8');
  assert.ok(graph.indexOf('busy.js?v=busy-2') > graph.indexOf('GraphView.js?v='), 'busy.js rewraps a reloaded controller');
  assert.ok(graph.indexOf('busy.js?v=busy-2') < graph.indexOf('graphviewready'), 'profiles bootstrap through the wrapper');
  const css = fs.readFileSync(path.join(root, 'workspace.css'), 'utf8');
  assert.match(css, /\.ws-busy-ready\s*\{[^}]*background:#1e8e4e/);
  assert.match(css, /\.ws-busy-working\s*\{[^}]*background:#c62828/);
  assert.match(index, /workspace\.css\?v=busy-1/);
}

(async () => {
  assert.ok(fs.existsSync(busyFile), 'busy.js exists');
  await testSynchronousWorkStartsOnlyAfterWorkingIsPainted();
  await testExploreStaysWorkingUntilTheGraphRequestFinishes();
  await testFailureReturnsToReady();
  await testTableWorksUntilItsOwnPaneLoadCompletes();
  await testOverlappingOperationsAndReevaluation();
  testPagesIncludeTheIndicator();
  console.log('Busy indicator tests passed');
})().catch(error => { console.error(error); process.exitCode = 1; });

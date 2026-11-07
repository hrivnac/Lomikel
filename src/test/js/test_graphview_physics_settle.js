/* Graph physics must organize the graph and then stop moving.
 * vis-network keeps simulating until every node is slower than minVelocity; some
 * graphs (notably with clustered groups) keep rotating forever. The controller
 * stops the simulation after graphMaxSimulationSeconds; dragging restarts it. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const web = path.join(__dirname, '../../webservice');
const source = fs.readFileSync(path.join(web, 'GraphView.js'), 'utf8');
const optionsSource = fs.readFileSync(path.join(web, 'OptionsDefault.js'), 'utf8');

function graphContext() {
  const controls = Object.fromEntries(['vis', 'filter', 'add2graph', 'clusterize', 'zoom', 'stabilize', 'physics']
    .map(id => [id, {value: '', checked: false, addEventListener() {}}]));
  controls.physics.checked = true;
  const timers = [];
  const networks = [];
  const context = vm.createContext({
    document: {getElementById: id => controls[id] || null},
    console, stylesheet: {nodes: {}, edges: {}},
    setTimeout: (fn, ms) => { const t = {fn, ms, cleared: false}; timers.push(t); return t; },
    clearTimeout: t => { if (t) t.cleared = true; },
    vis: {Network: class {
      constructor(container, data, options) {
        this.listeners = {}; this.options = options; this.stops = 0; this.destroyed = false; networks.push(this);
      }
      on(name, fn) { (this.listeners[name] = this.listeners[name] || []).push(fn); }
      once() {}
      emit(name, params) { (this.listeners[name] || []).forEach(fn => fn(params)); }
      stopSimulation() { this.stops++; }
      setOptions() {}
      destroy() { this.destroyed = true; }
    }}
  });
  context.window = context;
  vm.runInContext(optionsSource, context, {filename: 'OptionsDefault.js'});
  vm.runInContext(source, context, {filename: 'GraphView.js'});
  const pending = () => timers.filter(t => !t.cleared);
  return {context, controls, timers, pending, networks};
}

function testDefaultsSettleFaster() {
  const {context} = graphContext();
  assert.equal(context.options4Graph.physics.minVelocity, 1, 'stop when nodes move slowly (vis default 0.1 never ends on large graphs)');
  assert.equal(context.graphMaxSimulationSeconds, 10);
}

function testSimulationIsStoppedAfterTheLimit() {
  const t = graphContext();
  t.context.show(null);
  const net = t.networks.at(-1);
  net.emit('startStabilizing');
  assert.equal(t.pending().length, 1);
  assert.equal(t.pending()[0].ms, 10000);
  t.pending()[0].fn();
  assert.equal(net.stops, 1, 'a graph still moving after 10 s is stopped');
}

function testNaturalStabilizationCancelsTheLimit() {
  const t = graphContext();
  t.context.show(null);
  const net = t.networks.at(-1);
  net.emit('startStabilizing');
  net.emit('stabilized', {iterations: 300});
  assert.equal(t.pending().length, 0, 'nothing to stop once the graph is at rest');
  assert.equal(net.stops, 0);
}

function testDraggingRestartsTheLimitOnce() {
  const t = graphContext();
  t.context.show(null);
  const net = t.networks.at(-1);
  net.emit('startStabilizing');
  net.emit('startStabilizing');  // vis restarts the simulation when a node is dragged
  assert.equal(t.pending().length, 1, 'one limit at a time');
  t.pending()[0].fn();
  assert.equal(net.stops, 1);
}

function testOldNetworksAreNotTouched() {
  const t = graphContext();
  t.context.show(null);
  const first = t.networks.at(-1);
  first.emit('startStabilizing');
  const stale = t.pending()[0];
  t.context.show(null);  // a new graph replaces the old network
  stale.fn();
  assert.equal(first.stops, 0, 'a destroyed network is never stopped');
  t.context.disposeGraphView();
  assert.equal(t.pending().length, 0, 'Reset clears the limit');
}

function testPhysicsSwitchedOffNeedsNoLimit() {
  const t = graphContext();
  t.context.show(null);
  const net = t.networks.at(-1);
  t.controls.physics.checked = false;
  t.context.switchPhysics();  // "Live physics" unticked
  net.emit('startStabilizing');
  assert.equal(t.pending().length, 0);
  t.context.graphMaxSimulationSeconds = 0;  // 0 disables the limit
  t.controls.physics.checked = true;
  t.context.switchPhysics();
  net.emit('startStabilizing');
  assert.equal(t.pending().length, 0);
}

function testVersions() {
  assert.match(fs.readFileSync(path.join(web, 'GraphView.jsp'), 'utf8'), /GraphView\.js\?v=canonical-5/);
  assert.match(fs.readFileSync(path.join(web, 'index.jsp'), 'utf8'), /OptionsDefault\.js\?v=physics-settle-1/);
}

testDefaultsSettleFaster();
testSimulationIsStoppedAfterTheLimit();
testNaturalStabilizationCancelsTheLimit();
testDraggingRestartsTheLimitOnce();
testOldNetworksAreNotTouched();
testPhysicsSwitchedOffNeedsNoLimit();
testVersions();
console.log('Graph physics stops: minVelocity 1, at most 10 s per simulation run');

/* Offline behavior checks for the shared FinkBrowser graph controller. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../../webservice/GraphView.js'), 'utf8');

function graphContext() {
  const controls = Object.fromEntries(['vis', 'gremlin_server', 'bootstrap_command', 'bootstrap_graph',
    'add2graph', 'select', 'feedback', 'commands', 'filter', 'clusterize', 'zoom', 'stabilize',
    'expandTo', 'expandFrom', 'removeOld'].map(id => [id, {
      value: '', checked: false, innerHTML: '', addEventListener() {}
    }]));
  controls.vis.offsetHeight = 500;
  controls.feedback.appendChild = () => {};
  const context = vm.createContext({
    document: {getElementById: id => controls[id] || null,
      createTextNode: text => ({type:'text', text}), createElement: tag => ({type:tag})},
    vis: {Network: class {}}, options4Graph: {}, stylesheet: {nodes: {}, edges: {}},
    sleep: async () => {}, console, setTimeout
  });
  context.window = context;
  context.server = '//offline-graph:8182';
  vm.runInContext(source, context, {filename: 'GraphView.js'});
  return {context, controls};
}

function testExpansionUsesSnapshot() {
  const {context} = graphContext();
  context.nodes.push({id: 1}, {id: 2});
  const calls = [];
  context.expand = id => {
    calls.push(id);
    if (calls.length > 6) throw Error('expanded nodes appended during iteration');
    context.nodes.push({id: id + 10});
  };
  context.expandNodes(1);
  assert.deepEqual(calls, [1, 2], 'one-level expansion should visit only the starting nodes');
}

function testRemoveOldKeepsExpandedVertex() {
  const {context, controls} = graphContext();
  context.nodes.push({id: 1}, {id: 2});
  context.edges.push({id: 'edge-1', from: 1, to: 2});
  context.selectedNode = context.nodes[0];
  controls.removeOld.checked = true;
  controls.expandTo.checked = true;
  const calls = [];
  context.callGremlinGraph = command => calls.push(command);
  context.expand(2);
  assert.deepEqual(Array.from(context.nodes, node => node.id), [2]);
  assert.equal(context.edges.length, 0, 'old edges must not reference cleared vertices');
  assert.equal(calls.length, 2, 'outgoing vertices and edges are requested');
}

function testZoomClusteringRespectsMenuToggle() {
  const {context, controls} = graphContext();
  context.vis.Network = class {
    constructor() { this.listeners = {}; }
    on(name, callback) { this.listeners[name] = callback; }
    once() {}
  };
  context.show(null);
  context.lastClusterZoomLevel = 1;
  const clustered = [];
  context.makeClusters = scale => clustered.push(scale);
  controls.zoom.checked = false;
  context.network.listeners.zoom({direction: '-', scale: 0.5});
  assert.deepEqual(clustered, [], 'zoom clustering disabled in graph menu');
  controls.zoom.checked = true;
  context.network.listeners.zoom({direction: '-', scale: 0.5});
  assert.deepEqual(clustered, [0.5], 'zoom clustering enabled in graph menu');
}

async function testBootstrapWaitsForCanvasAndControls() {
  const {context, controls} = graphContext();
  delete controls.vis;
  context.container = null;
  controls.gremlin_server.value = '//host:8182';
  controls.bootstrap_command.value = 'g.V()';
  controls.bootstrap_graph.value = 'g.V().has("lbl", "OCol")';
  const calls = [];
  context.sleep = async () => {
    controls.vis = {offsetHeight: 500};
    if (calls.length > 3) throw Error('stale canvas wait');
    calls.push('wait');
  };
  context.callGremlinGraph = command => calls.push(command);
  context.showTab = () => {};
  await context.bootstrap('selection');
  assert.ok(calls.includes('wait'), 'bootstrap waits until the graph canvas exists');
  assert.equal(calls.at(-1), 'g.V().has("lbl", "OCol")');
  assert.equal(context.container, controls.vis);
}

async function testGraphRequestsEncodeGremlinForDirectAndProxy() {
  const {context, controls} = graphContext();
  controls.select.value = 'limit(10)';
  const urls = [];
  context.XMLHttpRequest = class {
    open(method, url) { assert.equal(method, 'GET'); urls.push(url); }
    send() { this.status = 200; this.readyState = 4; this.responseText = '{}'; this.onload(); }
  };
  context.parseGraph = () => [];
  context.show = () => {};
  context.expandNodes = () => {};
  const request = 'g.V().has("cls", "a&b").has("flavor", "x+y")';
  for (const server of ['//host:8182', '//host/Proxy.jsp?server=http://host:8182']) {
    await context.callGremlinGraph(request, server);
    const url = new URL('http:' + urls.at(-1));
    assert.equal(url.searchParams.get('gremlin'), request + '.limit(10)');
    assert.equal(url.searchParams.getAll('gremlin').length, 1);
    if (server.includes('?')) assert.equal(url.searchParams.get('server'), 'http://host:8182');
  }
}

function testRemovingVertexAlsoRemovesIncidentEdges() {
  const {context} = graphContext();
  context.nodes.push({id: 1}, {id: 2}, {id: 3});
  context.edges.push({id: 'ab', from: 1, to: 2}, {id: 'bc', from: 2, to: 3});
  context.show = () => {};
  context.removeNode(1);
  assert.deepEqual(Array.from(context.edges, edge => edge.id), ['bc']);
}

function testFilteringVerticesRemovesOrphanEdges() {
  const {context, controls} = graphContext();
  controls.filter.value = 'keep';
  context.nodes.push({id: 1, label: 'keep'}, {id: 2, label: 'discard'}, {id: 3, label: 'keep too'});
  context.edges.push({id: 'ab', from: 1, to: 2}, {id: 'ac', from: 1, to: 3});
  context.show = () => {};
  context.applyFilter();
  assert.deepEqual(Array.from(context.nodes, node => node.id), [1, 3]);
  assert.deepEqual(Array.from(context.edges, edge => edge.id), ['ac']);
}

function testRenderDisposesOldVisNetwork() {
  const {context} = graphContext();
  const instances = [];
  context.vis.Network = class {
    constructor() { this.destroyed = false; instances.push(this); }
    on() {}
    once() {}
    getScale() { return 1.5; }
    getViewPosition() { return {x: 10, y: 20}; }
    moveTo(options) { this.view = options; }
    destroy() { this.destroyed = true; }
  };
  context.show(null);
  context.show(null);
  assert.equal(instances.length, 2);
  assert.equal(instances[0].destroyed, true, 'stale physics and listeners must be disposed');
  assert.equal(instances[1].destroyed, false);
  assert.equal(instances[1].view?.scale, 1.5, 'rerender retains user zoom');
}

function testFeedbackLogsCommandsAsText() {
  const {context, controls} = graphContext();
  const entries = [];
  controls.feedback.appendChild = node => entries.push(node);
  context.document.createTextNode = text => ({type: 'text', text});
  context.document.createElement = tag => ({type: tag});
  controls.feedback.parentElement = {scrollTop: 0, scrollHeight: 240};
  context.feedbackLine('<img src=x onerror=alert(1)>');
  assert.equal(controls.feedback.parentElement.scrollTop, 240, 'the compact activity pane follows new entries');
  assert.deepEqual(entries.map(node => node.type), ['text', 'br']);
  assert.equal(entries[0].text, '<img src=x onerror=alert(1)>');
  assert.equal(controls.feedback.innerHTML, '');
}

async function testGraphRequestFailureIsReported() {
  const {context, controls} = graphContext();
  const entries = [];
  controls.feedback.appendChild = node => entries.push(node);
  context.XMLHttpRequest = class {
    open() {}
    send() { this.readyState = 4; this.status = 503; this.responseText = 'unavailable'; this.onload(); }
  };
  context.show = () => { throw Error('failure must not replace the graph'); };
  await context.callGremlinGraph('g.V()', '//host:8182');
  assert.ok(entries.some(node => node.text?.includes('HTTP 503')), 'failed request appears in panel 05');
}

function testValueRequestFailureDoesNotParseErrorPage() {
  const {context, controls} = graphContext();
  const entries = [];
  controls.feedback.appendChild = node => entries.push(node);
  context.XMLHttpRequest = class {
    open() {}
    send() { this.status = 503; this.responseText = '<html>unavailable</html>'; }
  };
  assert.equal(context.callGremlinValues('g.V().count()', '//host:8182'), '');
  assert.ok(entries.some(node => node.text?.includes('HTTP 503')));
}

function testClusterRefreshKeepsViewPosition() {
  const {context, controls} = graphContext();
  controls.clusterize.checked = true;
  controls.add2graph.checked = true;
  context.vis.Network = class {
    constructor() { this.view = null; }
    on() {}
    once() {}
    destroy() {}
    getScale() { return 1.5; }
    getViewPosition() { return {x: 10, y: 20}; }
    moveTo(view) { this.view = view; }
    setData() { this.view = null; }
  };
  context.show(null);
  context.show([]);
  assert.equal(context.network.view?.scale, 1.5, 'clustering must not undo restored zoom');
}

function testFreshRenderAvoidsRedundantDataReset() {
  const {context, controls} = graphContext();
  controls.clusterize.checked = true;
  let dataResets = 0;
  context.vis.Network = class {
    on() {}
    once() {}
    setData() { dataResets++; }
  };
  context.show([]);
  assert.equal(dataResets, 0, 'fresh vis network already owns the graph data');
}

function testRepeatedGroupCreatesOneCluster() {
  const {context} = graphContext();
  context.groups.push('sample', 'sample');
  const names = [];
  context.network = {
    setData() {},
    cluster(options) { names.push(options.clusterNodeProperties.id); }
  };
  context.clusterByGroups();
  assert.deepEqual(names, ['cluster:sample']);
}

function testParallelEdgesKeepDistinctIds() {
  const {context} = graphContext();
  context.nodes.push({id:1, group:' '}, {id:2, group:' '});
  context.edges.push({id:'first', from:2, to:1});
  context.stylesheet.edges.overlaps = {properties:{}, graphics:{title:'overlap', subtitle:' ',
    label:' ', group:' ', arrows:' ', value:'1'}, actions:[]};
  context.vis.Network = class { on() {} once() {} };
  context.show([{type:'edge', label:'overlaps', id:'second', inVid:2, outVid:1, element:[]}]);
  assert.deepEqual(Array.from(context.edges, edge => edge.id), ['first','second']);
}

function testWorkspaceDisposesOldGraphBeforeReplacement() {
  const {context} = graphContext();
  let destroyed = 0;
  context.network = {destroy() { destroyed++; }};
  context.disposeGraphView();
  assert.equal(destroyed, 1);
  assert.equal(context.network, null);
}

function testDoubleClickEdgeExpandsBothEndpoints() {
  const {context, controls} = graphContext();
  controls.expandTo.checked = true;
  context.nodes.push({id:1}, {id:2});
  context.edges.push({id:'overlap', from:1, to:2});
  context.vis.Network = class {
    constructor() { this.listeners = {}; }
    on(name, fn) { this.listeners[name] = fn; }
    once() {}
    isCluster() { return false; }
  };
  context.show(null);
  const expanded = [];
  context.expand = id => expanded.push(id);
  context.network.listeners.doubleClick({nodes:[], edges:['overlap']});
  assert.deepEqual(expanded, [1,2]);
}

function testEdgeExpansionReplaceKeepsBothEndpoints() {
  const {context, controls} = graphContext();
  context.nodes.push({id:1}, {id:2}, {id:3});
  context.edges.push({id:'overlap', from:1, to:2}, {id:'other', from:2, to:3});
  controls.removeOld.checked = true;
  controls.expandTo.checked = true;
  context.vis.Network = class {
    constructor() { this.listeners = {}; }
    on(name, fn) { this.listeners[name] = fn; }
    once() {}
  };
  context.show(null);
  const calls = [];
  context.callGremlinGraph = command => calls.push(command);
  context.network.listeners.doubleClick({nodes:[], edges:['overlap']});
  assert.deepEqual(Array.from(context.nodes, node => node.id), [1,2]);
  assert.deepEqual(Array.from(context.edges, edge => edge.id), ['overlap']);
  assert.equal(calls.length, 4, 'both endpoints request outgoing vertices and edges');
}

function testNoDirectionSelectionLeavesGraphUntouched() {
  const {context, controls} = graphContext();
  context.nodes.push({id:1}, {id:2});
  context.edges.push({id:'edge', from:1, to:2});
  controls.removeOld.checked = true;
  context.callGremlinGraph = () => { throw Error('no direction selected'); };
  context.expand(1);
  assert.deepEqual(Array.from(context.nodes, node => node.id), [1,2]);
  assert.equal(context.edges.length, 1);
  context.expandEdge('edge');
  assert.deepEqual(Array.from(context.nodes, node => node.id), [1,2]);
  assert.equal(context.edges.length, 1);
}

async function testResetCancelsBootstrapWaitingForCanvas() {
  const {context, controls} = graphContext();
  delete controls.vis;
  const calls = [];
  context.sleep = async () => {
    context.disposeGraphView();
    controls.vis = {offsetHeight:500};
    vm.runInContext(source, context, {filename:'GraphView.js'}); // new pane replaces globals
  };
  controls.gremlin_server.value = '//host:8182';
  controls.bootstrap_command.value = 'g.V()';
  context.callGremlinGraph = command => calls.push(command);
  context.showTab = () => {};
  await context.bootstrap('selection');
  assert.deepEqual(calls, [], 'reset cancels the pending initial graph');
}

async function testResetCancelsGraphRequestWaitingForMenu() {
  const {context, controls} = graphContext();
  delete controls.select;
  const urls = [];
  context.setTimeout = callback => {
    context.disposeGraphView();
    controls.select = {value:'limit(10)'};
    callback();
  };
  context.XMLHttpRequest = class { open(method,url) { urls.push(url); } send() {} };
  await context.callGremlinGraph('g.V()', '//host:8182');
  assert.deepEqual(urls, [], 'reset cancels a pending graph request before dispatch');
}

async function main() {
  testExpansionUsesSnapshot();
  testRemoveOldKeepsExpandedVertex();
  testZoomClusteringRespectsMenuToggle();
  testRemovingVertexAlsoRemovesIncidentEdges();
  testFilteringVerticesRemovesOrphanEdges();
  testRenderDisposesOldVisNetwork();
  testClusterRefreshKeepsViewPosition();
  testFreshRenderAvoidsRedundantDataReset();
  testRepeatedGroupCreatesOneCluster();
  testParallelEdgesKeepDistinctIds();
  testWorkspaceDisposesOldGraphBeforeReplacement();
  testDoubleClickEdgeExpandsBothEndpoints();
  testEdgeExpansionReplaceKeepsBothEndpoints();
  testNoDirectionSelectionLeavesGraphUntouched();
  testFeedbackLogsCommandsAsText();
  testValueRequestFailureDoesNotParseErrorPage();
  await testBootstrapWaitsForCanvasAndControls();
  await testGraphRequestsEncodeGremlinForDirectAndProxy();
  await testGraphRequestFailureIsReported();
  await testResetCancelsBootstrapWaitingForCanvas();
  await testResetCancelsGraphRequestWaitingForMenu();
  console.log('GraphView interaction tests passed');
}
main().catch(error => { console.error(error); process.exitCode = 1; });

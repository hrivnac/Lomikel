'use strict';
// Fink Portal -> Graph Browser hand-off (v1): one validated LSST object opens
// with a bounded first-degree neighbourhood; ordinary visits stay unchanged.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const base = path.join(__dirname, '../../webservice');
const read = name => fs.readFileSync(path.join(base, name), 'utf8');

const DIA = '313761043604045880';
const SS = '20892028060578637';
const EXISTS = id => "g.V().has('lbl', 'object').has('objectId', '" + id + "').count()";
const GRAPH = id => "g.V().has('lbl', 'object').has('objectId', '" + id + "').limit(1)" +
  ".union(identity(), bothE().limit(25).union(otherV(), identity()))";

function element(id, extra = {}) {
  return Object.assign({id, value: '', checked: false, children: [], textContent: '',
    appendChild(child) { this.children.push(child); return child; },
    replaceChildren(...kids) { this.children = kids; this.textContent = ''; },
    setAttribute(name, value) { this[name] = value; },
    querySelector() { return null; }}, extra);
}

function environment({search = '', servers = [['//157.136.253.253:24445', 'ZTF'], ['//134.158.243.144:24445', 'LSST']],
                      count = 1, controls = true, profile = 'cc', nodes = null} = {}) {
  const requests = {values: [], graph: [], bootstrap: [], selected: [], commands: [], feedback: [], tabs: []};
  const options = servers.map(([value, survey]) => ({value, survey}));
  const select = element('gremlin_server', {value: options.length ? options[0].value : '',
    querySelector(selector) {
      const match = /option\[data-survey="([A-Z]+)"\]/.exec(selector);
      return match ? options.find(option => option.survey === match[1]) || null : null;
    }});
  const els = {gremlin_server: select, bootstrap_command: element('bootstrap_command', {value: 'g.V()'}),
    add2graph: element('add2graph', {checked: true}), commands: element('commands'), graph: element('graph'),
    feedback: element('feedback'),
    bootstrap_graph: element('bootstrap_graph'), select: element('select', {value: 'limit(10)'})};
  const listeners = {};
  const document = {
    getElementById(id) { return controls || id === 'graph' ? els[id] || null : null; },
    createElement(tag) { return element(tag, {tagName: tag.toUpperCase()}); },
    createTextNode(text) { return {nodeType: 3, textContent: String(text)}; }
  };
  const window = {
    location: {search, href: 'http://127.0.0.1/FinkBrowser/index.jsp' + search},
    addEventListener(type, fn) { (listeners[type] = listeners[type] || []).push(fn); },
    removeEventListener(type, fn) { listeners[type] = (listeners[type] || []).filter(f => f !== fn); },
    dispatchEvent(event) { (listeners[event.type] || []).slice().forEach(fn => fn(event)); },
    graphViewToken: {cancelled: false},
    nodes: nodes || [],
    activityLogMessage(text) { requests.feedback.push(String(text)); },
    callGremlinValues(request, server) {
      requests.values.push([request, server]);
      if (count === null) return '';
      return [{'@type': 'g:Int64', '@value': count}];
    },
    async callGremlinGraph(request, server, level, token) {
      requests.graph.push([request, server, level, token === window.graphViewToken]);
      window.nodes.push({id: 4104, label: window.handoffTestObject, heading: 'object:' + window.handoffTestObject});
      window.nodes.push({id: 8208, label: 'FINK:SN candidate', heading: 'OCol:FINK'});
    },
    bootstrap(...args) { requests.bootstrap.push(args); },
    showGraphCommands(item, edge) { requests.commands.push([item.id, edge]); },
    showTab(tab) { requests.tabs.push(tab); },
    network: {selectNodes(ids) { requests.selected.push(Array.from(ids)); }, findNode(id) { return [id]; }},
    loadPane() {},
    setTimeout: (fn) => { fn(); return 1; }
  };
  window.window = window;
  window.document = document;
  window.URLSearchParams = URLSearchParams;
  window.Event = class { constructor(type) { this.type = type; } };
  window.Promise = Promise;
  const context = vm.createContext(window);
  vm.runInContext(read('handoff.js'), context, {filename: 'handoff.js'});
  if (profile) vm.runInContext(read('profiles/' + profile + '.js'), context, {filename: profile + '.js'});
  return {window, els, requests, ready() {
    window.graphViewReady = els.graph;
    window.dispatchEvent(new window.Event('graphviewready'));
    return window.finkHandoffDone || Promise.resolve();
  }};
}

function testParse() {
  const env = environment({profile: null});
  const parse = env.window.parseFinkHandoff;
  assert.equal(parse(''), null, 'an ordinary visit is not a hand-off');
  assert.equal(parse('?profile=cc&style=x'), null, 'existing parameters alone do not start a hand-off');
  const dia = parse('?profile=cc&handoff=v1&kind=dia&objectId=' + DIA);
  assert.deepEqual(JSON.parse(JSON.stringify(dia)), {kind: 'dia', objectId: DIA, exists: EXISTS(DIA), graph: GRAPH(DIA)});
  assert.equal(parse('?handoff=v1&kind=ss&objectId=' + SS).objectId, SS, 'a 17-digit ID stays an exact string');
  assert.equal(parse('?handoff=v1&kind=dia&objectId=9223372036854775807').objectId, '9223372036854775807');
  for (const [query, reason] of [
    ['?handoff=v2&kind=dia&objectId=' + DIA, /version/],
    ['?handoff=v1&kind=ztf&objectId=' + DIA, /kind/],
    ['?handoff=v1&objectId=' + DIA, /kind/],
    ['?handoff=v1&kind=dia', /objectId/],
    ['?handoff=v1&kind=dia&objectId=0123', /objectId/],
    ['?handoff=v1&kind=dia&objectId=12345678901234567890', /objectId/],
    ['?handoff=v1&kind=dia&objectId=1e5', /objectId/],
    ["?handoff=v1&kind=dia&objectId=1')", /objectId/],
    ['?handoff=v1&kind=dia&objectId=' + DIA + '&objectId=' + SS, /objectId/],
    ['?handoff=v1&kind=dia&kind=ss&objectId=' + DIA, /kind/],
    ['?handoff=v1&kind=dia&objectId=' + DIA + '&gremlin=g.V().drop()', /unsupported parameter/],
    ['?handoff=v1&kind=dia&objectId=' + DIA + '&server=//evil:1', /unsupported parameter/],
    ['?kind=dia&objectId=' + DIA, /version/]]) {
    const result = parse(query);
    assert.ok(result && result.error, query + ' is rejected');
    assert.match(result.error, reason, query);
  }
}

async function testDiaHandoff() {
  const env = environment({search: '?profile=cc&handoff=v1&kind=dia&objectId=' + DIA});
  env.window.handoffTestObject = DIA;
  assert.equal(env.window.graphViewProfileBooted, true, 'a valid hand-off replaces the profile start');
  await env.ready();
  assert.deepEqual(env.requests.bootstrap, [], 'the profile default bootstrap never runs');
  assert.deepEqual(env.requests.values, [[EXISTS(DIA), '//134.158.243.144:24445']],
    'existence is checked on the configured LSST server even when it is not the first option');
  assert.deepEqual(env.requests.graph, [[GRAPH(DIA), '//134.158.243.144:24445', 0, true]], 'exactly one bounded graph request');
  assert.equal(env.els.gremlin_server.value, '//134.158.243.144:24445', 'later actions use the LSST server');
  assert.equal(env.els.bootstrap_command.value, GRAPH(DIA), 'the executed command is visible and repeatable');
  assert.equal(env.els.add2graph.checked, false, 'the hand-off starts a new graph');
  assert.deepEqual(env.requests.selected, [[4104]], 'the incoming object is selected');
  assert.deepEqual(env.requests.commands, [[4104, false]], 'its actions are shown');
  assert.ok(env.requests.feedback.some(text => text.includes('DIA object ' + DIA) && /Fink Portal/.test(text)));
  // Reset reloads the graph fragment: the hand-off must not run again.
  await env.ready();
  assert.equal(env.requests.graph.length, 1);
  assert.equal(env.requests.values.length, 1);
}

async function testMissingObject() {
  for (const [kind, id] of [['dia', DIA], ['ss', SS]]) {
    const env = environment({search: '?handoff=v1&kind=' + kind + '&objectId=' + id, count: 0});
    await env.ready();
    assert.equal(env.requests.graph.length, 0, 'no unrelated default graph is drawn');
    assert.equal(env.requests.bootstrap.length, 0);
    const text = JSON.stringify(env.els.commands.children);
    assert.match(text, new RegExp(id), 'the message keeps the requested ID');
    assert.match(text, /not yet available in the graph/);
    const link = env.els.commands.children.find(child => child.tagName === 'A');
    if (kind === 'dia') {
      assert.equal(link.href, 'https://lsst.fink-portal.org/' + id);
      assert.equal(link.target, '_blank');
      assert.equal(link.rel, 'noopener noreferrer');
    } else {
      assert.equal(link, undefined, 'an ssObjectId is never used as a Portal path');
    }
  }
}

async function testBackendFailureIsNotAbsence() {
  const env = environment({search: '?handoff=v1&kind=dia&objectId=' + DIA, count: null});
  await env.ready();
  assert.equal(env.requests.graph.length, 0);
  const text = JSON.stringify(env.els.commands.children);
  assert.match(text, /could not be checked/);
  assert.doesNotMatch(text, /not yet available/);
}

async function testInvalidKeepsNormalWorkspace() {
  const env = environment({search: "?profile=cc&handoff=v1&kind=dia&objectId=1')"});
  assert.notEqual(env.window.graphViewProfileBooted, true);
  await env.ready();
  assert.deepEqual(env.requests.bootstrap, [['text', 0]], 'the ordinary profile start still runs');
  assert.equal(env.requests.values.length, 0, 'no hand-off query is sent');
  assert.ok(env.requests.feedback.some(text => /Fink Portal link ignored/.test(text) && /objectId/.test(text)));
  const plain = environment({search: '?profile=cc'});
  await plain.ready();
  assert.deepEqual(plain.requests.bootstrap, [['text', 0]], 'an ordinary visit is unchanged');
  assert.deepEqual(plain.requests.feedback, []);
}

async function testNoLsstServer() {
  const env = environment({search: '?handoff=v1&kind=dia&objectId=' + DIA,
    servers: [['//157.136.253.253:24445', 'ZTF']]});
  await env.ready();
  assert.equal(env.requests.values.length + env.requests.graph.length, 0);
  assert.match(JSON.stringify(env.els.commands.children), /no LSST graph server/);
}

async function testResetBeforeControlsCancels() {
  const env = environment({search: '?handoff=v1&kind=dia&objectId=' + DIA, controls: false});
  const done = env.ready();
  env.window.graphViewToken.cancelled = true;
  await done;
  assert.equal(env.requests.values.length + env.requests.graph.length, 0);
}

async function testWaitsForActivityPane() {
  // The existence query must be logged: do not start before the Activity pane exists.
  const env = environment({search: '?handoff=v1&kind=dia&objectId=' + DIA});
  env.window.handoffTestObject = DIA;
  const feedback = env.els.feedback;
  delete env.els.feedback;
  let polls = 0;
  env.window.setTimeout = fn => { if (++polls === 3) env.els.feedback = feedback; fn(); return 1; };
  await env.ready();
  assert.ok(polls >= 3, 'the hand-off waited for the Activity pane');
  assert.equal(env.requests.values.length, 1);
  assert.equal(env.requests.graph.length, 1);
}

function testPackaging() {
  const index = read('index.jsp');
  const handoff = index.indexOf('src="handoff.js?v=portal-handoff-1"');
  assert.ok(handoff > index.indexOf('src="busy.js'), 'loaded after the workspace wrappers');
  assert.ok(handoff < index.indexOf("$.getScript('profiles/"), 'loaded before the profile can bootstrap');
  for (const site of ['CC', 'IJCLab', 'Local']) {
    const servers = read('Servers-' + site + '.jsp');
    assert.match(servers, /value="\/\/134\.158\.243\.144:24445"\s+data-survey="LSST"/, site + ' marks the LSST graph');
    assert.match(servers, /value="\/\/157\.136\.253\.253:24445"\s+data-survey="ZTF"/, site + ' marks the ZTF graph');
  }
  const source = read('handoff.js');
  assert.doesNotMatch(source, /innerHTML|eval\(|new Function/, 'graph and URL text is never interpreted as markup or code');
}

(async () => {
  testParse();
  await testDiaHandoff();
  await testMissingObject();
  await testBackendFailureIsNotAbsence();
  await testInvalidKeepsNormalWorkspace();
  await testNoLsstServer();
  await testResetBeforeControlsCancels();
  await testWaitsForActivityPane();
  testPackaging();
  console.log('Fink Portal hand-off tests passed');
})().catch(error => { console.error(error); process.exit(1); });

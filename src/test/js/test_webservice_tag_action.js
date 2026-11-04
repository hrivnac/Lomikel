'use strict';
// "Tag" selection action: sentinel action on object vertices, safe routing through GraphView/workspace,
// and tag-graph.js building one fixed, validated registerOCol call on the vertex's own graph server.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const web = path.resolve(__dirname, '../../webservice');
const read = file => fs.readFileSync(path.join(web, file), 'utf8');

function element(tag) {
  return {tag, children: [], attributes: {}, listeners: {}, textContent: '', value: '', className: '',
    appendChild(c) { this.children.push(c); return c; }, replaceChildren(...c) { this.children = c; },
    setAttribute(k, v) { this.attributes[k] = v; }, addEventListener(k, fn) { this.listeners[k] = fn; }};
}
function graphContext() {
  const controls = Object.fromEntries(['vis','filter','add2graph','clusterize','zoom','stabilize','commands',
    'physics','glayout','glayout_direction','glayout_method','expandTo','expandFrom','removeOld','select']
    .map(id => [id, element(id)]));
  const loads = [];
  const context = vm.createContext({
    document: {getElementById: id => controls[id] || null, createElement: element,
      createTextNode: text => ({tag: '#text', textContent: text})},
    vis: {Network: class {constructor() {this.listeners = {};} on(n, fn) {this.listeners[n] = fn;}
      once() {} isCluster() {return false;} cluster() {} setData() {} setOptions() {} destroy() {}}},
    loadPane: (...args) => loads.push(args), options4Graph: {physics: {enabled: true}}, console, URL, URLSearchParams,
    location: {href: 'https://fink.example/FinkBrowser/index.jsp'}});
  context.window = context;
  for (const file of ['StylesheetDefault.js', 'Stylesheet.js', 'SurveyStylesheet.js', 'GraphView.js'])
    vm.runInContext(read(file), context, {filename: file});
  const node = context.stylesheet.nodes.object;
  node.properties = {};
  Object.assign(node.graphics, {label: 'lbl', title: 'title', subtitle: 'subtitle', group: 'group'});
  node.actions = node.actions.map(a => a.url && a.url.gremlin ? {...a, url: 'Node.jsp?id=1'} : a);
  return {context, controls, loads};
}

// 1. Every object vertex (ZTF and LSST) gets exactly one safe "Tag" action.
for (const id of ['ZTF20aachcvz', '313761043604045880', '20892028060578637']) {
  const {context, controls, loads} = graphContext();
  context.show([{type: 'vertex', id: 7, label: 'object', element: [{key: 'lbl', value: 'object'},
    {key: 'objectId', value: id}, {key: 'title', value: id}, {key: 'group', value: 'g'}]}]);
  context.network.listeners.click({nodes: [7], edges: []});
  const tags = controls.commands.children.filter(c => c.tag === 'a' && c.textContent === 'Tag');
  assert.equal(tags.length, 1, id);
  assert.equal(tags[0].href, '#');
  assert.notEqual(tags[0].className, 'ws-external-link', 'Tag stays inside the application');
  tags[0].listeners.click({preventDefault() {}});
  assert.deepEqual(loads, [['tag', 'tag']], 'the click only routes the static sentinel');
}
// Non-object vertices have no Tag action.
{
  const {context, controls} = graphContext();
  context.stylesheet.nodes.OCol = {properties: {}, graphics: {...context.stylesheet.nodes.object.graphics}, actions: []};
  context.show([{type: 'vertex', id: 8, label: 'OCol', element: [{key: 'lbl', value: 'OCol'}, {key: 'title', value: 'x'}]}]);
  context.network.listeners.click({nodes: [8], edges: []});
  assert.equal(controls.commands.children.some(c => c.textContent === 'Tag'), false);
}
// 2. The action whitelist accepts only the exact sentinel for target "tag".
{
  const {context} = graphContext();
  assert.deepEqual({...context.safeGraphAction({name: 'Tag', target: 'tag'}, 'tag')}, {name: 'Tag', url: 'tag', target: 'tag'});
  for (const bad of ['gr.registerOCol(1)', 'Tag', 'tag?x=1', 'Node.jsp', '../tag', ''])
    assert.equal(context.safeGraphAction({name: 'Tag', target: 'tag'}, bad), null, bad);
}
// 3. workspace.js routes the tag pane to tagGraphState and never to the legacy loader.
{
  const calls = [];
  const ws = read('workspace.js');
  assert.match(ws, /if \(pane === 'tag'\) \{\s*return url === 'tag' && window\.tagGraphState \? window\.tagGraphState\.run\(\) : false;/);
  assert.ok(ws.indexOf("pane === 'tag'") < ws.indexOf('legacyLoadPane.call('), 'tag is handled before any legacy call');
}

// 4. tag-graph.js behaviour.
function tagContext({confirmAnswer = true, origin = '//134.158.243.144:24445', id = '313761043604045880',
                     responder} = {}) {
  const messages = [];
  const requests = [];
  const selected = {id: 5};
  const context = vm.createContext({
    console, encodeURIComponent, setTimeout, clearTimeout, AbortController,
    selectedNode: selected, nodes: [selected],
    skyGraphState: {getId: vertex => vertex === 5 ? id : null},
    graphTableServerForVertex: vertex => vertex === 5 ? origin : null,
    feedbackLine: message => messages.push(message),
    confirm: question => { messages.push('CONFIRM ' + question); return confirmAnswer; },
    fetch: async (target) => {
      requests.push(decodeURIComponent(target));
      const reply = responder ? responder(requests.length, requests.at(-1)) :
        (requests.length === 1 ? {status: {code: 200}, result: {data: {'@type': 'g:List', '@value': []}}}
          : {status: {code: 200}, result: {data: {'@type': 'g:List', '@value': [{'@type': 'g:Int64', '@value': 1}]}}});
      return {ok: reply.http === undefined, status: reply.http || 200, json: async () => reply};
    }});
  context.window = context;
  vm.runInContext(read('tag-graph.js'), context, {filename: 'tag-graph.js'});
  return {context, messages, requests, selected};
}
const CLS = 'com.astrolabsoftware.FinkBrowser.Januser.Classifier';
(async () => {
  // LSST object -> LSST classifier argument, the vertex's own server, fixed OCol name, empty instances/weights.
  let t = tagContext();
  assert.equal(await t.context.tagGraphState.run(), true);
  assert.equal(t.requests.length, 2);
  assert.equal(t.requests[0], `//134.158.243.144:24445?gremlin=gr.registerOCol(${CLS}.instance('TAG', 'LSST', ''), 'GraphBrowser', '313761043604045880', 1.0, '', '')`);
  assert.match(t.requests[1], /^\/\/134\.158\.243\.144:24445\?gremlin=g\.V\(\)\.has\('lbl', 'OCol'\)\.has\('classifier', 'TAG'\)\.has\('cls', 'GraphBrowser'\)\.out\('deepcontains'\)\.has\('lbl', 'object'\)\.has\('objectId', '313761043604045880'\)\.count\(\)$/);
  assert.ok(t.messages.some(m => /CONFIRM Tag object 313761043604045880 as "GraphBrowser".*134\.158\.243\.144:24445/.test(m)));
  assert.ok(t.messages.some(m => /is now tagged "GraphBrowser" \(verified/.test(m)));

  // ZTF object on the ZTF graph server.
  t = tagContext({id: 'ZTF21aaxtctv', origin: '//157.136.253.253:24445'});
  assert.equal(await t.context.tagGraphState.run(), true);
  assert.match(t.requests[0], /^\/\/157\.136\.253\.253:24445\?gremlin=gr\.registerOCol\(.*instance\('TAG', 'ZTF', ''\), 'GraphBrowser', 'ZTF21aaxtctv', 1\.0, '', ''\)$/);

  // Declined confirmation: no request at all.
  t = tagContext({confirmAnswer: false});
  assert.equal(await t.context.tagGraphState.run(), false);
  assert.equal(t.requests.length, 0);

  // Fail closed: unknown origin, hostile or missing object ID, nothing selected.
  for (const bad of [{origin: null}, {origin: 'javascript:alert(1)'}, {origin: '//evil.example/x?y=1'},
                     {id: "1');g.V().drop();//"}, {id: ''}, {id: 'ZTF'}, {id: "ZTF1'x"}]) {
    t = tagContext(bad);
    assert.equal(await t.context.tagGraphState.run(), false, JSON.stringify(bad));
    assert.equal(t.requests.length, 0, JSON.stringify(bad));
  }
  t = tagContext(); t.context.nodes.length = 0; // vertex no longer live
  assert.equal(await t.context.tagGraphState.run(), false);
  assert.equal(t.requests.length, 0);

  // Server error: reported, sanitized, no verification query.
  t = tagContext({responder: () => ({http: 500, message: 'No such property: gr\n\u0000boom'})});
  assert.equal(await t.context.tagGraphState.run(), false);
  assert.equal(t.requests.length, 1);
  assert.ok(t.messages.some(m => /Tag failed for 313761043604045880: No such property: gr\s+boom/.test(m) && /repeating it is safe/.test(m)));

  // Write accepted but not found afterwards: not reported as success.
  t = tagContext({responder: n => n === 1 ? {status: {code: 200}, result: {data: {'@value': []}}}
    : {status: {code: 200}, result: {data: {'@value': [{'@value': 0}]}}}});
  assert.equal(await t.context.tagGraphState.run(), false);
  assert.ok(t.messages.some(m => /not found in "GraphBrowser" afterwards/.test(m)));

  // One request at a time.
  let release;
  t = tagContext({responder: n => n === 1 ? new Promise(r => { release = r; }) : null});
  const original = t.context.fetch;
  t.context.fetch = (target) => { t.requests.push(decodeURIComponent(target));
    return t.requests.length === 1 ? new Promise(r => { release = () => r({ok: true, status: 200,
      json: async () => ({status: {code: 200}, result: {data: {'@value': [{'@value': 1}]}}})}); })
      : Promise.resolve({ok: true, status: 200, json: async () => ({status: {code: 200}, result: {data: {'@value': [{'@value': 1}]}}})}); };
  const first = t.context.tagGraphState.run();
  await new Promise(r => setImmediate(r));
  assert.equal(await t.context.tagGraphState.run(), false);
  assert.ok(t.messages.some(m => /still running/.test(m)));
  release();
  assert.equal(await first, true);
  void original;

  // The page loads the script and the cache-busted action list.
  assert.match(read('GraphView.jsp'), /tag-graph\.js\?v=tag-1/);
  console.log('Tag action: sentinel action, fixed validated registerOCol on the vertex origin server, fail-closed checks');
})().catch(error => { console.error(error); process.exit(1); });

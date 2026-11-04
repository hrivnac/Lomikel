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

// 4. tag-graph.js behaviour: one POST to the WAR's TagObject.jsp, never a write to a Gremlin server.
function tagContext({confirmAnswer = true, id = '313761043604045880', responder} = {}) {
  const messages = [];
  const requests = [];
  const selected = {id: 5};
  const context = vm.createContext({
    console, setTimeout, clearTimeout, AbortController,
    selectedNode: selected, nodes: [selected],
    skyGraphState: {getId: vertex => vertex === 5 ? id : null},
    feedbackLine: message => messages.push(message),
    confirm: question => { messages.push('CONFIRM ' + question); return confirmAnswer; },
    fetch: async (target, options) => {
      requests.push({target, options});
      const custom = responder ? await responder(requests.length) : undefined;
      if (custom instanceof Error) throw custom;
      const reply = custom !== undefined ? custom : {ok: true, survey: id.startsWith('ZTF') ? 'ZTF' : 'LSST',
        objectId: id, tag: 'GraphBrowser', alreadyTagged: false, members: 1};
      return {ok: reply.http === undefined, status: reply.http || 200, json: async () => reply};
    }});
  context.window = context;
  vm.runInContext(read('tag-graph.js'), context, {filename: 'tag-graph.js'});
  return {context, messages, requests, selected};
}
(async () => {
  // LSST and ZTF objects: one same-origin POST with the ID only, behind a confirmation.
  for (const [id, survey] of [['313761043604045880', 'LSST'], ['20892028060578637', 'LSST'], ['ZTF21aaxtctv', 'ZTF']]) {
    const t = tagContext({id});
    assert.equal(await t.context.tagGraphState.run(), true, id);
    assert.equal(t.requests.length, 1);
    const {target, options} = t.requests[0];
    assert.equal(target, 'TagObject.jsp', 'relative URL: the WAR that served the page');
    assert.equal(options.method, 'POST');
    assert.equal(options.headers['X-Fink-Tag'], '1');
    assert.equal(options.headers['Content-Type'], 'application/json');
    assert.equal(options.credentials, 'same-origin');
    assert.deepEqual(JSON.parse(options.body), {objectId: id}, 'only the validated ID is sent');
    assert.ok(t.messages.some(m => m === `CONFIRM Tag object ${id} as "GraphBrowser" in the ${survey} graph database?`));
    assert.ok(t.messages.some(m => /is now tagged "GraphBrowser" in the .* graph \(verified\)/.test(m)));
  }
  // Already tagged is reported as such.
  let t = tagContext({responder: () => ({ok: true, survey: 'LSST', objectId: '313761043604045880', tag: 'GraphBrowser', alreadyTagged: true, members: 1})});
  assert.equal(await t.context.tagGraphState.run(), true);
  assert.ok(t.messages.some(m => /was already tagged "GraphBrowser"/.test(m)));

  // Declined confirmation: no request.
  t = tagContext({confirmAnswer: false});
  assert.equal(await t.context.tagGraphState.run(), false);
  assert.equal(t.requests.length, 0);

  // Hostile or missing object IDs and a non-live vertex never reach the network.
  for (const bad of ["1');g.V().drop();//", '', 'ZTF', "ZTF1'x", '12ab', '1'.repeat(30)]) {
    t = tagContext({id: bad});
    assert.equal(await t.context.tagGraphState.run(), false, bad);
    assert.equal(t.requests.length, 0, bad);
  }
  t = tagContext(); t.context.nodes.length = 0;
  assert.equal(await t.context.tagGraphState.run(), false);
  assert.equal(t.requests.length, 0);

  // Server-side refusals are shown (sanitized); a mismatching reply is not trusted.
  for (const [reply, pattern] of [[{http: 404, error: 'Object is not in the LSST graph'}, /Tag failed for 313761043604045880: Object is not in the LSST graph.*repeating it is safe/],
                                  [{http: 503, error: 'Writable LSST graph is not available'}, /Writable LSST graph is not available/],
                                  [{http: 502, error: 'bad\n\u0000text'}, /bad\s+text/],
                                  [{http: 500}, /HTTP 500/],
                                  [{ok: true, objectId: '999', tag: 'GraphBrowser', survey: 'LSST'}, /Unexpected tag response/],
                                  [{ok: true, objectId: '313761043604045880', tag: 'Other', survey: 'LSST'}, /Unexpected tag response/]]) {
    t = tagContext({responder: () => reply});
    assert.equal(await t.context.tagGraphState.run(), false);
    assert.ok(t.messages.some(m => pattern.test(m)), JSON.stringify(reply) + ' => ' + t.messages.join(' | '));
  }
  t = tagContext({responder: () => new TypeError('Failed to fetch')});
  assert.equal(await t.context.tagGraphState.run(), false);
  assert.ok(t.messages.some(m => /Failed to fetch.*repeating it is safe/.test(m)));

  // One request at a time.
  let release;
  t = tagContext({responder: () => new Promise(resolve => { release = () => resolve(undefined); }).then(() => undefined)});
  const first = t.context.tagGraphState.run();
  await new Promise(r => setImmediate(r));
  assert.equal(await t.context.tagGraphState.run(), false);
  assert.ok(t.messages.some(m => /still running/.test(m)));
  release();
  assert.equal(await first, true);

  // The client never talks to a Gremlin server or builds scripts any more.
  const client = read('tag-graph.js');
  assert.doesNotMatch(client, /graphTableServerForVertex|\?gremlin=/);
  assert.match(read('GraphView.jsp'), /tag-graph\.js\?v=tag-\d/);
  console.log('Tag action: sentinel action, one validated POST to TagObject.jsp, fail-closed checks');
})().catch(error => { console.error(error); process.exit(1); });

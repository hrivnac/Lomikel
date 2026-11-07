'use strict';
// "Tag" selection action: sentinel action on object vertices, safe routing through GraphView/workspace,
// and tag-graph.js asking for the tag details and posting them to the WAR's TagObject.jsp.
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

// 4. tag-graph.js behaviour: GET the configured tag names, ask in a dialog, then one POST to the WAR's
//    TagObject.jsp with the dialog values; never a write to a Gremlin server.
// Tag names change: take them from TagNames.txt (same rules as TagObject.jsp), never hard-code them.
const TAG_NAMES = [...new Set(fs.readFileSync(path.resolve(__dirname, '../../java/com/astrolabsoftware/FinkBrowser/Januser/TagNames.txt'), 'utf8')
  .split(/\r?\n/).map(line => line.replace(/#.*/, '').trim()).filter(name => /^[A-Za-z0-9_.-]{1,64}$/.test(name)))];
assert.ok(TAG_NAMES.length >= 1, 'TagNames.txt must list at least one valid tag name');
const N1 = TAG_NAMES[0], N2 = TAG_NAMES[TAG_NAMES.length - 1];
function domElement(tag) {
  const node = {tag, children: [], attributes: {}, listeners: {}, textContent: '', value: '', parentNode: null, open: false,
    appendChild(child) { child.parentNode = this; this.children.push(child); return child; },
    removeChild(child) { this.children = this.children.filter(c => c !== child); child.parentNode = null; },
    setAttribute(k, v) { this.attributes[k] = String(v); }, addEventListener(k, fn) { this.listeners[k] = fn; },
    focus() {}, showModal() { this.open = true; }, close() { this.open = false; if (this.listeners.close) this.listeners.close(); }};
  return node;
}
function find(node, test) {
  if (test(node)) return node;
  for (const child of node.children || []) { const hit = find(child, test); if (hit) return hit; }
  return null;
}
function tagContext({id = '313761043604045880', names = TAG_NAMES, fill, responder, getResponder} = {}) {
  const messages = [];
  const requests = [];
  const dialogs = [];
  const selected = {id: 5};
  const body = domElement('body');
  const context = vm.createContext({
    console, setTimeout, clearTimeout, AbortController,
    selectedNode: selected, nodes: [selected],
    skyGraphState: {getId: vertex => vertex === 5 ? id : null},
    feedbackLine: message => messages.push(message),
    confirm: () => { throw new Error('window.confirm must not be used'); },
    document: {createElement: domElement, body},
    fetch: async (target, options) => {
      requests.push({target, options});
      if (options.method === 'GET') {
        const custom = getResponder ? await getResponder() : undefined;
        if (custom instanceof Error) throw custom;
        const reply = custom !== undefined ? custom : {tags: names};
        return {ok: reply.http === undefined, status: reply.http || 200, json: async () => reply};
      }
      const sent = JSON.parse(options.body);
      const custom = responder ? await responder(sent) : undefined;
      if (custom instanceof Error) throw custom;
      const reply = custom !== undefined ? custom : {ok: true, survey: id.startsWith('ZTF') ? 'ZTF' : 'LSST',
        objectId: sent.objectId, tag: sent.tag, value: sent.value, entries: 1, ocolCreated: false};
      return {ok: reply.http === undefined, status: reply.http || 200, json: async () => reply};
    }});
  context.window = context;
  vm.runInContext(read('tag-graph.js'), context, {filename: 'tag-graph.js'});
  // Fill and submit (or cancel) the dialog as soon as it is shown.
  const realAppend = body.appendChild.bind(body);
  body.appendChild = child => {
    realAppend(child);
    if (child.tag === 'dialog') {
      dialogs.push(child);
      Promise.resolve().then(() => {
        const field = name => find(child, n => n.attributes.name === name);
        const action = fill ? fill(field, child) : 'submit';
        if (action === 'cancel') find(child, n => n.textContent === 'Cancel').listeners.click();
        else if (action === 'escape') child.listeners.cancel();
        else if (action !== 'leave') find(child, n => n.tag === 'form').listeners.submit({preventDefault() {}});
      });
    }
    return child;
  };
  return {context, messages, requests, dialogs, body};
}
(async () => {
  // Dialog content: read-only object id, configured tag names, value 1.0, empty author/annotation/ref.
  {
    let seen;
    const t = tagContext({fill: (field, dialog) => { seen = {
      id: field('objectId'), tag: field('tag'), value: field('value').value,
      options: field('tag').children.map(o => o.textContent), author: field('author').value,
      annotation: field('annotation').value, ref: field('ref').value, open: dialog.open}; return 'cancel'; }});
    assert.equal(await t.context.tagGraphState.run(), false);
    await new Promise(r => setImmediate(r));
    assert.equal(seen.id.value, '313761043604045880');
    assert.equal(seen.id.attributes.readonly, 'readonly', 'object id is not modifiable');
    assert.deepEqual(seen.options, TAG_NAMES);
    assert.equal(seen.tag.value, N1);
    assert.equal(seen.value, '1.0');
    assert.deepEqual([seen.author, seen.annotation, seen.ref], ['', '', '']);
    assert.equal(seen.open, true, 'shown as a modal dialog');
    assert.equal(t.requests.length, 1, 'only the GET of the tag names; nothing posted');
    assert.equal(t.requests[0].target, 'TagObject.jsp');
    assert.equal(t.requests[0].options.method, 'GET');
    assert.ok(t.messages.some(m => /cancelled; nothing was written/.test(m)));
    assert.equal(t.body.children.length, 0, 'the dialog is removed after closing');
  }
  // Escape closes without writing.
  {
    const t = tagContext({fill: () => 'escape'});
    assert.equal(await t.context.tagGraphState.run(), false);
    assert.equal(t.requests.filter(r => r.options.method === 'POST').length, 0);
  }
  // Submitted values reach TagObject.jsp exactly (LSST and ZTF objects).
  for (const [id, survey] of [['313761043604045880', 'LSST'], ['20892028060578637', 'LSST'], ['ZTF17aaabadq', 'ZTF']]) {
    const t = tagContext({id, fill: field => {
      field('tag').value = N1; field('value').value = '0.5'; field('author').value = 'Julius.Hrivnac@cern.ch';
      field('annotation').value = 'muj pokus'; field('ref').value = 'blablabla'; }});
    assert.equal(await t.context.tagGraphState.run(), true, id);
    const posts = t.requests.filter(r => r.options.method === 'POST');
    assert.equal(posts.length, 1);
    const {target, options} = posts[0];
    assert.equal(target, 'TagObject.jsp', 'relative URL: the WAR that served the page');
    assert.equal(options.headers['X-Fink-Tag'], '1');
    assert.equal(options.headers['Content-Type'], 'application/json');
    assert.equal(options.credentials, 'same-origin');
    assert.deepEqual(JSON.parse(options.body), {objectId: id, tag: N1, value: 0.5,
      author: 'Julius.Hrivnac@cern.ch', annotation: 'muj pokus', ref: 'blablabla'});
    assert.ok(t.messages.some(m => m.includes(`is now tagged "${N1}" (0.5) in the ${survey} graph (verified, 1 entry`)));
  }
  // The second configured name and defaults.
  {
    const t = tagContext({fill: field => { field('tag').value = N2; }});
    assert.equal(await t.context.tagGraphState.run(), true);
    const sent = JSON.parse(t.requests.find(r => r.options.method === 'POST').options.body);
    assert.deepEqual(sent, {objectId: '313761043604045880', tag: N2, value: 1, author: '', annotation: '', ref: ''});
  }
  // Invalid dialog values keep the dialog open with a message and post nothing.
  for (const [setup, pattern] of [[f => { f('value').value = '-1'; }, /number >= 0/],
                                  [f => { f('value').value = ''; }, /number >= 0/],
                                  [f => { f('value').value = 'abc'; }, /number >= 0/],
                                  [f => { f('tag').value = 'NotConfigured' + TAG_NAMES.join(''); }, /configured tag names/],
                                  [f => { f('author').value = 'x'.repeat(201); }, /author is longer than 200/],
                                  [f => { f('annotation').value = 'x'.repeat(2001); }, /annotation is longer than 2000/],
                                  [f => { f('ref').value = 'x'.repeat(1001); }, /ref is longer than 1000/]]) {
    let dialogRef;
    const t = tagContext({fill: (field, dialog) => { setup(field); dialogRef = dialog; return 'submit-then-cancel'; }});
    const running = t.context.tagGraphState.run();
    await new Promise(r => setImmediate(r));
    const problem = find(dialogRef, n => n.attributes.class === 'tag-dialog-error');
    assert.match(problem.textContent, pattern);
    assert.equal(dialogRef.open, true, 'dialog stays open');
    find(dialogRef, n => n.textContent === 'Cancel').listeners.click();
    assert.equal(await running, false);
    assert.equal(t.requests.filter(r => r.options.method === 'POST').length, 0);
  }
  // Control characters are flattened before sending.
  {
    const t = tagContext({fill: f => { f('annotation').value = 'a\nb\u0000c'; }});
    assert.equal(await t.context.tagGraphState.run(), true);
    assert.equal(JSON.parse(t.requests.find(r => r.options.method === 'POST').options.body).annotation, 'a b c');
  }
  // Tag names unavailable: no dialog, nothing written.
  for (const [getReply, pattern] of [[{http: 503, error: 'Tag names are not available'}, /Tag names are not available/],
                                     [{tags: []}, /no tag names are configured/],
                                     [{tags: ['bad name', 5]}, /no tag names are configured/],
                                     [new TypeError('Failed to fetch'), /Failed to fetch/]]) {
    const t = tagContext({getResponder: () => getReply});
    assert.equal(await t.context.tagGraphState.run(), false);
    assert.equal(t.dialogs.length, 0);
    assert.ok(t.messages.some(m => pattern.test(m) && /nothing was written/.test(m)), t.messages.join(' | '));
  }
  // Hostile or missing object IDs and a non-live vertex never reach the network.
  for (const bad of ["1');g.V().drop();//", '', 'ZTF', "ZTF1'x", '12ab', '1'.repeat(30)]) {
    const t = tagContext({id: bad});
    assert.equal(await t.context.tagGraphState.run(), false, bad);
    assert.equal(t.requests.length, 0, bad);
  }
  {
    const t = tagContext(); t.context.nodes.length = 0;
    assert.equal(await t.context.tagGraphState.run(), false);
    assert.equal(t.requests.length, 0);
  }
  // Server-side refusals are shown (sanitized); a mismatching reply is not trusted.
  for (const [reply, pattern] of [[{http: 404, error: 'Object is not in the LSST graph'}, /Tag failed for 313761043604045880: Object is not in the LSST graph; nothing was written/],
                                  [{http: 503, error: 'Writable LSST graph is not available'}, /Writable LSST graph is not available; nothing was written/],
                                  [{http: 400, error: 'Unknown tag name'}, /Unknown tag name; nothing was written/],
                                  [{http: 403, error: 'Cross-origin request refused'}, /Cross-origin request refused; nothing was written/],
                                  [{http: 502, error: 'bad\n\u0000text'}, /bad\s+text\. The tag may or may not have been written/],
                                  [{http: 500}, /HTTP 500\. The tag may or may not have been written/],
                                  [{ok: true, objectId: '999', tag: N1, survey: 'LSST'}, /Unexpected tag response/],
                                  [{ok: true, objectId: '313761043604045880', tag: N1 + 'X', survey: 'LSST'}, /Unexpected tag response/]]) {
    const t = tagContext({responder: () => reply});
    assert.equal(await t.context.tagGraphState.run(), false);
    assert.ok(t.messages.some(m => pattern.test(m)), JSON.stringify(reply) + ' => ' + t.messages.join(' | '));
  }
  // Answers relayed from the owner site's WAR name the writing server; 421/504 are explained.
  {
    const t = tagContext({id: 'ZTF17aaabadq', responder: sent => ({ok: true, objectId: sent.objectId, tag: sent.tag, survey: 'ZTF', entries: 2, site: 'IJCLab'})});
    assert.equal(await t.context.tagGraphState.run(), true);
    assert.ok(t.messages.some(m => m.includes(`is now tagged "${N1}" (1) in the ZTF graph by the IJCLab server (verified, 2 entries`)), t.messages.join(' | '));
    const odd = tagContext({responder: sent => ({ok: true, objectId: sent.objectId, tag: sent.tag, survey: 'LSST', entries: 1, site: '<b>x</b>'})});
    assert.equal(await odd.context.tagGraphState.run(), true);
    assert.ok(odd.messages.some(m => /in the LSST graph \(verified, 1 entry/.test(m)), 'an unusual site name is not shown');
  }
  for (const [reply, pattern] of [[{http: 421, error: 'This server (CC) does not write ZTF tags'}, /does not write ZTF tags; nothing was written/],
                                  [{http: 504, error: 'The ZTF tagging service at IJCLab did not answer in time'}, /did not answer in time\. The tag may or may not have been written/],
                                  [{http: 503, error: 'The ZTF tagging service at IJCLab (157.136.253.253) is not reachable from this server'}, /is not reachable from this server; nothing was written/]]) {
    const t = tagContext({responder: () => reply});
    assert.equal(await t.context.tagGraphState.run(), false);
    assert.ok(t.messages.some(m => pattern.test(m)), JSON.stringify(reply) + ' => ' + t.messages.join(' | '));
  }
  // A network failure or a timeout after sending leaves the outcome unknown.
  {
    const t = tagContext({responder: () => new TypeError('Failed to fetch')});
    assert.equal(await t.context.tagGraphState.run(), false);
    assert.ok(t.messages.some(m => /Failed to fetch\. The tag may or may not have been written/.test(m)), t.messages.join(' | '));
  }
  // One request (or open dialog) at a time.
  {
    let dialogRef;
    const t = tagContext({fill: (f, d) => { dialogRef = d; return 'leave'; }});
    const first = t.context.tagGraphState.run();
    await new Promise(r => setImmediate(r));
    assert.equal(await t.context.tagGraphState.run(), false, 'no second dialog while one is open');
    assert.ok(t.messages.some(m => /still running/.test(m)));
    find(dialogRef, n => n.tag === 'form').listeners.submit({preventDefault() {}});
    assert.equal(await first, true);
  }
  {
    let release;
    const t = tagContext({responder: () => new Promise(resolve => { release = () => resolve(undefined); })});
    const first = t.context.tagGraphState.run();
    for (let i = 0; i < 5; i++) await new Promise(r => setImmediate(r));
    assert.equal(await t.context.tagGraphState.run(), false);
    release();
    assert.equal(await first, true);
  }

  // The Ready/Working box shows Working for the names request and the POST, never while the dialog waits.
  {
    const t = tagContext();
    const tracked = [];
    let pending = 0;
    t.context.wsBusy = {during: work => {
      pending++;
      tracked.push({method: t.requests.at(-1) && t.requests.at(-1).options.method,
                    dialogOpen: t.dialogs.some(d => d.open)});
      const done = () => { pending--; };
      work.then(done, done);
      return work;
    }};
    assert.equal(await t.context.tagGraphState.run(), true);
    await new Promise(r => setImmediate(r));
    assert.deepEqual(tracked, [{method: 'GET', dialogOpen: false}, {method: 'POST', dialogOpen: false}]);
    assert.equal(pending, 0, 'both requests finished');
  }
  // The client never talks to a Gremlin server.
  const client = read('tag-graph.js');
  assert.doesNotMatch(client, /graphTableServerForVertex|\?gremlin=|window\.confirm/);
  assert.match(read('GraphView.jsp'), /tag-graph\.js\?v=tag-dialog-\d/);
  // The dialog styles live in workspace.css; any later version still contains them.
  assert.match(read('index.jsp'), /workspace\.css\?v=(?:tag-dialog|busy)-\d/);
  assert.match(read('workspace.css'), /\.tag-dialog \{/);
  console.log('Tag action: sentinel action, dialog with configured tag names and value/author/annotation/ref, one validated POST to TagObject.jsp');
})().catch(error => { console.error(error); process.exit(1); });

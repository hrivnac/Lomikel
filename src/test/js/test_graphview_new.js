'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const base = path.join(__dirname, '../../webservice');
const read = name => fs.readFileSync(path.join(base, name), 'utf8');

function route(value) {
  const calls = [];
  const window = {graphViewVariant: value, loadPane(...args) { calls.push(args); },
    addEventListener() {}};
  const w2ui = {layout: {html() {}}, layoutLeft: {load(pane,url) {calls.push([pane,url]);}},
    layoutMain: {load() {}}};
  window.w2ui = w2ui;
  const document = {baseURI:'http://localhost/app/', getElementById(id) {
    if (['restore-layout','reset-workspace','version-button'].includes(id)) return {addEventListener() {}};
    return null;
  }};
  function $(arg) {
    if (typeof arg === 'function') {arg(); return;}
    return {w2layout() {}};
  }
  const context = {window, w2ui, loadPane: (...args) => window.loadPane(...args),
    showTab() {}, $, document, URL, URLSearchParams};
  vm.runInNewContext(read('workspace.js'), context);
  vm.runInNewContext(read('profiles/null.js'), context); // no bootstrap or network
  window.reset();
  return calls.filter(args => args[0] === 'graph' || args[0] === 'bottom')
    .map(args => args[1]);
}

function testRoute() {
  const index = read('index.jsp');
  assert.match(index, /"new"\.equals\(request\.getParameter\("graphview"\)\)/);
  assert.match(index, /window\.graphViewVariant\s*=/);
  assert.deepEqual(route('new'), ['GraphViewNew.jsp','GraphViewNew.jsp']);
  assert.deepEqual(route('legacy'), ['GraphView.jsp','GraphView.jsp']);
  assert.deepEqual(route(undefined), ['GraphView.jsp','GraphView.jsp']);
  for (const profile of ['local', 'ijclab']) {
    const script = read('profiles/' + profile + '.js');
    assert.match(script, /graphViewVariant\s*===\s*'new'/);
    assert.doesNotMatch(script, /GraphViewNew\.js/, 'the fragment owns the new controller');
    assert.match(script, /GraphView\.js/);
  }
  assert.match(read('GraphViewNew.jsp'), /GraphViewNew\.js/);
  assert.match(read('GraphViewNew.jsp'), /GraphViewNew\.css/);
  assert.match(read('GraphViewNew.css'), /button\.button-describe/);
  assert.match(read('GraphViewNew.css'), /button\.button-remove/);
  assert.doesNotMatch(read('GraphView.jsp'), /GraphViewNew/);
  assert.ok(fs.existsSync(path.join(base, 'profiles/null.js')));
  assert.match(read('GraphViewNew.jsp'), /id="vis"/);
  assert.match(read('GraphViewNew.jsp'), /id="clusterize"/);
  // A profile=null page gets its controller from the fragment, without profile bootstrap.
  assert.doesNotMatch(read('profiles/null.js'), /GraphView\.js|GraphViewNew\.js/);
}

testRoute();
console.log('GraphViewNew route tests passed');

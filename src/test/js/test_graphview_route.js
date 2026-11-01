'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const base = path.join(__dirname, '../../webservice');
const read = name => fs.readFileSync(path.join(base, name), 'utf8');

function route(value) {
  const calls = [];
  const window = {loadPane(...args) { calls.push(args); },
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
  assert.doesNotMatch(index, /graphViewVariant|request\.getParameter\("graphview"\)/);
  assert.doesNotMatch(read('workspace.js'), /graphViewVariant|GraphViewNew/);
  for (const value of ['new', 'legacy', undefined])
    assert.deepEqual(route(value), ['GraphView.jsp','GraphView.jsp']);
  for (const profile of ['local', 'ijclab', 'cc']) {
    const script = read('profiles/' + profile + '.js');
    assert.doesNotMatch(script, /graphViewVariant|\$\.getScript\(["']GraphView\.js/);
  }
  for (const ext of ['jsp','js','css'])
    assert.equal(fs.existsSync(path.join(base, 'GraphViewNew.' + ext)), false);
  assert.match(read('GraphView.jsp'), /GraphView\.js/);
  assert.match(read('GraphView.jsp'), /GraphView\.css/);
  assert.match(read('GraphView.css'), /button\.button-describe/);
  assert.match(read('GraphView.jsp'), /id="vis"/);
  assert.match(read('GraphView.jsp'), /id="clusterize"/);
  assert.doesNotMatch(read('profiles/null.js'), /GraphView\.js/);
}
testRoute();
console.log('Canonical GraphView route tests passed');

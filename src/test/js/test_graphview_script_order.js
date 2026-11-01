'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const base = path.join(__dirname, '../../webservice');
const read = name => fs.readFileSync(path.join(base, name), 'utf8');
const fragment = read('GraphView.jsp');
const scripts = Array.from(fragment.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/g), match => ({
  src: /\bsrc="([^"]+)"/.exec(match[1])?.[1], body: match[2]
}));
assert.equal(scripts.filter(script => script.src?.split('?')[0] === 'GraphView.js').length, 1);

function scenario(profile, fragmentFirst) {
  let graph = {id: 'first'};
  const listeners = new Map();
  const calls = [];
  const loads = [];
  const document = {
    baseURI: 'http://localhost/app/',
    getElementById(id) { return id === 'graph' ? graph : null; },
    addEventListener() {}
  };
  const context = vm.createContext({document, Map, Set, URL, console,
    addEventListener(name, callback) {
      const handlers = listeners.get(name) || new Set();
      handlers.add(callback); listeners.set(name, handlers);
    },
    removeEventListener(name, callback) { listeners.get(name)?.delete(callback); },
    dispatchEvent(event) { for (const callback of Array.from(listeners.get(event.type) || [])) callback(event); },
    Event: class { constructor(type) { this.type = type; } },
    loadPane(...args) { calls.push(['loadPane', ...args]); },
    $: {getScript(src, callback) { loads.push(src); if (callback) callback(); }}
  });
  context.window = context;

  function runFragment() {
    let controllerShow;
    for (const script of scripts) {
      if (script.src) {
        const name = script.src.split('?')[0];
        if (!['GraphView.js', 'skyview-graph.js', 'lightcurve-graph.js'].includes(name)) continue;
        vm.runInContext(read(name), context, {filename:name});
        if (name === 'GraphView.js') {
          controllerShow = context.show;
          context.bootstrap = (...args) => {
            assert.notEqual(context.show, controllerShow, 'sky wrapper must precede bootstrap');
            assert.ok(context.skyGraphState && context.lightcurveGraphState, 'both wrappers must precede bootstrap');
            calls.push(['bootstrap', ...args]);
          };
        }
      } else if (script.body.trim()) vm.runInContext(script.body, context, {filename:'GraphView.jsp inline'});
    }
    assert.notEqual(context.show, controllerShow, 'fragment installed sky wrapper');
  }
  function runProfile() { vm.runInContext(read('profiles/' + profile + '.js'), context); }
  if (fragmentFirst) { runFragment(); runProfile(); }
  else {
    context.graphViewReady = {id:'stale-fragment'};
    runProfile();
    assert.equal(calls.filter(call => call[0] === 'bootstrap').length, 0,
      'profile may not start the old or incomplete controller');
    runFragment();
  }
  assert.deepEqual(loads, [], 'profile never requests a second controller');
  assert.deepEqual(calls.filter(call => call[0] === 'bootstrap').map(call => call.slice(1)),
    [['text', profile === 'local' ? 1 : 0]]);
  context.nodes.push({id: 42});
  assert.equal(context.nodes.length, 1, 'profile load did not reset the controller arrays');
  context.disposeGraphView();
  graph = {id:'after-reset'};
  runFragment();
  assert.deepEqual(loads, [], 'reset still gets its controller solely from the fragment');
  assert.equal(context.nodes.length, 0, 'reset fragment has fresh state');
  assert.equal(calls.filter(call => call[0] === 'bootstrap').length, 1,
    'the initial profile bootstrap does not repeat on reset');
  runProfile();
  assert.equal(calls.filter(call => call[0] === 'bootstrap').length, 1,
    'a repeated profile script cannot bootstrap twice');
}
for (const profile of ['local','ijclab','cc']) {
  scenario(profile, true);
  scenario(profile, false);

}
console.log('Canonical GraphView script-order tests passed');

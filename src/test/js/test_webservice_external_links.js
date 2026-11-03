'use strict';
// "Selection actions": the three links that leave for another window/service are real,
// correctly filled, absolute links and carry the external-link arrow.
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
function setup() {
  const controls = Object.fromEntries(['vis','filter','add2graph','clusterize','zoom','stabilize','commands',
    'physics','glayout','glayout_direction','glayout_method','expandTo','expandFrom','removeOld','select']
    .map(id => [id, element(id)]));
  const context = vm.createContext({
    document: {getElementById: id => controls[id] || null, createElement: element,
      createTextNode: text => ({tag: '#text', textContent: text})},
    vis: {Network: class {constructor() {this.listeners = {};} on(n, fn) {this.listeners[n] = fn;}
      once() {} isCluster() {return false;} cluster() {} setData() {} setOptions() {} destroy() {}}},
    loadPane() {}, options4Graph: {physics: {enabled: true}}, console, URL,
    URLSearchParams, location: {href: 'https://fink.example/FinkBrowser/index.jsp?x=1#h'}});
  context.window = context;
  for (const file of ['StylesheetDefault.js', 'Stylesheet.js', 'SurveyStylesheet.js', 'GraphView.js'])
    vm.runInContext(read(file), context, {filename: file});
  // Keep the REAL action list; only replace graph-server (Gremlin) lookups, which need a live server.
  const node = context.stylesheet.nodes.object;
  node.properties = {};
  Object.assign(node.graphics, {label: 'lbl', title: 'title', subtitle: 'subtitle', group: 'group'});
  node.actions = node.actions.map(a => a.url && a.url.gremlin ? {...a, url: 'Node.jsp?id=1'} : a);
  return {context, controls};
}
function selectObject(objectId) {
  const {context, controls} = setup();
  context.show([{type: 'vertex', id: 1, label: 'object', element: [{key: 'lbl', value: 'object'},
    {key: 'objectId', value: objectId}, {key: 'title', value: objectId}, {key: 'group', value: 'g'}]}]);
  context.network.listeners.click({nodes: [1], edges: []});
  return controls.commands.children.filter(c => c.tag === 'a');
}
const ZTF = 'ZTF20aachcvz', DIA = '313761043604045880', SS = '21163637480633927';
const base = 'https://fink.example/FinkBrowser/';
const external = links => links.filter(l => l.target === '_blank');

for (const [id, survey] of [[ZTF, 'ZTF'], [DIA, 'LSST'], [SS, 'LSST']]) {
  const links = external(selectObject(id));
  const byName = Object.fromEntries(links.map(l => [l.textContent, l]));
  assert.equal(byName['Classification neighborhood'].href,
    `${base}ClassificationView/index.html?survey=${survey}&objectId=${id}`, 'neighborhood menu is filled');
  assert.ok(byName['Fink Portal'].href.startsWith(`https://${survey.toLowerCase()}.fink-portal.org/`));
  if (survey === 'LSST') {
    assert.equal(byName['LSST Light Curve Visualizer'].href,
      `${base}LightCurveView/index.html?objectId=${id}`, 'visualizer menu is filled (DIA and SS)');
    assert.equal(links.length, 3);
  } else {
    assert.equal(byName['LSST Light Curve Visualizer'], undefined, 'no LSST visualizer for ZTF objects');
    assert.equal(links.length, 2);
  }
  for (const link of links) {
    assert.equal(link.className, 'ws-external-link', 'every external link carries the arrow class');
    assert.equal(link.rel, 'noopener noreferrer');
    assert.match(link.attributes['aria-label'], /opens in a new tab/);
  }
}
// Internal actions keep their in-app behavior and get no arrow.
const internal = selectObject(DIA).filter(l => l.target !== '_blank');
assert.ok(internal.length >= 3 && internal.every(l => l.className !== 'ws-external-link'));
// Hostile or unknown IDs never produce a link.
const {context} = setup();
for (const bad of ['', null, '12ab', 'x"><script>', '../../etc', 'ZTF', '1'.repeat(30)]) {
  assert.equal(context.finkNeighborhoodUrl(bad), '', String(bad));
  assert.equal(context.finkLightCurveVisualizerUrl(bad), '', String(bad));
}
assert.equal(context.finkNeighborhoodUrl('ZTF21abc&survey=LSST'), '', 'extra parameters cannot be smuggled');
// The arrow is drawn from CSS at the top-right of the link text.
const css = read('panels.css');
assert.match(css, /a\.ws-external-link::after\s*\{[^}]*content:"\\2197"[^}]*vertical-align:super/);
console.log('Selection actions: neighborhood and visualizer links filled, external-link arrow present');

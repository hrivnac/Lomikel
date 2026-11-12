'use strict';
// vis-network >= 9 renders a string `title` as plain text (innerText), so graph
// tooltips and the Describe heading must be plain text with real line breaks,
// not HTML with a literal "<br/>".
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const web = path.join(__dirname, '../../webservice');
const source = fs.readFileSync(path.join(web, 'GraphView.js'), 'utf8');
function element(tag) {
  return {tag, children: [], attributes: {}, listeners: {}, textContent: '', value: '', checked: false,
    appendChild(child) { this.children.push(child); return child; },
    replaceChildren(...children) { this.children = children; },
    setAttribute(k, v) { this.attributes[k] = v; }, addEventListener(k, fn) { this.listeners[k] = fn; }};
}
function setup() {
  const controls = Object.fromEntries(['vis','filter','add2graph','clusterize','zoom','stabilize',
    'commands','physics','glayout','glayout_direction','glayout_method','expandTo','expandFrom',
    'removeOld','select'].map(id => [id, element(id)]));
  const context = vm.createContext({document: {getElementById: id => controls[id] || null,
    createElement: element, createTextNode: text => ({tag: '#text', textContent: text})},
    vis: {Network: class {constructor() {this.listeners = {};} on(n, fn) {this.listeners[n] = fn;}
      once() {} isCluster() {return false;} cluster() {} setData() {} setOptions() {} destroy() {}}},
    loadPane() {}, options4Graph: {physics: {enabled: true}}, console,
    stylesheet: {nodes: {default: {properties: {}, graphics: {title: 'title', subtitle: 'subtitle',
      label: 'lbl', group: 'group', value: '0', shape: 'dot', borderDashes: [1, 0], borderWidth: '1'},
      actions: []}},
      edges: {default: {properties: {}, graphics: {title: 'title', subtitle: 'subtitle', label: 'lbl',
        group: ' ', value: '1', arrows: ' '}, actions: []}}}});
  context.window = context;
  vm.runInContext(source, context, {filename: 'GraphView.js'});
  return {context, controls};
}

// Vertex and edge tooltips: "label:title" and the subtitle on separate lines, no markup.
{
  const {context} = setup();
  context.show([
    {type: 'vertex', id: 1, label: 'object', element: [{key: 'title', value: 'ZTF21aaxtctv'},
      {key: 'subtitle', value: 'A & B'}, {key: 'lbl', value: 'x'}, {key: 'group', value: 'g'}]},
    {type: 'vertex', id: 2, label: 'object', element: [{key: 'title', value: 'ZTF19acnqcqe'},
      {key: 'subtitle', value: ' '}, {key: 'lbl', value: 'y'}, {key: 'group', value: 'g'}]},
    {type: 'edge', id: 'e-1', label: 'overlaps', outVid: 1, inVid: 2,
      element: [{key: 'title', value: 'pair'}, {key: 'subtitle', value: '0.5'}]}]);
  assert.equal(context.nodes[0].title, 'object:ZTF21aaxtctv\nA & B');
  assert.equal(context.nodes[1].title, 'object:ZTF19acnqcqe', 'blank subtitle adds no empty line');
  assert.equal(context.edges[0].title, 'overlaps:pair\n0.5');
  for (const item of [...context.nodes, ...context.edges]) {
    assert.doesNotMatch(item.title, /<br|&amp;|&lt;/, `${item.id} tooltip must be plain text`);
  }
}

// Describe popup heading: title lines separated by <br> elements, never a literal "<br/>".
{
  const {context} = setup();
  const popup = {document: {title: '', body: element('body'), createElement: element,
    createTextNode: text => ({tag: '#text', textContent: text})}, focus() {}};
  context.open = () => popup;
  context.callGremlinValues = () => 'name: x';
  context.nodes.push({id: 1, title: 'object:ZTF21aaxtctv\nsubtitle <b>'});
  context.describeNode(1);
  const heading = popup.document.body.children[0];
  assert.equal(heading.tag, 'b');
  assert.deepEqual(heading.children.map(child => child.tag), ['#text', 'br', '#text']);
  assert.equal(heading.children[0].textContent, 'object:ZTF21aaxtctv');
  assert.equal(heading.children[2].textContent, 'subtitle <b>');
  assert.equal(popup.document.title, 'object:ZTF21aaxtctv subtitle <b>');
  assert.doesNotMatch(JSON.stringify(heading), /<br\/>/);
}

// Cluster tooltip is plain text as well.
{
  const {context} = setup();
  context.nodes.push({id: 1, group: 'A & B'});
  const clusters = [];
  context.network = {setData() {}, cluster(options) { clusters.push(options.clusterNodeProperties); }};
  context.clusterByGroups();
  assert.equal(clusters[0].title, 'cluster:A & B');
}

// Show (Node.jsp/Edge.jsp) inserts the title as HTML: it must escape it and keep the line break.
for (const page of ['Node.jsp', 'Edge.jsp']) {
  const jsp = fs.readFileSync(path.join(web, page), 'utf8');
  assert.match(jsp, /escapeGraphHtml\((node|edge)\.title\)\.replace\(\/\\n\/g, '<br\/>'\)/, `${page} escapes the plain-text title`);
}
assert.match(fs.readFileSync(path.join(web, 'GraphView.jsp'), 'utf8'), /GraphView\.js\?v=canonical-6/);
console.log('Graph tooltips, cluster titles and Describe heading are plain text with line breaks');

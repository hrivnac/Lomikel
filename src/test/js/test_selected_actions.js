/* Regression checks for graph context actions and the Fink Portal link. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.join(__dirname, '../../webservice');
const graph = fs.readFileSync(path.join(root, 'GraphView.js'), 'utf8');
const fink = fs.readFileSync(path.join(root, 'Stylesheet.js'), 'utf8');

function fixture() {
  const controls = Object.fromEntries(['vis', 'filter', 'add2graph', 'clusterize', 'zoom', 'stabilize',
    'commands'].map(id => [id, {value:'', checked:false, innerHTML:''}]));
  const ctx = vm.createContext({
    document:{getElementById:id=>controls[id]||null},
    vis:{Network:class {on(){} once(){} isCluster(){return false}}},
    options4Graph:{}, stylesheet:{nodes:{},edges:{}}, console
  });
  ctx.window = ctx;
  vm.runInContext(graph, ctx);
  return {ctx, controls};
}

function testInternalActionsDoNotNavigateToBase() {
  const {ctx} = fixture();
  const graphics = {title:'label', subtitle:'', label:'label', group:'', value:'1', shape:'dot',
    borderDashes:[1,0], borderWidth:'1', arrows:''};
  ctx.stylesheet.nodes.example = {properties:{}, graphics, actions:[
    {name:'Show', url:'Node.jsp?id=7', target:'result'},
    {name:'Table', url:'Nodes.jsp?id=7', target:'table'}
  ]};
  ctx.stylesheet.edges.overlaps = {properties:{}, graphics, actions:[
    {name:'Show', url:'Edge.jsp?id=e7', target:'result'},
    {name:'Table', url:'Edges.jsp?id=e7', target:'table'},
    {name:'Venn', url:'Venn.jsp?id=e7', target:'plot'}
  ]};
  ctx.show([{type:'vertex', id:7, label:'example', element:[]},
    {type:'edge', id:'e7', label:'overlaps', element:[], inVid:7, outVid:7}]);
  for (const action of ctx.nodes[0].actions.matchAll(/<a\b[^>]+>/g)) {
    assert.match(action[0], /return false/, 'Show and Table must cancel the # default navigation');
  }
  for (const action of ctx.edges[0].actions.matchAll(/<a\b[^>]+>/g)) {
    assert.match(action[0], /return false/, 'Edge Show, Table and Venn must not leave the workspace');
  }
  assert.match(ctx.nodes[0].actions, /loadPane\("result", "Node.jsp\?id=7"\)/);
  assert.match(ctx.nodes[0].actions, /loadPane\("table", "Nodes.jsp\?id=7"\)/);
  assert.match(ctx.edges[0].actions, /loadPane\("plot", "Venn.jsp\?id=e7"\)/);
}

function testPortalUsesObjectSurvey() {
  const {ctx} = fixture();
  vm.runInContext(fink, ctx);
  const action = ctx.stylesheet.nodes.object.actions.find(action => action.name === 'Fink Portal');
  assert.ok(action);
  ctx.callGremlinValues = () => 'https://api.ztf.fink-portal.org/123456789';
  for (const [oid, url] of [
    ['123456789', 'https://lsst.fink-portal.org/123456789'],
    ['ZTF20abcdef', 'https://ztf.fink-portal.org/ZTF20abcdef'],
    ['ZTF20 a&b', 'https://ztf.fink-portal.org/ZTF20%20a%26b']
  ]) {
    const value = ctx.stylesheetValue(action.url, 7, new Map([['objectId',oid]]), new Map(), false);
    assert.equal(value, url);
  }
  assert.equal(ctx.stylesheetValue(action.url, 7, new Map([['objectId','not-an-object']]),
    new Map(), false), '', 'unknown IDs must not be linked to a wrong survey');
}

testInternalActionsDoNotNavigateToBase();
testPortalUsesObjectSurvey();
console.log('Selected action tests passed');

'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../../webservice/GraphViewNew.js'), 'utf8');
function element(tag) {
  return {tag, children: [], attributes: {}, listeners: {}, textContent: '', value: '', checked: false,
    appendChild(child) { this.children.push(child); return child; },
    replaceChildren(...children) { this.children = children; },
    setAttribute(k, v) { this.attributes[k] = v; }, addEventListener(k, fn) { this.listeners[k] = fn; }};
}
function setup() {
  const controls = Object.fromEntries(['vis','filter','add2graph','clusterize','zoom','stabilize',
    'commands','physics','glayout','glayout_direction','glayout_method','expandTo','expandFrom',
    'removeOld','select'].map(id => [id,element(id)]));
  const context = vm.createContext({document: {getElementById: id => controls[id] || null,
    createElement: element, createTextNode: text => ({tag: '#text', textContent: text})},
    vis: {Network: class {constructor() {this.listeners={};} on(n, fn) {this.listeners[n]=fn;}
      once() {} isCluster() {return false;} cluster() {} setData() {} setOptions() {} destroy() {}}},
    loadPane() {}, options4Graph: {physics:{enabled:true}}, stylesheet: {nodes:{default:{properties:{},
      graphics:{title:'title',subtitle:'subtitle',label:'lbl',group:'group',value:'0',shape:'dot',
        borderDashes:[1,0],borderWidth:'1'},actions:[]}},edges:{}}, console});
  context.window = context;
  vm.runInContext(source, context, {filename:'GraphViewNew.js'});
  return {context, controls};
}
function testGraphPropertyCannotExecute() {
  const {context} = setup();
  const properties = new Map([['lbl','safe'], ['title','safe'], ['x','";globalThis.compromised=true;//']]);
  assert.equal(context.stylesheetValue('lbl', 1, properties, new Map([['x','";globalThis.compromised=true;//']]), false), 'safe');
  assert.equal(context.compromised, undefined);
  assert.equal(context.stylesheetValue({js:'title.replace("site:", "")'},1,properties,new Map(),false,'site:ok'), 'ok');
  assert.equal(context.stylesheetValue({js:'globalThis.compromised=true'},1,properties,new Map(),false), '');
  assert.equal(context.compromised, undefined, 'stylesheet JS outside supported expressions is inert');
}
function testHostileGraphTextAndActionsRemainInert() {
  const {context, controls} = setup();
  const hostile = '<img src=x onerror=globalThis.compromised=true>';
  context.stylesheet.nodes.default.actions = [
    {name:hostile,url:'javascript:alert(1)',target:'external'},
    {name:hostile,url:'Node.jsp?id=" onclick="alert(1)',target:'result'},
    {name:'safe',url:'Node.jsp?id=1',target:'result'},
    {name:'bad',url:'Node.jsp',target:'" onclick="alert(1)'}];
  context.show([{type:'vertex',id:1,label:hostile,element:[{key:'title',value:hostile},
    {key:'lbl',value:hostile},{key:'subtitle',value:hostile},{key:'group',value:'sample'}]}]);
  assert.ok(context.nodes[0].title.includes('&lt;img'), 'vis tooltip escapes markup');
  context.network.listeners.click({nodes:[1],edges:[]});
  const pane = controls.commands;
  assert.equal(pane.children.some(child => child.tag === 'img'), false);
  assert.equal(pane.children.some(child => child.tag === 'a' && child.href?.startsWith('javascript:')), false);
  const links = pane.children.filter(child => child.tag === 'a');
  assert.equal(links.length, 2);
  assert.equal(links[0].textContent, hostile);
  links[0].listeners.click({preventDefault() {}});
  assert.equal(context.compromised, undefined);
}
function testGraphImageFilenameCannotEscapeImagesDirectory() {
  const {context} = setup();
  context.stylesheet.nodes.site = {properties:{},graphics:{title:'name',subtitle:'',label:'name',
    group:'site',value:'0',shape:'image',borderWidth:'1',
    image:{js:"title.replace('site:', '') + '.png'"}},actions:[]};
  context.show([{type:'vertex',id:1,label:'site',element:[{key:'name',value:'../Node.jsp?id=42&ignore='}]}]);
  assert.equal(context.nodes[0].image, '', 'hostile site label must not trigger authenticated JSP GET');
  context.show([{type:'vertex',id:2,label:'site',element:[{key:'name',value:'IJCLab'}]}]);
  assert.equal(context.nodes[1].image, 'images/IJCLab.png', 'safe site image remains visible');
  context.stylesheet.nodes.site.graphics.image = 'Star-LSST.png';
  context.show([{type:'vertex',id:3,label:'site',element:[{key:'name',value:'safe'}]}]);
  assert.equal(context.nodes[2].image, 'images/Star-LSST.png', 'known star asset remains visible');
}
function testControlsBeforeInitializationAndGroupsAfterRemoval() {
  const {context, controls} = setup();
  for (const fn of ['switchPhysics','switchLayout','clusterByGroups','clusterByHubsize',
    'clusterExpand','applyFilter','fillEdges']) assert.doesNotThrow(() => context[fn](), fn);
  context.show([{type:'vertex',id:1,label:'sample',element:[{key:'lbl',value:'sample'},
    {key:'title',value:'one'},{key:'group',value:'old'}]}]);
  context.removeNode(1);
  const clustered=[];
  context.network.cluster = options => clustered.push(options.clusterNodeProperties.id);
  context.clusterByGroups();
  assert.deepEqual(clustered, [], 'removed groups must not be clustered');
}
function testPopupUsesTextNodes() {
  const {context} = setup();
  const popup = {document:{title:'',body:element('body'),createElement:element,
    createTextNode:text => ({tag:'#text',textContent:text})},focus() {}};
  context.open = () => popup;
  context.popup('<svg onload=alert(1)>','<img src=x onerror=alert(1)>');
  assert.equal(popup.document.body.children.some(child => child.tag === 'img'), false);
  assert.ok(JSON.stringify(popup.document.body.children).includes('<img src=x'));
}
function testClusterTooltipEscapesHostileGroup() {
  const {context} = setup();
  context.nodes.push({id:1,group:'<img src=x onerror=alert(1)>'});
  const clusters=[];
  context.network = {setData() {},cluster(options) { clusters.push(options.clusterNodeProperties); }};
  context.clusterByGroups();
  assert.equal(clusters.length, 1);
  assert.ok(clusters[0].title.includes('&lt;img'), 'vis cluster tooltip must not parse hostile group');
}
function testGraphIdsAreCheckedAtEveryQueryBoundary() {
  const {context, controls} = setup();
  const queries=[];
  context.callGremlinValues = query => { queries.push(query); return 'ok'; };
  context.callGremlinGraph = query => { queries.push(query); };
  context.popup = () => {};
  const bad = "1);globalThis.compromised=true;//";
  const badEdge = "4r44db-5f3i0o-5jp-s0mxs');globalThis.compromised=true;//";
  assert.equal(context.stylesheetValue({gremlin:'valueMap()'},bad,new Map(),new Map(),false), '');
  assert.equal(context.stylesheetValue({gremlin:'valueMap()'},badEdge,new Map(),new Map(),true), '');
  controls.expandTo.checked = true;
  context.expand(bad);
  context.nodes.push({id:bad,title:'bad',label:'bad'}, {id:123,title:'good',label:'good'});
  context.edges.push({id:badEdge,title:'bad edge',from:bad,to:123});
  context.describeNode(bad);
  context.describeEdge(badEdge);
  context.network = {};
  context.fillEdges();
  assert.deepEqual(queries, [], 'invalid graph IDs must never reach any Gremlin query');
  assert.equal(context.compromised, undefined);
  context.nodes.splice(0,1);
  context.edges.splice(0,1);
  context.expand(123);
  context.describeNode(123);
  assert.equal(context.stylesheetValue({gremlin:'valueMap()'},123,new Map(),new Map(),false), 'ok');
  context.fillEdges();
  assert.ok(queries.some(query => query.includes('.V(123).out()')));
  assert.ok(queries.some(query => query.includes('within(123)')));
  assert.equal(context.stylesheetValue({gremlin:'valueMap()'},'4r44db-5f3i0o-5jp-s0mxs',new Map(),new Map(),true), 'ok');
  context.edges.push({id:'4r44db-5f3i0o-5jp-s0mxs',title:'edge'});
  context.describeEdge('4r44db-5f3i0o-5jp-s0mxs');
  assert.ok(queries.some(query => query.includes(".E('4r44db-5f3i0o-5jp-s0mxs')")));
}
function testDocumentedGraphsonIdentifiers() {
  const {context} = setup();
  const graphson = {result:{data:{'@value':[
    {'@type':'g:Vertex','@value':{id:{'@value':123},label:'object'}},
    {'@type':'g:Edge','@value':{id:{'@value':{relationId:'4r44db-5f3i0o-5jp-s0mxs'}},
      label:'match',inV:{'@value':123},outV:{'@value':456}}}
  ]}}};
  const parsed = context.parseGraph(JSON.stringify(graphson));
  assert.equal(context.validGraphId(parsed[0].id,false), true);
  assert.equal(context.validGraphId(parsed[1].id,true), true);
  assert.equal(context.validGraphId(parsed[1].inVid,false), true);
  assert.equal(context.validGraphId(parsed[1].outVid,false), true);
  assert.equal(context.validGraphId('1);drop();',false), false);
  assert.equal(context.validGraphId("4r44db');drop();//",true), false);
}
function testSentinelsReachTheirMatchingPanesOnly() {
  const {context, controls} = setup();
  const loaded=[];
  context.loadPane = (pane,url) => loaded.push([pane,url]);
  context.showGraphCommands({id:1,heading:'safe',actions:[
    {name:'LightCurve',target:'plot',url:'lightcurve'},
    {name:'Cutouts',target:'image',url:'cutouts'},
    {name:'wrong',target:'result',url:'lightcurve'},
    {name:'wrong',target:'plot',url:'cutouts'}].map(action => context.safeGraphAction(action,action.url)).filter(Boolean)},false);
  controls.commands.children.filter(child => child.tag === 'a').forEach(link => link.listeners.click({preventDefault() {}}));
  assert.deepEqual(loaded, [['plot','lightcurve'],['image','cutouts']]);
}
async function testInternalActionEncodesOnceAtRealLoader() {
  const {context, controls} = setup();
  const loaded=[];
  controls.table = element('table');
  context.$ = () => ({load: url => loaded.push(url)});
  context.showTab = () => {};
  context.w2ui = {layoutMain:{panels:[{tabs:{click() {}}}]}};
  context.sleep = () => Promise.resolve();
  vm.runInContext(fs.readFileSync(path.join(__dirname,'../../webservice/LoadPane.js'),'utf8'),context);
  context.showGraphCommands({id:1,heading:'safe',actions:[
    {name:'table',target:'table',url:'Nodes.jsp?id=one two'}]},false);
  const link = controls.commands.children.find(child => child.tag === 'a');
  link.listeners.click({preventDefault() {}});
  await Promise.resolve();
  assert.deepEqual(loaded, ['Nodes.jsp?id=one%20two']);
}
async function main() {
for (const test of [testGraphPropertyCannotExecute,testHostileGraphTextAndActionsRemainInert,
  testGraphImageFilenameCannotEscapeImagesDirectory,testControlsBeforeInitializationAndGroupsAfterRemoval,testPopupUsesTextNodes,
  testClusterTooltipEscapesHostileGroup,testGraphIdsAreCheckedAtEveryQueryBoundary,
  testDocumentedGraphsonIdentifiers,
  testSentinelsReachTheirMatchingPanesOnly,testInternalActionEncodesOnceAtRealLoader]) await test();
console.log('GraphViewNew offline security tests passed');
}
main().catch(error => { console.error(error); process.exitCode = 1; });

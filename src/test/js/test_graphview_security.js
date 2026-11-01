'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../../webservice/GraphView.js'), 'utf8');
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
  vm.runInContext(source, context, {filename:'GraphView.js'});
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
function popupWindow() {
  return {document:{title:'',body:element('body'),createElement:element,
    createTextNode:text => ({tag:'#text',textContent:text})},focus() {}};
}
function testPopupTreatsUntrustedSeparatorsAsPlainText() {
  const {context} = setup();
  const popup = popupWindow();
  context.open = () => popup;
  context.popup('<svg onload=alert(1)>','first<br/>second<hr/><img src=x onerror=alert(1)>');
  assert.deepEqual(popup.document.body.children.map(child => child.tag),['b','hr','pre','button']);
  const description = popup.document.body.children[2];
  assert.deepEqual(description.children.map(child => child.tag),['#text']);
  assert.equal(description.children[0].textContent,
    'first<br/>second<hr/><img src=x onerror=alert(1)>');
}
function testDescribeFormatsGraphsonRowsWithoutInterpretingGraphValues() {
  const {context} = setup();
  const windows=[];
  context.open = () => {const w=popupWindow();windows.push(w);return w;};
  const hostile='<img src=x onerror=alert(1)><br/><hr/>';
  const fixture={result:{data:{'@value':[{'@type':'g:Map','@value':[
    'name',{'@type':'g:List','@value':[{'@type':'g:String','@value':hostile}]},
    'rank',{'@type':'g:List','@value':[{'@type':'g:Int32','@value':7}]}
  ]}]}}};
  const queries=[];
  context.callGremlinValues=query => {queries.push(query);return context.parseValues(JSON.stringify(fixture));};
  context.nodes.push({id:'123',title:'node '+hostile});
  context.edges.push({id:'edge-1',title:'edge '+hostile});
  context.describeNode('123');context.describeEdge('edge-1');
  assert.deepEqual(queries,["g.V('123').valueMap()","g.E('edge-1').valueMap()"]);
  for (const w of windows) {
    const body=w.document.body.children;
    assert.deepEqual(body.map(child => child.tag),['b','hr','pre','hr','pre','button']);
    assert.deepEqual(body[2].children.map(child => child.tag),['#text','br','#text']);
    assert.equal(body[2].children[0].textContent,'name: '+hostile);
    assert.equal(body[2].children[2].textContent,'rank: 7');
    assert.ok(body[4].children[0].textContent.includes(hostile));
    assert.equal(JSON.stringify(body).includes('"tag":"img"'),false);
  }
}
function testDescribeFallbackPreservesLiteralResponse() {
  const {context} = setup();
  const popup=popupWindow();context.open=() => popup;
  context.callGremlinValues=() => 'raw <br/> text <hr/> <script>alert(1)</script>';
  context.nodes.push({id:1,title:'fallback'});
  context.describeNode(1);
  const body=popup.document.body.children;
  assert.deepEqual(body.map(child => child.tag),['b','hr','pre','hr','pre','button']);
  assert.deepEqual(body[2].children.map(child => child.tag),['#text']);
  assert.equal(body[2].children[0].textContent,'raw <br/> text <hr/> <script>alert(1)</script>');
}
function testActionsAreCommaSeparated() {
  const {context,controls} = setup();
  context.showGraphCommands({id:1,heading:'safe',actions:[
    {name:'Show',target:'result',url:'Node.jsp?id=1'},
    {name:'Table',target:'table',url:'Nodes.jsp?id=1'},
    {name:'SkyView',target:'skyview',url:'d3/skyview.jsp'}]},false);
  const actions=controls.commands.children.filter(child => child.tag === 'a' || child.tag === '#text');
  assert.deepEqual(actions.map(child => child.tag === 'a' ? child.textContent : child.textContent),
    ['Show', ', ', 'Table', ', ', 'SkyView']);
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
  context.server = '//offline-graph:8182';
  context.fillEdges();
  assert.deepEqual(queries, [], 'invalid graph IDs must never reach any Gremlin query');
  assert.equal(context.compromised, undefined);
  context.nodes.splice(0,1);
  context.edges.splice(0,1);
  context.expand(123);
  context.describeNode(123);
  assert.equal(context.stylesheetValue({gremlin:'valueMap()'},123,new Map(),new Map(),false), 'ok');
  assert.equal(queries.at(-1), 'g.V(123).valueMap()', 'numeric IDs remain numeric in stylesheet lookups');
  assert.equal(context.stylesheetValue({gremlin:'valueMap()'},'123',new Map(),new Map(),false), 'ok');
  assert.equal(queries.at(-1), "g.V('123').valueMap()", 'string IDs remain quoted in stylesheet lookups');
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
function testDefaultStylesheetUsesNumericVertexIdForPropertiesAndActions() {
  const {context, controls} = setup();
  vm.runInContext(fs.readFileSync(path.join(__dirname,'../../webservice/StylesheetDefault.js'),'utf8'),context);
  const queries=[];
  context.callGremlinValues=query => {
    queries.push(query);
    if (query.includes('valueMap(')) return [{'@type':'g:Map','@value':[
      'lbl',{'@type':'g:List','@value':['Object 123']}]}];
    if (query.includes('Node.jsp?id=')) return 'Node.jsp?id=123';
    if (query.includes('Nodes.jsp?id=')) return 'Nodes.jsp?id=123';
    return '';
  };
  const graphson={result:{data:{'@value':[{'@type':'g:Vertex','@value':{
    id:{'@type':'g:Int64','@value':123},label:'object',
    properties:{lbl:[{'@value':{value:'Object 123'}}]}}}]}}};
  context.show(context.parseGraph(JSON.stringify(graphson)));
  context.network.listeners.click({nodes:[123],edges:[]});
  assert.ok(queries.length >= 3);
  assert.ok(queries.every(query => query.startsWith('g.V(123).')), 'default style must use the numeric vertex ID');
  assert.equal(context.nodes[0].label, 'Object 123');
  assert.ok(controls.commands.children.some(child => child.tag === 'a' && child.textContent === 'show'));
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
  testGraphImageFilenameCannotEscapeImagesDirectory,testControlsBeforeInitializationAndGroupsAfterRemoval,
  testPopupTreatsUntrustedSeparatorsAsPlainText,testDescribeFormatsGraphsonRowsWithoutInterpretingGraphValues,
  testDescribeFallbackPreservesLiteralResponse,testActionsAreCommaSeparated,
  testClusterTooltipEscapesHostileGroup,testGraphIdsAreCheckedAtEveryQueryBoundary,
  testDocumentedGraphsonIdentifiers,testDefaultStylesheetUsesNumericVertexIdForPropertiesAndActions,
  testSentinelsReachTheirMatchingPanesOnly,testInternalActionEncodesOnceAtRealLoader]) await test();
console.log('GraphView offline security tests passed');
}
main().catch(error => { console.error(error); process.exitCode = 1; });

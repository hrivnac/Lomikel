'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../../webservice/GraphView.js'), 'utf8');
function setup() {
  const ids = ['vis','select','filter','expandTo','expandFrom','removeOld','clusterize','zoom',
    'stabilize','add2graph','commands','feedback'];
  const controls = Object.fromEntries(ids.map(id => [id, {value:'',checked:false,children:[],
    appendChild(child) {this.children.push(child);},replaceChildren() {this.children=[];}}]));
  controls.select.value='limit(2)';
  controls.vis.offsetHeight=500;
  const listeners=[];
  const context=vm.createContext({console, document:{getElementById:id => controls[id] || null,
    createElement:tag => ({tag,addEventListener() {},appendChild() {}}),createTextNode:text => ({text})},
    vis:{Network:class {constructor() {this.listeners={};listeners.push(this.listeners);}
      on(name,callback) {this.listeners[name]=callback;} once() {} isCluster() {return false;}
      destroy() {} getScale() {return 1;} getViewPosition() {return {x:0,y:0};}
      moveTo() {}}}, options4Graph:{},stylesheet:{nodes:{default:{properties:{},
      graphics:{title:'lbl',subtitle:'',label:'lbl',group:' ',value:'0',shape:'dot',borderWidth:'1',borderDashes:[1,0]},actions:[]}},
      edges:{default:{properties:{},graphics:{title:'lbl',subtitle:'',label:'lbl',group:' ',value:'1',arrows:' '},actions:[]}}},
    setTimeout,showTab() {}});
  context.window=context;
  vm.runInContext(source,context,{filename:'GraphView.js'});
  return {context,controls,listeners};
}
const vertex=(id,label) => ({'@type':'g:Vertex','@value':{id:{'@value':id},label:'sample',
  properties:{lbl:[{'@value':{value:label}}]}}});
const edge=(id,from,to) => ({'@type':'g:Edge','@value':{id:{'@value':{relationId:id}},label:'link',
  inV:{'@value':to},outV:{'@value':from}}});
const result=items => JSON.stringify({result:{data:{'@value':items}}});
function exercise({id=42,children=true,parents=false,replace=false,filter=''}) {
  const {context,controls}=setup();
  controls.expandTo.checked=children;controls.expandFrom.checked=parents;
  controls.removeOld.checked=replace;controls.filter.value=filter;
  context.server='//offline-graph:8182';
  context.show([{type:'vertex',id,label:'sample',element:[{key:'lbl',value:'keep root'}]},
    {type:'vertex',id:999,label:'sample',element:[{key:'lbl',value:'old vertex'}]}]);
  const requests=[];
  context.XMLHttpRequest=class {
    open(method,url,async) {assert.equal(method,'GET');assert.equal(async,false);this.url=url;}
    send() {
      const query=new URL('http:'+this.url).searchParams.get('gremlin');
      requests.push(query);
      const outgoing=query.includes('.out()');
      const incoming=query.includes('.in()');
      const outgoingEdge=query.includes('.outE()');
      const incomingEdge=query.includes('.inE()');
      const payload=outgoing ? [vertex(43,'keep child'),vertex(44,'hidden child')]
        : incoming ? [vertex(41,'keep parent')]
        : outgoingEdge ? [edge('edge-out',id,43),edge('edge-hidden',id,44)]
        : incomingEdge ? [edge('edge-in',41,id)] : [];
      this.readyState=4;this.status=200;this.responseText=result(payload);this.onload();
    }
  };
  const current=context.network;
  current.listeners.doubleClick({nodes:[id],edges:[]});
  return {context,requests};
}
function testStringIdDoubleClickExpandsConnectedVerticesAndEdges() {
  const {context,requests}=exercise({id:'vertex-7',children:true,parents:true});
  assert.deepEqual(requests, ["g.V('vertex-7').out().limit(2)","g.V('vertex-7').in().limit(2)",
    "g.V('vertex-7').outE().limit(2)","g.V('vertex-7').inE().limit(2)"]);
  assert.deepEqual(Array.from(context.nodes,n=>n.id),['vertex-7',999,43,44,41]);
  assert.deepEqual(Array.from(context.edges,e=>e.id),['edge-out','edge-hidden','edge-in']);
}
function testNumericIdMenusAndReplace() {
  const {context,requests}=exercise({children:true,parents:false,replace:true,filter:'keep'});
  assert.deepEqual(requests,['g.V(42).out().limit(2)','g.V(42).outE().limit(2)']);
  assert.deepEqual(Array.from(context.nodes,n=>n.id),[42,43]);
  assert.deepEqual(Array.from(context.edges,e=>e.id),['edge-out']);
}
function testNumericLookingStringIdStaysQuoted() {
  const {context,requests}=exercise({id:'123',children:true,parents:false});
  assert.deepEqual(requests,["g.V('123').out().limit(2)","g.V('123').outE().limit(2)"]);
  assert.equal(context.graphVertexLiteral(123), '123');
  assert.equal(context.graphVertexLiteral('00123'), "'00123'");
  assert.equal(context.graphVertexLiteral("123');drop();//"), null);
}
function testEdgeFirstFilteredGraphsonKeepsOnlyVisibleConnections() {
  const {context,controls}=setup();
  controls.filter.value='keep';
  context.show(context.parseGraph(result([
    edge('visible',1,2),edge('orphan',1,3),
    vertex(1,'keep one'),vertex(2,'keep two'),vertex(3,'hidden')])));
  assert.deepEqual(Array.from(context.nodes,n=>n.id),[1,2]);
  assert.deepEqual(Array.from(context.edges,e=>e.id),['visible']);
  const unfiltered=setup();
  unfiltered.context.show(unfiltered.context.parseGraph(result([
    edge('orphan',1,3),vertex(1,'keep one')])));
  assert.deepEqual(Array.from(unfiltered.context.edges,e=>e.id),['orphan'],
    'unfiltered rendering retains prior edge-first behavior');
}
function testParentsOnly() {
  const {context,requests}=exercise({children:false,parents:true});
  assert.deepEqual(requests,['g.V(42).in().limit(2)','g.V(42).inE().limit(2)']);
  assert.deepEqual(Array.from(context.nodes,n=>n.id),[42,999,41]);
}
for(const test of [testStringIdDoubleClickExpandsConnectedVerticesAndEdges,
  testNumericIdMenusAndReplace,testNumericLookingStringIdStaysQuoted,
  testEdgeFirstFilteredGraphsonKeepsOnlyVisibleConnections,testParentsOnly]) test();
console.log('GraphView double-click expansion tests passed');

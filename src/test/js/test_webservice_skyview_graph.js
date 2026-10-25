'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../../webservice');
const stylesheetCode = fs.readFileSync(path.join(root, 'SurveyStylesheet.js'), 'utf8');
const graphCode = fs.readFileSync(path.join(root, 'skyview-graph.js'), 'utf8');
const workspaceCode = fs.readFileSync(path.join(root, 'workspace.js'), 'utf8');
const graphPage = fs.readFileSync(path.join(root, 'GraphView.jsp'), 'utf8');
assert.match(graphPage, /Stylesheet\.js\?v=survey-colors-/);
assert.match(graphPage, /skyview-graph\.js\?v=skyview-2/);
assert.match(workspaceCode, /skyGraphState\.open\(\)/);
const style = {nodes:{object:{actions:[{name:'Table',url:'Nodes.jsp',target:'table'}]}}};
vm.runInNewContext(stylesheetCode, {stylesheet:style});
assert.equal(style.nodes.object.actions.find(a => a.name === 'SkyView').target, 'skyview');
assert.equal(style.nodes.object.actions.filter(a => a.name === 'SkyView').length, 1);
const calls = [];
const pending = [];
const posted = [];
const sky = {innerHTML:'', children:[], appendChild(child) {this.children.push(child);},
  replaceChildren(child) {this.children=[child];}};
const nodes = [];
const document={baseURI:'http://local/FinkBrowser/',body:{appendChild(){},removeChild(){}},
  createElement: tag => ({tag,children:[],appendChild(child){this.children.push(child);},
    submit(){posted.push(this);}}), getElementById: id => id==='skyview' ? sky : null};
const ctx = {window:{}, nodes, document,
  fetch:(url,options) => new Promise((resolve,reject) => pending.push({url,options,resolve,reject})),
  loadPane:(...args) => calls.push(args), showTab:name => calls.push(['tab',name]),
  w2ui:{layoutMain:{panels:[{tabs:{click:name=>calls.push(['click',name])}}]}},
  URL, URLSearchParams, JSON, Set, Map, AbortController};
ctx.window.nodes = nodes;
ctx.window.show = graph => {if(graph) for(const item of graph) if(item.type==='vertex') nodes.push({id:item.id,label:item.element.find(p=>p.key==='objectId')?.value||'',title:item.label+':x'});};
ctx.window.disposeGraphView = ()=>{nodes.length=0;};
vm.runInNewContext(graphCode, ctx);
const object = (id,oid) => ({type:'vertex',label:'object',id,element:[{key:'objectId',value:oid}]});
ctx.window.show([object(1,'ZTF_TEST'),object(2,'123456'),{type:'vertex',label:'OCol',id:3,element:[{key:'objectId',value:'FAKE'}]}]);
assert.deepEqual(Array.from(ctx.window.skyGraphState.getIds()), ['ZTF_TEST','123456']);
ctx.window.show([object(4,'ZTF_TEST')]);
assert.deepEqual(Array.from(ctx.window.skyGraphState.getIds()), ['ZTF_TEST','123456']);
nodes.splice(0,1); ctx.window.show(null);
assert.deepEqual(Array.from(ctx.window.skyGraphState.getIds()), ['123456','ZTF_TEST']);
async function test() {
 const request = ctx.window.skyGraphState.open();
 assert.equal(pending.length,1);
 assert.equal(pending[0].url,'SkyPositions.jsp');
 assert.deepEqual(JSON.parse(pending[0].options.body).ids,['123456','ZTF_TEST']);
 assert.equal(pending[0].options.method,'POST');
 pending[0].resolve({ok:true,json:async()=>({points:[{objectId:'123456',ra:0,dec:0},{objectId:'ZTF_TEST',ra:180,dec:22}],missing:[]})});
 await request;
 assert.equal(sky.children[0].name,'skyGraphFrame');
 assert.equal(posted.at(-1).action,'/FinkBrowser/d3/skyview.jsp');
 const points=JSON.parse(posted.at(-1).children.find(x=>x.name==='points').value);
 assert.equal(points[0].ra,0);
 assert.equal(posted.at(-1).children.find(x=>x.name==='requested').value,'2');
 const stale = ctx.window.skyGraphState.open();
 assert.equal(pending.length,2);
 ctx.window.disposeGraphView();
 pending[1].resolve({ok:true,json:async()=>({points:[{objectId:'123456',ra:1,dec:2}],missing:[]})});
 await stale;
 assert.equal(posted.length,1,'reset must suppress stale plot');
 console.log('webservice SkyView graph snapshots, route, and reset race passed');
}
test().catch(e=>{console.error(e); process.exitCode=1});

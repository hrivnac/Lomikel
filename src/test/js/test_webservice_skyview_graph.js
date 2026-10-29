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
assert.match(graphPage, /skyview-graph\.js\?v=longss-history-1/);
assert.match(fs.readFileSync(path.join(root,'GraphViewNew.jsp'),'utf8'),
  /skyview-graph\.js\?v=longss-history-1/);
assert.match(workspaceCode, /skyGraphState\.open\(\)/);
const style = {nodes:{object:{actions:[{name:'Table',url:'Nodes.jsp',target:'table'}]}}};
vm.runInNewContext(stylesheetCode, {stylesheet:style});
assert.equal(style.nodes.object.actions.find(a => a.name === 'SkyView').target, 'skyview');
assert.equal(style.nodes.object.actions.filter(a => a.name === 'SkyView').length, 1);
const calls = [];
const queries = [];
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
ctx.window.activityLogQuery = (target, query) => queries.push({target, query});
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
 assert.equal(queries.length,0,'do not guess which server-side indices will execute');
 assert.equal(pending[0].options.method,'POST');
 const executed=[
   {path:'/radec/_mget',body:JSON.stringify({docs:[{_id:'ZTF_TEST',_source:['location']}]})},
   {path:'/dia_radec/_mget',body:JSON.stringify({docs:[{_id:'123456',_source:['location']}]})},
   {path:'/ss_radec/_mget',body:JSON.stringify({docs:[{_id:'123456',_source:false}]})}];
 pending[0].resolve({ok:true,json:async()=>({points:[{objectId:'123456',ra:0,dec:0},{objectId:'ZTF_TEST',ra:180,dec:22}],missing:[],ssObjectIds:[],esRequests:executed})});
 await request;
 assert.deepEqual(queries,[
   {target:'ES',query:executed[0].path+' '+executed[0].body},
   {target:'ES',query:executed[1].path+' '+executed[1].body},
   {target:'ES',query:executed[2].path+' '+executed[2].body}], 'one activity entry per executed index and exact body');
 assert.equal(sky.children[0].name,'skyGraphFrame');
 assert.equal(posted.at(-1).action,'/FinkBrowser/d3/skyview.jsp');
 const points=JSON.parse(posted.at(-1).children.find(x=>x.name==='points').value);
 assert.equal(points[0].ra,0);
 assert.equal(posted.at(-1).children.find(x=>x.name==='requested').value,'2');
 const noFallback=ctx.window.skyGraphState.open();
 pending[1].resolve({ok:true,json:async()=>({points:[],missing:['123456','ZTF_TEST'],ssObjectIds:[],esRequests:executed.slice(0,2)})});
 await noFallback;
 assert.equal(queries.length,5,'do not invent a conditional ss_radec call');
 const malformed=ctx.window.skyGraphState.open();
 pending[2].resolve({ok:true,json:async()=>({points:[],missing:[],ssObjectIds:[],esRequests:[{path:'https://private.example/key',body:'secret'}]})});
 await malformed;
 assert.equal(queries.length,5,'untrusted response metadata cannot leak into the activity pane');
 assert.equal(posted.length,3,'invalid activity metadata must not break the plotting response');
 const largeIds=Array.from({length:500},(_,i)=>'ZTF'+String(i).padStart(77,'0'));
 const large=ctx.window.skyGraphState.openIds(largeIds);
 const largeBody=JSON.stringify({docs:largeIds.map(_id=>({_id,_source:['location']}))});
 assert.ok(largeBody.length > 48000,'exercise a valid large _mget body');
 pending[3].resolve({ok:true,json:async()=>({points:[],missing:largeIds,ssObjectIds:[],
   esRequests:[{path:'/radec/_mget',body:largeBody}]})});
 await large;
 assert.equal(queries.at(-1).query,'/radec/_mget '+largeBody,'large actual body remains inspectable');
 const failed=ctx.window.skyGraphState.openIds(['ZTF_TEST']);
 pending[4].resolve({ok:false,status:502,json:async()=>({error:'Sky coordinate lookup failed',
   esRequests:[executed[0]]})});
 await failed;
 assert.equal(queries.at(-1).query,executed[0].path+' '+executed[0].body,
   'executed ES request is visible even when the proxy fails');
 assert.equal(queries.length,7);
 const stale = ctx.window.skyGraphState.open();
 assert.equal(pending.length,6);
 ctx.window.disposeGraphView();
 pending[5].resolve({ok:true,json:async()=>({points:[{objectId:'123456',ra:1,dec:2}],missing:[],ssObjectIds:[],esRequests:executed})});
 await stale;
 assert.equal(posted.length,4,'reset must suppress stale plot');
 assert.equal(queries.length,7,'reset must suppress stale activity');
 const solar=ctx.window.skyGraphState.openIds(['123456']);
 pending[6].resolve({ok:true,json:async()=>({points:[{objectId:'123456',ra:1,dec:2}],missing:[],
   ssObjectIds:['123456'],esRequests:executed.slice(1)})});
 for(let i=0;i<10 && pending.length<8;i++) await new Promise(setImmediate);
 assert.equal(pending[7].url,'AlertsView/SSTrajectory.jsp?id=123456');
 assert.equal(posted.length,4,'wait for the full paired source history before drawing');
 const resolver={path:'/api/v1/resolver',body:JSON.stringify({resolver:'ssodnet',name_or_id:'123456',reverse:true,nmax:10,'output-format':'json'})};
 const sso={path:'/api/v1/sso',body:JSON.stringify({n_or_d:'K25AB1',columns:'r:diaSourceId,r:ssObjectId,r:midpointMjdTai,r:ra,r:dec','output-format':'json'})};
 const historyEs=[{path:'/ss_mjd/_mget',body:JSON.stringify({ids:['123456']})},
   {path:'/ss_radec/_mget',body:JSON.stringify({ids:['123456']})}];
 pending[7].resolve({ok:true,json:async()=>({objectId:'123456',esCoverageComplete:true,esMjdAvailable:false,
   sources:[{sourceId:'90071992547409930',mjd:61003,ra:1,dec:0},
     {sourceId:'90071992547409929',mjd:61002,ra:359,dec:0}],finkRequests:[resolver,sso],esRequests:historyEs})});
 await solar;
 assert.equal(posted.length,5);
 assert.deepEqual(JSON.parse(posted.at(-1).children.find(x=>x.name==='points').value),[],
   'sparse ES positions are replaced by complete source rows');
 const trajectories=JSON.parse(posted.at(-1).children.find(x=>x.name==='trajectories').value);
 assert.deepEqual(trajectories,[{objectId:'123456',sources:[
   {sourceId:'90071992547409929',mjd:61002,ra:359,dec:0},
   {sourceId:'90071992547409930',mjd:61003,ra:1,dec:0}],esMjdAvailable:false}]);
 assert.deepEqual(queries.slice(-4).map(q=>q.query),[
   ...historyEs.map(x=>x.path+' '+x.body),
   'https://api.lsst.fink-portal.org'+resolver.path+' '+resolver.body,
   'https://api.lsst.fink-portal.org'+sso.path+' '+sso.body]);
 const late=ctx.window.skyGraphState.openIds(['123456']);
 pending[8].resolve({ok:true,json:async()=>({points:[{objectId:'123456',ra:1,dec:2}],missing:[],
   ssObjectIds:['123456'],esRequests:executed.slice(1)})});
 for(let i=0;i<10 && pending.length<10;i++) await new Promise(setImmediate);
 assert.equal(pending.length,10);
 ctx.window.skyGraphState.cancel();
 pending[9].resolve({ok:true,json:async()=>({objectId:'123456',esCoverageComplete:true,
   sources:[{sourceId:'3',mjd:61004,ra:2,dec:0}],finkRequests:[resolver,sso]})});
 await late;
 assert.equal(posted.length,5,'reset after ES but before Fink must not plot stale trajectory');
 const longIds=Array.from({length:10},(_,i)=>String(21163615905265241n+BigInt(i)));
 const longMap=ctx.window.skyGraphState.openIds(longIds);
 pending[10].resolve({ok:true,json:async()=>({points:[],missing:[],ssObjectIds:longIds,esRequests:[]})});
 let nextHistory=11;
 for(let i=0;i<longIds.length;i++) {
   for(let spin=0;spin<30 && pending.length<=nextHistory;spin++) await new Promise(setImmediate);
   assert.equal(pending[nextHistory].url,'AlertsView/SSTrajectory.jsp?id='+longIds[i]);
   const id=longIds[i];
   pending[nextHistory++].resolve({ok:true,json:async()=>({objectId:id,esCoverageComplete:true,
     sources:Array.from({length:800},(_,j)=>({sourceId:String(300000000000000000n+BigInt(i*800+j)),
       mjd:61000+j/10000,ra:j/100,dec:20})),finkRequests:[],esRequests:[]})});
   await new Promise(setImmediate);
 }
 await longMap;
 assert.equal(posted.length,6,'ten complete SS histories must reach the map');
 assert.equal(JSON.parse(posted.at(-1).children.find(x=>x.name==='trajectories').value)
   .reduce((n,t)=>n+t.sources.length,0),8000,'no SS observation may be silently dropped');
 console.log('webservice SkyView graph snapshots, route, and reset race passed');
}
test().catch(e=>{console.error(e); process.exitCode=1});

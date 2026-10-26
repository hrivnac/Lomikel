'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const root=path.resolve(__dirname,'../../webservice');
const text=name=>fs.readFileSync(path.join(root,name),'utf8');
const stylesheet={nodes:{object:{actions:[{name:'Show',target:'result'},{name:'Table',target:'table'}]}}};
vm.runInNewContext(text('SurveyStylesheet.js'),{stylesheet});
vm.runInNewContext(text('SurveyStylesheet.js'),{stylesheet});
const actions=stylesheet.nodes.object.actions.filter(a=>a.name==='LightCurve');
assert.equal(actions.length,1);
assert.equal(actions[0].target,'plot');
assert.equal(actions[0].url,'lightcurve');
assert.match(text('GraphView.jsp'),/lightcurve-graph\.js\?v=/);
assert.match(text('workspace.js'),/lightcurveGraphState\.open\(\)/);
assert.match(text('workspace.js'),/lightcurveGraphState\.cancel\(\)/);
assert.match(text('index.jsp'),/workspace\.js\?v=corr-fit-/);
const posts=[];
const queries=[];
function node(tag){const classes=new Set();return {tag,children:[],style:{},
 classList:{add:name=>classes.add(name),remove:name=>classes.delete(name),contains:name=>classes.has(name)},
 appendChild(child){this.children.push(child);return child},
 replaceChildren(...items){this.children=items},submit(){posts.push(this)},remove(){this.removed=true}}}
const pane=node('div'),body=node('body'),events=[],handlers=[];
const commands={contains:element=>element===link};
const link={textContent:'LightCurve',getAttribute:name=>name==='href'?'#':null,closest:selector=>selector==='a'?link:null};
const first={id:1,title:'object:ZTF21aaxtctv'},second={id:2,title:'object:313761043604045880'};
const ids=new Map([[1,'ZTF21aaxtctv'],[2,'313761043604045880']]);
const ctx={window:{selectedNode:first,nodes:[first,second],skyGraphState:{getId:id=>ids.get(id)}},
 document:{baseURI:'http://127.0.0.1/FinkBrowser/',body,getElementById:id=>id==='plot'?pane:id==='commands'?commands:null,
  createElement:node,addEventListener:(name,handler,capture)=>handlers.push({name,handler,capture})},
 showTab:value=>events.push(value),w2ui:{layoutMain:{panels:[{tabs:{click:value=>events.push(value)}}]}},URL};
 ctx.window.activityLogQuery=(target,query)=>queries.push({target,query});
body.removeChild=function(child){this.children.splice(this.children.indexOf(child),1)};
body.appendChild=function(child){this.children.push(child);return child};
vm.runInNewContext(text('lightcurve-graph.js'),ctx);
ctx.window.lightcurveGraphState.open();
assert.deepEqual(events,['plot','plotTab']);
assert.equal(pane.classList.contains('ws-lightcurve'),true);
assert.equal(pane.children.length,1);
let frame=pane.children[0];
assert.equal(frame.tag,'iframe');
assert.equal(body.children.find(child=>child.tag==='form'),undefined,'transient POST form removed after submitting');
assert.equal(posts.at(-1).children[0].name,'objectId');
assert.equal(posts.at(-1).children[0].value,'ZTF21aaxtctv');
assert.equal(queries[0].target,'Fink');
assert.equal(queries[0].query,'https://api.ztf.fink-portal.org/api/v1/objects '+JSON.stringify({objectId:'ZTF21aaxtctv',columns:'i:objectId,i:candid,i:jd,i:fid,i:magpsf,i:sigmapsf','output-format':'json'}));
assert.match(posts.at(-1).action,/\/d3\/lightcurve\.jsp$/);
ctx.window.selectedNode=second;
ctx.window.lightcurveGraphState.open();
assert.equal(posts.at(-1).children[0].value,'313761043604045880');
assert.equal(queries[1].query,'https://api.lsst.fink-portal.org/api/v1/sources '+JSON.stringify({diaObjectId:'313761043604045880',columns:'r:diaObjectId,r:diaSourceId,r:midpointMjdTai,r:band,r:scienceFlux,r:scienceFluxErr','output-format':'json'}));
assert.notEqual(pane.children[0],frame);
ctx.window.selectedNode={id:2,title:'stale action'};
ctx.window.lightcurveGraphState.open();
assert.equal(posts.length,2,'stale selected node cannot query a new object');
assert.equal(queries.length,2,'stale selected node cannot log a search');
ctx.window.lightcurveGraphState.cancel();
assert.equal(pane.classList.contains('ws-lightcurve'),false);
// Real graph commands use href="#" under a WAR-root <base>. If the legacy
// inline onclick is blocked or throws, the browser otherwise leaves the preview.
ctx.window.selectedNode=first;
const click=handlers.find(item=>item.name==='click' && item.capture===true);
assert.ok(click,'preview action must intercept the actual graph link before inline handlers');
const interaction={target:link,prevented:false,stopped:false,
 preventDefault(){this.prevented=true},stopImmediatePropagation(){this.stopped=true}};
click.handler(interaction);
assert.equal(interaction.prevented,true);
assert.equal(interaction.stopped,true);
assert.equal(posts.at(-1).children[0].value,'ZTF21aaxtctv');
assert.equal(pane.children[0].tag,'iframe');
link.textContent='Table';
const other={...interaction,prevented:false,stopped:false};
click.handler(other);
assert.equal(other.prevented,false,'unrelated graph actions keep their legacy route');
link.textContent='LightCurve';
vm.runInNewContext(text('lightcurve-graph.js'),ctx);
assert.equal(handlers.filter(item=>item.name==='click').length,1,'graph reload must not duplicate the click handler');
assert.match(text('LightCurveData.jsp'),/api\.ztf\.fink-portal\.org\/api\/v1\/objects/);
assert.match(text('LightCurveData.jsp'),/api\.lsst\.fink-portal\.org\/api\/v1\/sources/);
for (const logged of queries.slice(0,2)) {
 const [,body]=logged.query.split(' ');
 const upstream=JSON.parse(body);
 assert.ok(text('LightCurveData.jsp').includes('"'+upstream.columns+'"'),
   'reported columns must match the upstream JSP');
 assert.match(text('LightCurveData.jsp'),/\.put\("output-format", "json"\)/);
}
assert.match(text('d3/lightcurve.jsp'),/d3-v6\.0\.0\/d3\.js/);
console.log('per-object LightCurve action routes one live graph object to Plot');

'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../../webservice');
const oldNodes = fs.readFileSync(path.resolve(__dirname, '../../webservice/Nodes.jsp'), 'utf8');
const workspace = fs.readFileSync(path.join(root, 'workspace.js'), 'utf8');
const graph = fs.readFileSync(path.join(root, 'skyview-graph.js'), 'utf8');
assert.match(oldNodes, /onclick="showSky\(\)"/);
assert.match(oldNodes, /loadPane\("skyview", "d3\/skyview\.jsp\?" \+ params/);
const calls = [], requests = [], posted = [];
const sky = {replaceChildren(child) {this.child=child;}, set textContent(value) {this.status=value;}};
const doc = {baseURI:'http://localhost/FinkBrowser/', body:{appendChild(){},removeChild(){}},
 createElement:tag=>({tag, children:[], appendChild(child){this.children.push(child);},submit(){posted.push(this);}}),
 getElementById:id=>id==='skyview'?sky:null};
const win = {nodes:[], show(graph){(graph||[]).forEach(item=>this.nodes.push({id:item.id}));},
 disposeGraphView(){this.nodes.length=0;}, innerWidth:1200,innerHeight:800,
 addEventListener(){},setTimeout(){},loadPane:(...args)=>calls.push(args),
 plotTableScalar:value=>typeof value==='string' && value.startsWith('[') && value.endsWith(']') ? value.slice(1,-1) : value,
 w2ui:{layoutMain:{panels:[{tabs:{click:id=>calls.push(['click',id])}}]}}};
const ctx = {window:win,document:doc,URL,URLSearchParams,Map,Set,AbortController,ResizeObserver:undefined,
 fetch:(url,options)=>new Promise(resolve=>requests.push({url,options,resolve})),
 showTab:id=>calls.push(['tab',id]), w2ui:win.w2ui, $:()=>{},
 loadPane:(...args)=>calls.push(args)};
vm.runInNewContext(graph,ctx);
vm.runInNewContext(workspace,ctx);
async function run() {
 win.tdata=[{lbl:'object',objectId:'ZTF_A'},{lbl:'[object]',objectId:'123456'},
            {lbl:'object',objectId:'ZTF_A'}];
 const action=win.loadPane('skyview','d3/skyview.jsp?name=ZTF_A&tdata=[]',true,720);
 assert.equal(requests.length,1,'Table object rows use the shared coordinate lookup');
 assert.deepEqual(JSON.parse(requests[0].options.body).ids,['ZTF_A','123456']);
 assert.equal(calls.some(c=>c[0]==='skyview'),false,'do not load the old coordinate JSP');
 requests[0].resolve({ok:true,json:async()=>({points:[{objectId:'ZTF_A',ra:0,dec:0}],missing:['123456']})});
 await action;
 assert.equal(posted.at(-1).action,'/FinkBrowser/d3/skyview.jsp');
 assert.equal(posted.at(-1).children.find(x=>x.name==='requested').value,'2');
 assert.equal(posted.at(-1).children.find(x=>x.name==='missing').value,'1');
 // A second Table request supersedes the first.
 win.tdata=[{lbl:'object',objectId:'ZTF_B'}];
 const pendingTable=win.loadPane('skyview','d3/skyview.jsp?name=ZTF_B&tdata=[]',true);
 assert.equal(requests.length,2);
 requests[1].resolve({ok:true,json:async()=>({points:[{objectId:'ZTF_B',ra:20,dec:2}],missing:[]})});
 await pendingTable;
 assert.equal(posted.length,2);
 // Switching to the coordinate route must cancel an older object request,
 // otherwise its late reply replaces the legacy map.
 win.tdata=[{lbl:'object',objectId:'ZTF_A'}];
 const objectRequest=win.loadPane('skyview','d3/skyview.jsp?name=ZTF_A&tdata=[]',true);
 assert.equal(requests.length,3);
 win.tdata=[{lbl:'alert',objectId:'ZTF_A',ra:'10',dec:'2'}];
 win.loadPane('skyview','d3/skyview.jsp?name=alert&tdata=[]',true);
 assert.equal(calls.at(-1)[0],'skyview','non-object coordinate route remains unchanged');
 assert.equal(requests[2].options.signal.aborted,true,'legacy route invalidates pending object lookup');
 requests[2].resolve({ok:true,json:async()=>({points:[{objectId:'ZTF_A',ra:1,dec:1}],missing:[]})});
 await objectRequest;
 assert.equal(posted.length,2,'stale object map cannot overwrite coordinate map');
 win.tdata=[{lbl:'object',objectId:'ZTF_A'}];
 win.loadPane('skyview','d3/skyview.jsp?name=object&tdata=[]',true);
 assert.equal(requests.length,4);
 win.disposeGraphView();
 requests[3].resolve({ok:true,json:async()=>({points:[{objectId:'ZTF_A',ra:1,dec:1}],missing:[]})});
 await new Promise(resolve=>setImmediate(resolve));
 assert.equal(posted.length,2,'reset cancels pending Table requests');
 win.show([{type:'vertex',label:'object',id:7,element:[{key:'objectId',value:'ZTF_A'}]}]);
 const graphRequest=win.skyGraphState.open();
 assert.equal(requests.length,5,'graph starts a lookup');
 win.tdata=[{lbl:'object',objectId:'ZTF_B'}];
 const nextTable=win.loadPane('skyview','d3/skyview.jsp?name=ZTF_B&tdata=[]',true);
 assert.equal(requests.length,6);
 assert.equal(requests[4].options.signal.aborted,true,'table supersedes graph lookup');
 requests[4].resolve({ok:true,json:async()=>({points:[{objectId:'ZTF_A',ra:0,dec:0}],missing:[]})});
 requests[5].resolve({ok:true,json:async()=>({points:[{objectId:'ZTF_B',ra:2,dec:3}],missing:[]})});
 await Promise.all([graphRequest,nextTable]);
 assert.equal(posted.length,3,'only latest table map submits');
 console.log('Table object SkyView routes through shared map and preserves coordinate route');
}
run().catch(e=>{console.error(e);process.exitCode=1});

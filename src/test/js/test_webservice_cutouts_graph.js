'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../../webservice');
const styleCode = fs.readFileSync(path.join(root, 'Stylesheet.js'), 'utf8');
const cutoutStyle = fs.readFileSync(path.join(root, 'cutouts.css'), 'utf8');
const skyStyle = fs.readFileSync(path.join(root, 'd3/skyview.css'), 'utf8');
for (const survey of ['ztf','lsst']) {
 const skyColor=skyStyle.match(new RegExp('\\.sky-legend-'+survey+'::before \\{background:(#[0-9a-f]+)\\}'))?.[1];
 assert.ok(skyColor, 'SkyView has a color for '+survey);
 assert.match(cutoutStyle,new RegExp('\\.ws-cutout-'+survey+' \\.ws-cutout-cell \\{[^}]*background:\\s*'+skyColor));
}
const style = {nodes: {object: {actions: [{name:'Table',target:'table'}]}}};
vm.runInNewContext(styleCode, {stylesheet:style});
vm.runInNewContext(styleCode, {stylesheet:style});
assert.equal(style.nodes.object.actions.filter(a => a.name === 'Cutouts').length, 1);
assert.equal(style.nodes.object.actions.find(a => a.name === 'Cutouts').target, 'image');
assert.match(style.nodes.object.actions.find(a => a.name === 'Cutouts').url, /webservice\/cutouts/);
const graphPage = fs.readFileSync(path.join(root, 'GraphView.jsp'), 'utf8');
assert.match(graphPage, /webservice\/cutouts-graph\.js\?v=/);
const workspaceCode = fs.readFileSync(path.join(root, 'workspace.js'), 'utf8');
assert.match(workspaceCode, /cutoutsGraphState\.open\(\)/);
assert.match(workspaceCode, /cutoutsGraphState\.cancel\(\)/);
assert.match(workspaceCode, /cutoutsGraphState\.clear\(\)/);
assert.doesNotMatch(workspaceCode, /loadPane\('image', 'Image\.jsp'\)/);
assert.match(fs.readFileSync(path.join(root, 'index.jsp'), 'utf8'), /workspace\.js\?v=corr-fit-1/);
assert.match(fs.readFileSync(path.join(root, 'index.jsp'), 'utf8'), /<template id="ws-image-placeholder">/);
const server = fs.readFileSync(path.join(root, 'CutoutSource.jsp'), 'utf8');
assert.match(server, /api\/v1\/sources/);
assert.match(server, /r:diaSourceId/);
assert.match(server, /diaObjectId/);
assert.match(server, /getRequestURI|POST/);
function element(tag) {
 return {tag,children:[],dataset:{},style:{},textContent:'',appendChild(child){this.children.push(child);return child},
   replaceChildren(...items){this.children=items},setAttribute(k,v){this[k]=v}};
}
const pane=element('div');
pane.innerHTML='<div id="image-placeholder">Images go here.</div>';
pane.classList={remove() {}};
const placeholder={innerHTML:pane.innerHTML};
const pending=[];
const events=[];
const ctx={window:{skyGraphState:{getIds:()=>['ZTF21aaxtctv','313761043604045880','ZTF21aaxtctv']}},
 document:{getElementById:id=>id==='image'?pane:id==='ws-image-placeholder'?placeholder:null,createElement:element},
 showTab:name=>events.push(name),w2ui:{layoutMain:{panels:[{tabs:{click:name=>events.push(name)}}]}},
 fetch:(url,opts)=>new Promise(resolve=>pending.push({url,opts,resolve})),URL,URLSearchParams,JSON,Set,AbortController};
vm.runInNewContext(fs.readFileSync(path.join(root, 'cutouts-graph.js'),'utf8'),ctx);
const cells = row => row.children.filter(child=>child.tag==='div');
async function run(){
 pane.innerHTML='<iframe src="old-fits"></iframe>';
 ctx.window.cutoutsGraphState.clear();
 assert.equal(pane.innerHTML,placeholder.innerHTML,'Reset without prior Cutouts discards legacy FITS');
 pane.innerHTML='<iframe src="old-fits"></iframe>';
 let task=ctx.window.cutoutsGraphState.open();
 assert.deepEqual(events,['image','imageTab']);
 assert.equal(pane.children.length,3); // heading + two unique object rows
 assert.equal(pending.length,1);
 assert.equal(pending[0].opts.method,'POST');
 assert.deepEqual(JSON.parse(pending[0].opts.body),{diaObjectId:'313761043604045880'});
 const ztf=pane.children[1];
 assert.equal(ztf.className,'ws-cutout-row ws-cutout-ztf');
 assert.equal(ztf.children[0].textContent,'ZTF21aaxtctv');
 assert.deepEqual(cells(ztf).map(c=>c.children[0].textContent),['Science','Template','Difference']);
 assert.deepEqual(cells(ztf).map(c=>new URL(c.children[1].src).searchParams.get('kind')),['Science','Template','Difference']);
 assert.equal(new URL(cells(ztf)[0].children[1].src).searchParams.get('objectId'),'ZTF21aaxtctv');
 pending[0].resolve({ok:true,json:async()=>({diaSourceId:'170054933726363745'})});
 await task;
 const lsst=pane.children[2];
 assert.equal(lsst.className,'ws-cutout-row ws-cutout-lsst');
 assert.deepEqual(cells(lsst).map(c=>new URL(c.children[1].src).searchParams.get('diaSourceId')),
   ['170054933726363745','170054933726363745','170054933726363745']);
 const previous=pane.children;
 task=ctx.window.cutoutsGraphState.open();
 assert.equal(pending.length,2);
 ctx.window.cutoutsGraphState.cancel();
 pending[1].resolve({ok:true,json:async()=>({diaSourceId:'999'})});
 await task;
 assert.equal(pane.children[2].children[1].children.length,1,'late lookup cannot append images');
 assert.notEqual(pane.children,previous);
 ctx.window.cutoutsGraphState.clear();
 assert.equal(pane.innerHTML,placeholder.innerHTML,
   'Reset restores the immutable image placeholder, never an earlier FITS iframe');
 ctx.window.skyGraphState.getIds=()=>Array.from({length:31},(_,i)=>'ZTF'+i);
 await ctx.window.cutoutsGraphState.open();
 assert.equal(pending.length,2,'bounded graph must not request 93 images');
 assert.match(pane.textContent,/more than 30 objects/);
 console.log('webservice Cutouts graph matrix, routing, bounds and cancellation passed');
}
run().catch(e=>{console.error(e);process.exitCode=1});

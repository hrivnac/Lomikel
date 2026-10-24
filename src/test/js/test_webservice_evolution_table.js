'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const base=path.resolve(__dirname,'../../webservice');
const legacy=fs.readFileSync(path.resolve(__dirname,'../../ws/Nodes.jsp'),'utf8');
assert.match(legacy,/onclick="showScatter\('evolution'\)"/);
const calls=[];
const checked={x:['fluxErr'],y:['flux'],z:['weight'],s:['band'],k:['objectId']};
const rows=[{time:'2026-10-24T12:00:00Z',fluxErr:'[0]',flux:'[2]',weight:'[0]',band:'[g]',objectId:'[ZTF_A]'},
  {time:'2026-10-25T12:00:00Z',fluxErr:'[1]',flux:'[3]',weight:'[4]',band:'[r]',objectId:'[ZTF_B]'}];
const win={loadPane:(...args)=>calls.push(args),tdata:rows,plotTableScalar:v=>typeof v==='string' && /^\[[^\[\],;]*\]$/.test(v.trim())?v.trim().slice(1,-1):v,
  addEventListener(){},setTimeout(){},innerWidth:800,innerHeight:600};
let tablePresent=true;
const doc={baseURI:'http://local/FinkBrowser/',getElementById:id=>id==='tbl' && tablePresent?{}:null,
 getElementsByClassName:role=>(checked[role]||[]).map(field=>({checked:true,getAttribute:()=>field}))};
const ctx={window:win,document:doc,URL,URLSearchParams,$:()=>{},Date};
vm.runInNewContext(fs.readFileSync(path.join(base,'evolution-table.js'),'utf8'),ctx);
vm.runInNewContext(fs.readFileSync(path.join(base,'workspace.js'),'utf8'),ctx);
function parseLast(){const call=calls.at(-1);return {call,query:new URL(call[1],doc.baseURI).searchParams};}
// Both x and y selections must each become their own time series, including zero.
win.loadPane('plot','d3/scatterplot.jsp?name=Import%20Data&url=&x=&y=fluxErr%20flux&tdata=[]',true,480,false);
let {call,query}=parseLast();
assert.match(call[1],/^webservice\/d3\/scatterplot\.jsp\?/);
assert.equal(call[4],true,'encoded URL is not double encoded');
assert.equal(query.get('x'),'','use time axis rather than field x');
assert.equal(query.get('timeUnit'),'ms');
const points=JSON.parse(query.get('tdata'));
assert.equal(points.length,4,'x and y each produce one point per time');
assert.deepEqual(points.map(p=>p.y),['0','2','1','3']);
assert.deepEqual(points.map(p=>p.yField),['fluxErr','flux','fluxErr','flux']);
assert.deepEqual(points.map(p=>p.g),['g','g','r','r'],'s remains the color grouping');
assert.deepEqual(points.map(p=>p.z),['0','0','4','4']);
assert.equal(points[0].k,'objectId=ZTF_A');
assert.equal(points[0].t,Date.parse(rows[0].time));
win.tdata=[{time:'1792843200',flux:'[0]'}];
checked.x=[];checked.y=['flux'];
win.loadPane('plot','d3/scatterplot.jsp?x=&y=flux&tdata=[]',true,480,false);
assert.equal(JSON.parse(parseLast().query.get('tdata'))[0].t,1792843200000,
  'epoch seconds normalize to milliseconds while zero values survive');
// A single selected field works; no time means no spurious plotted point.
calls.length=0;checked.x=[];checked.y=['flux'];win.tdata=[rows[0],{...rows[1],time:''}];
win.loadPane('plot','d3/scatterplot.jsp?x=&y=flux&tdata=[]',true,480,false);
assert.deepEqual(JSON.parse(parseLast().query.get('tdata')).map(p=>p.y),['2']);
// A table without the requested time column keeps the old route.
calls.length=0;win.tdata=[{jd:'[2460973.0]',flux:'[5]'}];
win.loadPane('plot','d3/scatterplot.jsp?x=&y=flux&tdata=[]',true,480,false);
assert.equal(calls[0][1],'d3/scatterplot.jsp?x=&y=flux&tdata=[]');
// Non-evolution Scatter and HBase evolution retain existing routes.
calls.length=0;win.loadPane('plot','d3/scatterplot.jsp?x=flux&y=flux&tdata=[]',true,480,false);
assert.equal(calls[0][1],'d3/scatterplot.jsp?x=flux&y=flux&tdata=[]');
tablePresent=false;
win.loadPane('plot','d3/scatterplot.jsp?x=&y=flux&tdata=[]',true,480,false);
assert.equal(calls[1][1],'d3/scatterplot.jsp?x=&y=flux&tdata=[]','HBase form lacks #tbl');
console.log('Webservice Table evolution routes both selected series over time');

'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const root=path.resolve(__dirname,'../../webservice/d3');
const code=fs.readFileSync(path.join(root,'skyview.js'),'utf8');
const draws=[], mapCalls=[];
let layer, features=[], activeProjection;
const canvas={width:900,height:450,style:{width:'450px',height:'225px'},addEventListener(){},
  getBoundingClientRect:()=>({left:0,top:0,width:450,height:225})};
function el(tag){return {tag,children:[],style:{},textContent:'',hidden:false,clientWidth:500,clientHeight:300,
  appendChild(x){this.children.push(x)},addEventListener(){},setAttribute(){},
  getBoundingClientRect(){return {left:0,top:0,width:450,height:225}},
  querySelector(){return canvas}};}
const summary=el('p'), list=el('div'), legend=el('span'), stage=el('div'), settings=el('button');
const elements={'sky-summary':summary,'sky-list':list,'sky-legend':legend,'celestial-map':stage,
  'sky-settings-toggle':settings,'sky-coordinates':el('details')};
const document={getElementById:id=>elements[id],createElement:el,createDocumentFragment:()=>el('fragment'),
  body:{classList:{toggle:()=>false}}};
const selection={data(items){features=items;return this},enter(){return this},append(){return this},
  attr(){return this},each(fn){features.forEach(p=>fn.call({},p));return this}};
const context={canvas,beginPath(){draws.push({begin:true})},moveTo(x,y){draws.push({move:[x,y]})},
  lineTo(x,y){draws.push({line:[x,y]})},arc(x,y,r){draws.push({arc:[x,y,r]})},
  closePath(){},stroke(){draws.push({stroke:true})},fill(){},save(){},restore(){},
  measureText:t=>({width:t.length*4}),strokeText(){},fillText(t){draws.push({label:t})}};
function map(line) {
  mapCalls.push(line);
  const a=activeProjection(line.coordinates[0]), b=activeProjection(line.coordinates[1]);
  const width=450;
  context.moveTo(...a);
  if(Math.abs(b[0]-a[0])>width/2){
    context.lineTo(b[0]>a[0]?0:width,a[1]);
    context.moveTo(b[0]>a[0]?width:0,b[1]);
  }
  context.lineTo(...b);
}
map.projection=()=>activeProjection;
const Celestial={container:{selectAll:()=>selection},add(x){layer=x},getData:collection=>collection,
  display(){layer.callback(null)},redraw(){layer.redraw()},clip:()=>true,
  map,setStyle(){},resize(){},context};
const window={addEventListener(){}};
vm.runInNewContext(code,{window,document,Celestial,Set,Map,Number});
const history=[{objectId:'123456',sources:[
  {sourceId:'90071992547409930',mjd:12,ra:1,dec:0},
  {sourceId:'90071992547409929',mjd:11,ra:359,dec:0},
  {sourceId:'90071992547409931',mjd:13,ra:1.2,dec:0}],esMjdAvailable:false}];
activeProjection=([ra])=>({359:[440,90],1:[10,92],1.2:[11,93],100:[200,60]})[ra];
window.skyRender([{objectId:'ZTFname',ra:100,dec:0}],2,0,history);
assert.match(summary.textContent,/4 sky positions/);
assert.match(summary.textContent,/1 trajector/);
assert.match(summary.textContent,/1 trajectory without an Elasticsearch MJD cross-check/);
const lines=draws.filter(d=>d.line).map(d=>d.line);
assert.ok(lines.length>=3,'seam-crossing history splits into visible segments instead of a canvas-wide line');
assert.equal(mapCalls.length,2,'each chronological link is delegated to Celestial geoPath for active-projection clipping');
assert.ok(draws.filter(d=>d.move).length>=2);
const circles=draws.filter(d=>d.arc).map(d=>d.arc);
assert.equal(circles.length,4,'source observations and static DIA/ZTF marker are all plotted');
assert.ok(circles[1][2]<4 && circles[2][2]<1.5,'dense historical markers shrink below the fixed minimum');
assert.ok(circles[3][2]>=circles[2][2] && circles[3][2]<2.5,
  'latest marker stays distinct without hiding a close link');
assert.equal(draws.filter(d=>d.label).length,2,'one label per object, not one per historical source');
const listed=list.children[0].children.map(x=>x.textContent).join(' ');
assert.match(listed,/90071992547409929/);
assert.match(listed,/MJD \(TAI\)/);
activeProjection=([ra])=>({359:[220,100],1:[225,102],1.2:[227,103],100:[200,60]})[ra];
draws.length=0;Celestial.redraw();
assert.deepEqual(draws.filter(d=>d.arc).map(d=>d.arc[0]),[200,220,225,227],
  'positions and trajectories use the current projection on pan/change');
assert.ok(draws.filter(d=>d.line).every(d=>d.line[0]>=200 && d.line[0]<250),
  'paths follow the changed projection, without stale full-width bridges');
Celestial.clip=([ra])=>ra!==359;
mapCalls.length=0;draws.length=0;Celestial.redraw();
assert.equal(draws.filter(d=>d.arc).length,3,'clipped source marker is not painted');
assert.equal(mapCalls.length,2,
  'active geoPath still draws the visible part of a segment with a clipped endpoint');
console.log('SkyView SS chronology, seam, adaptive markers and active projection passed');

'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../../webservice/d3');
const jsCode = fs.readFileSync(path.join(root, 'skyview.js'), 'utf8');
const events = {};
const draws = [];
const points = [{objectId:'ZTF21aaxtctv',ra:10,dec:0},
  {objectId:'313761043604045880',ra:20,dec:1},
  {objectId:'ZTFbottom',ra:30,dec:2}];
const canvas = {width:800,height:400,style:{width:'400px',height:'200px'},
 getBoundingClientRect:()=>({left:20,top:40,width:300,height:150}),
 addEventListener:(type,handler)=>events[type]=handler};
function el(tag) { return {tag,children:[],style:{},attributes:{},textContent:'',hidden:false,
  appendChild(child){this.children.push(child);return child},addEventListener:(type,handler)=>events['element-'+type]=handler,
  setAttribute(k,v){this.attributes[k]=v},getBoundingClientRect:()=>({left:10,top:20,width:420,height:230})}; }
const summary=el('p'), list=el('div'), settings=el('button'), coordinates=el('details'), legend=el('span');
const stage=el('div');stage.clientWidth=500;stage.clientHeight=300;
stage.querySelector=selector=>selector==='canvas'?canvas:null;
const elements={'sky-summary':summary,'sky-list':list,'sky-settings-toggle':settings,'sky-legend':legend,
 'celestial-map':stage,'sky-coordinates':coordinates};
const document={getElementById:id=>elements[id]||null,createElement:el,
 createDocumentFragment:()=>el('fragment'),body:{classList:{toggle:()=>false}}};
let features=[], layer;
const aitoff=([ra])=>ra===10?[2,80]:ra===20?[390,80]:[200,198];
let activeProjection=aitoff;
const selection={data(items){features=items;return this},enter(){return this},append(){return this},
 attr(){return this},each(fn){features.forEach(p=>fn.call({},p));return this}};
const Celestial={container:{selectAll:()=>selection},add:item=>layer=item,
  getData:collection=>collection,display(){layer.callback(null)},redraw(){layer.redraw()},
  clip:()=>true,mapProjection:aitoff,map:{projection:()=>activeProjection},
  setStyle:style=>draws.push({style}),resize(){},context:{
   beginPath(){},arc(x,y){draws.push({arc:[x,y]})},closePath(){},stroke(){},fill(){},
   canvas:canvas,measureText:text=>({width:text.length*5}),
   textAlign:'center',save(){},restore(){},strokeText(text,x,y){draws.push({halo:[text,x,y]})},
   fillText(text,x,y){draws.push({label:[text,x,y],align:this.textAlign})}
  }};
const window={addEventListener(){}};
vm.runInNewContext(jsCode,{window,document,Celestial,Set,Number});
window.skyRender(points,3,0);
assert.deepEqual(draws.filter(d=>d.label).map(d=>d.label[0]),points.map(p=>p.objectId),
  'each object id is drawn next to its visible point');
assert.ok(draws.filter(d=>d.label)[1].label[1] < 390,
  'label at the right edge is shifted left of its point rather than clipped');
assert.ok(draws.filter(d=>d.label)[2].label[2] <= 193,
  'label at the bottom edge remains within the logical canvas height');
assert.ok(draws.filter(d=>d.label).every(d=>d.align==='left'),
  'Celestial center-aligned star names must not shift object labels off the edge');
assert.equal(new Set(draws.filter(d=>d.style).map(d=>d.style.fill)).size,2,
  'ZTF and LSST points have different colors');
assert.equal(typeof events.pointermove,'function');
events.pointermove({clientX:21.5,clientY:100}); // projected first point, DPR 2 plus CSS scaling
const popup=stage.children.find(child=>child.className==='sky-hover');
assert.ok(popup,'hover tooltip is attached to map');
assert.equal(popup.textContent,'ZTF21aaxtctv');
assert.equal(popup.hidden,false);
events.pointermove({clientX:312.5,clientY:100});
assert.equal(popup.textContent,'313761043604045880');
events.pointermove({clientX:330,clientY:170});
assert.equal(popup.hidden,true,'tooltip hides away from points');
events.pointerleave();
assert.equal(popup.hidden,true);
activeProjection=([ra])=>ra===10?[45,70]:ra===20?[280,90]:[170,160];
draws.length=0;
Celestial.redraw(); // Celestial replaces its internal projection on switching to Mercator
assert.deepEqual(draws.filter(d=>d.arc).map(d=>d.arc),[[45,70],[280,90],[170,160]],
  'point positions follow the active Mercator projection, not the original Aitoff reference');
events.pointermove({clientX:53.75,clientY:92.5});
assert.equal(popup.textContent,'ZTF21aaxtctv','hover targets follow the projected points');
activeProjection=([ra])=>ra===10?[80,70]:ra===20?[260,90]:[150,160];
draws.length=0;
Celestial.redraw(); // panning after the projection switch
assert.deepEqual(draws.filter(d=>d.arc)[0].arc,[80,70],
  'point positions follow the panned sky in the new projection');
assert.match(fs.readFileSync(path.join(root,'skyview.css'),'utf8'),/\.sky-hover\s*\{/);
assert.match(fs.readFileSync(path.join(root,'skyview.jsp'),'utf8'),/skyview\.js\?v=longss-history-1/);
console.log('SkyView draws IDs, distinct survey colors and point hover tooltips');

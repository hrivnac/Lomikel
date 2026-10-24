'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname,'../../webservice/d3/correlogram.js'),'utf8');
const ctx = vm.createContext({});
vm.runInContext(source,ctx);
const prepared = ctx.corrPrepare([
  {x:'Alpha.Long.Classification',y:'Beta',value:'12345678',info:'40000000/50000000'},
  {x:'Beta',y:'Gamma',value:'2',info:'30/50'}
]);
assert.deepEqual(Array.from(prepared.names),['Alpha.Long.Classification','Beta','Gamma']);
assert.equal(prepared.cells.get('Beta\u0000Alpha.Long.Classification').value,12345678);
assert.equal(prepared.cells.get('Alpha.Long.Classification\u0000Beta').value,12345678);
assert.equal(prepared.cells.get('Beta\u0000Alpha.Long.Classification').firstName,'Alpha.Long.Classification');
assert.equal(prepared.cells.get('Beta\u0000Alpha.Long.Classification').secondName,'Beta');
assert.equal(prepared.cells.get('Alpha.Long.Classification\u0000Gamma'),undefined);
assert.equal(ctx.corrRadius(0),0);
assert.ok(ctx.corrRadius(0.1)>0 && ctx.corrRadius(0.1)<ctx.corrRadius(3));
assert.ok(ctx.corrRadius(3)<ctx.corrRadius(30) && ctx.corrRadius(30)<ctx.corrRadius(100));
assert.ok(ctx.corrRadius(100)<=23);
assert.equal(ctx.corrCompact(12345678),'12.3M');
assert.equal(ctx.corrCompact(0.00004),'4e-5');
assert.ok(ctx.corrFontSize('Alpha.Long.Classification',52)<ctx.corrFontSize('Beta',52));
assert.equal(ctx.corrEmptyMessage(true),'Cannot display malformed correlogram data.');
assert.equal(ctx.corrEmptyMessage(false),'No class pairs to display.');
assert.equal(ctx.corrPrepare([{x:'A',y:'B',value:'',info:'3/4'}]).cells.get('A\u0000B').valid,false);
assert.equal(ctx.corrPrepare([{x:'A',y:'B',value:'1e307',info:'1e307/1e307'}]).cells.get('A\u0000B').percent,100);
assert.equal(ctx.corrPrepare([{x:'A',y:'B',value:'1e308',info:'1e308/1e308'}]).cells.get('A\u0000B').valid,false);
assert.deepEqual(Array.from(ctx.corrTooltipPoint({type:'focus',target:{getBoundingClientRect:()=>({left:120,top:70,width:40,height:40})}},
  {getBoundingClientRect:()=>({left:100,top:60})})),[40,30]);
let opens = 0;
let popup = {closed:false};
assert.equal(ctx.corrGetPopup(()=>{opens++;return popup;}).fresh,true);
assert.equal(ctx.corrGetPopup(()=>{opens++;return popup;}).fresh,false);
assert.equal(opens,1,'reuse the existing Venn document instead of navigating it');
popup.closed = true;
assert.equal(ctx.corrGetPopup(()=>{opens++;return {closed:false};}).fresh,true);
assert.equal(opens,2);
assert.match(source,/\.attr\('textLength'/);
assert.match(source,/\.on\('click'/);
assert.doesNotMatch(source,/\.attr\('role','img'\)/);
console.log('Webservice correlogram data and presentation tests passed');

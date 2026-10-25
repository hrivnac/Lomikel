'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const code=fs.readFileSync(path.resolve(__dirname,'../../webservice/d3/lightcurve.js'),'utf8');
const ctx={window:{addEventListener(){}},Number,Set};
vm.runInNewContext(code,ctx);
const ztf={survey:'ZTF',objectId:'ZTF21aaxtctv',totalRows:2,excluded:0,points:[
 {sourceId:'1782552085315015008',band:'r',mjd:59536.552,magnitude:19.1,magnitudeError:.17},
 {sourceId:'1782552085315015007',band:'g',mjd:59535.552,magnitude:19.6,magnitudeError:.2}]};
const result=ctx.window.lightCurvePrepare(ztf,'ZTF21aaxtctv');
assert.deepEqual(Array.from(result.bands),['g','r']);
assert.deepEqual(Array.from(result.points,p=>p.sourceId),['1782552085315015007','1782552085315015008']);
const lsst={survey:'LSST',objectId:'313761043604045880',totalRows:7,excluded:1,points:
 'ugrizy'.split('').map((band,i)=>({sourceId:String(170666304476282943n+BigInt(i)),band,mjd:61000+i,magnitude:21+i*.1,magnitudeError:.1}))};
const curve=ctx.window.lightCurvePrepare(lsst,'313761043604045880');
assert.deepEqual(Array.from(curve.bands),['u','g','r','i','z','y']);
assert.equal(curve.excluded,1);
assert.throws(()=>ctx.window.lightCurvePrepare(lsst,'313761043604045881'),/object/);
assert.throws(()=>ctx.window.lightCurvePrepare({...lsst,points:[{...lsst.points[0],band:'X'}]},lsst.objectId),/point|band|count/);
assert.throws(()=>ctx.window.lightCurvePrepare({...ztf,totalRows:10001},ztf.objectId),/count|limit/);
assert.deepEqual(Array.from(ctx.window.lightCurvePrepare({...ztf,points:[],totalRows:0},ztf.objectId).bands),[]);
const jsp=fs.readFileSync(path.resolve(__dirname,'../../webservice/d3/lightcurve.jsp'),'utf8');
assert.match(jsp,/lightcurve\.js\?v=lightcurve-/);
assert.match(jsp,/d3-v6\.0\.0\/d3\.js/);
console.log('LightCurve payload validation, six-band availability and exact IDs passed');

'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const code = fs.readFileSync(path.resolve(__dirname,'../../webservice/d3/skyview.js'),'utf8');
const window = {};
vm.runInNewContext(code,{window,document:{getElementById:()=>null}});
const input=[{objectId:'ZTF-test',ra:0,dec:0},{objectId:'12',ra:359.5,dec:-90},
             {objectId:'bad',ra:NaN,dec:0},{objectId:'x',ra:360,dec:4},
             {objectId:'<img src=x onerror=alert(1)>',ra:2,dec:3}];
const points=window.skyPrepare(input);
assert.equal(points.length,3);
assert.equal(points[0].ra,0);
assert.equal(points[1].dec,-90);
assert.equal(points[2].objectId,'<img src=x onerror=alert(1)>');
assert.deepEqual(Array.from(window.skyPrepare([])),[]);
console.log('SkyView point validation preserves zero, range and plain-text labels');

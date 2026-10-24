'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const base = path.join(__dirname, '..');
let calls=[];
const window = {loadPane:function(...args){calls.push(args);return 'loaded';},
  tdata:[{fluxErr:'[2]', flux:'[3]', sizeOut:'[7]'}],
  plotTableScalar:v=>/^\[[^,]*\]$/.test(v)?v.slice(1,-1):v};
vm.runInNewContext(fs.readFileSync(path.join(base,'workspace.js'),'utf8'),
  {window, $:function(){}, document:{baseURI:'http://localhost/FinkBrowser/'}, URL});
const params = new URL('http://localhost/FinkBrowser/d3/scatterplot.jsp');
params.searchParams.set('name','A B');
params.searchParams.set('x','fluxErr flux');
params.searchParams.set('y','sizeOut');
params.searchParams.set('tdata',JSON.stringify([{x:'2',y:'7',g:'fluxErr/sizeOut'},
  {x:'3',y:'7',g:'flux/sizeOut'}]));
window.loadPane('plot','d3/scatterplot.jsp'+params.search,true,480,true);
window.loadPane('plot','d3/correlogram.jsp?tdata=%5B%5D',true,480);
window.loadPane('result','d3/scatterplot.jsp?name=X',false);
window.loadPane('plot','d3/scatterplot.jsp?name=Legacy',true,480,false);
assert.match(calls[0][1], /^webservice\/d3\/scatterplot\.jsp\?/);
assert.deepEqual(JSON.parse(new URL(calls[0][1],'http://localhost/FinkBrowser/').searchParams.get('tdata'))
  .map(p=>[p.xField,p.yField]),[['fluxErr','sizeOut'],['flux','sizeOut']]);
assert.equal(calls[0][4],true);
assert.equal(calls[1][1], 'd3/correlogram.jsp?tdata=%5B%5D');
assert.equal(calls[2][1], 'd3/scatterplot.jsp?name=X');
assert.equal(calls[3][1], 'd3/scatterplot.jsp?name=Legacy');
assert.match(fs.readFileSync(path.join(base,'index.jsp'),'utf8'), /webservice\/workspace\.js\?v=scatter-details-1/);
assert.match(fs.readFileSync(path.join(base,'d3/scatterplot.jsp'),'utf8'), /scatterplot\.js\?v=details-1/);
console.log('Webservice-only scatter routing tests passed');

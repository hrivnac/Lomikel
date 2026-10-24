'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const base = path.join(__dirname, '../../webservice/d3');
const context = vm.createContext({d3:{timeFormat:()=>date=>date.toISOString()}});
vm.runInContext(fs.readFileSync(path.join(base, 'scatterplot.js'), 'utf8'), context);
const p = {x:2, y:7, z:7, g:'Class A',
  k:'pften=a value with spaces sizeOut=7 pften=a value with spaces', raw:{}};
assert.deepEqual(Array.from(context.scatterPlotInfoLines(p, true, 'sizeIn', 'sizeOut', 'sizeOut', 'class')),
  ['pften - a value with spaces', 'sizeOut - 7', 'sizeIn - 2', 'class - Class A']);
assert.equal(context.scatterPlotInfoLines({x:1,y:2,z:null,g:'',k:'',raw:{}},true,'x','y','','').join('\n'),
  'x - 1\ny - 2');
assert.equal(context.scatterPlotInfoLines({x:1730000000000,y:0,z:null,g:'',k:'',raw:{}},false,'','flux','','')[0].startsWith('Time - '),true);
assert.equal(context.scatterPlotInfoLines({x:3,y:7,z:null,g:'Class A',k:'',raw:{xField:'flux',yField:'sizeOut'}},true,
  'fluxErr flux','sizeOut','','class').join('\n'),
  'flux - 3\nsizeOut - 7\nclass - Class A');
assert.equal(context.scatterPlotInfoLines({x:2,y:7,z:null,g:'',k:'sizeOut=7',raw:{xField:'fluxErr',yField:'sizeOut'}},true,
  'fluxErr flux','sizeOut','','').join('\n'), 'sizeOut - 7\nfluxErr - 2');
const js = fs.readFileSync(path.join(base, 'scatterplot.js'), 'utf8');
assert.notEqual(context.scatterPlotSeriesDash('fluxErr',['fluxErr','flux']),
  context.scatterPlotSeriesDash('flux',['fluxErr','flux']),
  'two time series remain visually distinct even with the same s color');
assert.match(js, /stroke-dasharray/);
const css = fs.readFileSync(path.join(base, 'scatterplot.css'), 'utf8');
assert.doesNotMatch(js, /append\('div'\)\.attr\('class', 'scatter-legend'\)/);
assert.doesNotMatch(css, /\.scatter-legend/);
assert.match(css, /white-space:\s*pre-line/);
console.log('Webservice scatter details and legend tests passed');

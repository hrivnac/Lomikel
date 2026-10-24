/* Scatter plot's data and DOM contracts without a live backend. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const code = fs.readFileSync(path.join(__dirname, '../../ws/d3/scatterplot.js'), 'utf8');
const css = fs.readFileSync(path.join(__dirname, '../../ws/d3/scatterplot.css'), 'utf8');
assert.match(css, /\.scatterplot\s*\{[^}]*width:\s*100%/);
assert.match(css, /\.scatter-tip/);
assert.match(css, /\.scatter-legend/);
const context = vm.createContext({});
vm.runInContext(code, context);

const points = context.scatterPlotPrepare([
  {x:'0',y:'2',z:'0',g:'red group',k:'A & B'},
  {x:'3',y:'4',z:'12',g:'blue group',k:'second'},
  {x:'bad',y:'4'}, {x:'3',y:'Infinity'}, {x:'',y:'2'}
], true);
assert.equal(points.length, 2, 'zero is a valid coordinate; invalid points are skipped');
assert.equal(points[0].x, 0);
assert.equal(points[0].z, 0);
assert.equal(points[0].g, 'red group');
assert.equal(points[0].k, 'A & B');
assert.equal(context.scatterPlotPrepare([{t:'1730000000000',y:'0',g:'time'}], false).length, 1,
  'evolution plot accepts timestamp and zero y');
assert.equal(context.scatterPlotPrepare([{t:'bad',y:'1'}], false).length, 0);
assert.equal(context.scatterPlotPrepare([], true).length, 0);
assert.equal(context.scatterPlotActionKey({k:'a & b',g:'group',x:123,raw:{}},true), 'a%20%26%20b%20group');
assert.equal(context.scatterPlotActionKey({k:'a & b',g:'group',x:123,raw:{}},false), 'a%20%26%20b%20123');
console.log('Scatter plot data tests passed');

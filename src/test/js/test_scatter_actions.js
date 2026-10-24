/* Plot callback strings must remain inert when inserted into legacy innerHTML. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.join(__dirname, '../../ws/d3');
const ctx = vm.createContext({});
vm.runInContext(fs.readFileSync(path.join(root, 'scatterplot.js'), 'utf8'), ctx);
vm.runInContext(fs.readFileSync(path.join(root, 'actions.js'), 'utf8'), ctx);
assert.equal(ctx.scatterPlotEscape('A <img src=x onerror=alert(1)> & B'),
  'A &lt;img src=x onerror=alert(1)&gt; &amp; B');
const safe = ctx.actions('Result.jsp?name="\'><img src=x onerror=alert(1)>','a"\'><svg onload=alert(1)>');
assert.doesNotMatch(safe, /<img|<svg|onclick='[^']*<|"\s*onerror=/i);
assert.match(safe, /&lt;img/);
assert.match(ctx.actions('Result.jsp?foo=bar','safe'), /loadPane/);
console.log('Scatter action HTML safety tests passed');

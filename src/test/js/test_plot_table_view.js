/* Expanded graph-table properties retain accessible plot selectors. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const code = fs.readFileSync(path.join(__dirname, '../../webservice/TableView.js'), 'utf8');
const css = fs.readFileSync(path.join(__dirname, '../../webservice/Nodes.css'), 'utf8');
assert.match(css, /\.plot-properties/);
for (const color of ['lightblue', 'lightgreen', 'lightpink', 'yellow', 'darkblue', 'darkgreen', 'darkred', 'brown']) {
  assert.ok(css.includes(color), `preserve existing selector color ${color}`);
}
const context = vm.createContext({});
vm.runInContext(code, context);

const row = {flux: '0', 'family:column': '<img src=x onerror=alert(1)>', note: 'A & B'};
const html = context.plotTableDetailFormatter(2, row);
assert.match(html, /plot-field-name/);
assert.match(html, /plot-field-value/);
assert.match(html, /plot-roles/);
assert.match(html, /data-plot-field="family:column"/);
assert.match(html, /&lt;img src=x onerror=alert\(1\)&gt;/);
assert.doesNotMatch(html, /<img/);
assert.match(html, /A &amp; B/);
for (const role of ['x', 'y', 'z', 's', 'k']) {
  assert.match(html, new RegExp('class="' + role + '"[^>]*>'));
  assert.match(html, new RegExp('title="' + ({x:'Horizontal axis', y:'Vertical axis', z:'Symbol size', s:'Color group', k:'Point details'})[role] + '"'));
}
const later = context.plotTableDetailFormatter(3, row);
const ids = [...html.matchAll(/id="([^"]+)"/g)].map(m => m[1]);
assert.equal(new Set(ids).size, ids.length, 'checkbox ids are unique within a row');
assert.ok(!later.includes('id="' + ids[0] + '"'), 'expanded rows have distinct input ids');
assert.equal(context.plotTableFieldName({getAttribute: () => 'family:column'}), 'family:column');
assert.equal(context.plotTableEscape("' & < > \""), '&#39; &amp; &lt; &gt; &quot;');
const plotUrl = context.plotTableScatterURL('A & B','x','y','z','s','note',[
  {x:0,y:4,z:2,s:'Class & A',note:'one "quoted"'}, {x:3,y:null,s:'skip'}]);
const plotParams = new URLSearchParams(plotUrl.split('?')[1]);
assert.equal(plotParams.get('name'),'A & B');
assert.deepEqual(JSON.parse(plotParams.get('tdata')),[{x:0,y:4,z:2,g:'Class & A',k:'note=one "quoted"'}]);
const wrapped = context.plotTableScatterURL('overlaps','sizeIn','sizeOut','intersection','cls','',[
  {sizeIn:'[12]',sizeOut:'[9]',intersection:'[4]',cls:'[Class A]'}]);
assert.deepEqual(JSON.parse(new URLSearchParams(wrapped.split('?')[1]).get('tdata')),
  [{x:'12',y:'9',z:'4',g:'Class A'}]);
console.log('Graph table property selector tests passed');

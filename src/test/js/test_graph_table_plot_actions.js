/* The graph table's existing Plot actions must survive selector layout changes. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.join(__dirname, '../../webservice');
for (const page of ['Nodes.jsp', 'Edges.jsp']) {
  const source = fs.readFileSync(path.join(root, page), 'utf8');
  const script = source.match(/<script>\s*var \$table[\s\S]*?<\/script>/)?.[0]
    .replace(/^<script>|<\/script>$/g, '').replaceAll('<%=timestampField%>', 'timestamp');
  assert.ok(script, `missing ${page} plot script`);
  const selected = {x:'sizeIn', y:'sizeOut', z:'intersection', s:'classifier', k:'name'};
  const calls = [];
  const context = vm.createContext({
    document:{getElementById:()=>({createTHead:()=>({})}),
      getElementsByClassName:role=>selected[role] ?
        (Array.isArray(selected[role]) ? selected[role] : [selected[role]])
          .map(field=>({checked:true,getAttribute:()=>field})) : []},
    $:()=>({bootstrapTable(){}}),
    plotTableDetailFormatter:()=>'', plotTableFieldName:input=>input.getAttribute('data-plot-field'),
    window:{alert:message=>{throw Error(message)}},
    loadPane:(...args)=>calls.push(args), tdata:[
      {sizeIn:'0',sizeOut:'10',intersection:'2',classifier:'Class & A',name:'first "quoted"'},
      {sizeIn:'25',sizeOut:'15',intersection:'5',classifier:'Class B',name:'second'}],
    tit:'Overlaps', header:'<tr></tr>', console
  });
  vm.runInContext(fs.readFileSync(path.join(root, 'TableView.js'), 'utf8'), context);
  vm.runInContext(script, context);
  context.showScatter('scatter');
  assert.equal(calls[0][0],'plot');
  assert.equal(calls[0][4],true,'encoded scatter query must not be double-encoded by loadPane');
  const url = calls[0][1];
  assert.ok(url.startsWith('d3/scatterplot.jsp?'));
  const params = new URLSearchParams(url.split('?')[1]);
  assert.equal(params.get('x'),'sizeIn');
  assert.equal(params.get('y'),'sizeOut');
  const points = JSON.parse(params.get('tdata'));
  assert.equal(points.length,2,'zero x remains a plotted value');
  assert.equal(points[0].g,'Class & A','s selects the point color group');
  assert.match(points[0].k,/name=first "quoted"/);
  selected.x = ['fluxErr','flux'];
  selected.z = selected.s = selected.k = '';
  context.tdata = [{fluxErr:'1',flux:'2',sizeOut:'10'}];
  calls.length = 0;
  context.showScatter('scatter');
  const nested = new URLSearchParams(calls[0][1].split('?')[1]);
  assert.equal(nested.get('x'),'fluxErr flux');
  assert.equal(JSON.parse(nested.get('tdata')).length,2,
    'prefix-related fields each produce a point');
  if (page === 'Edges.jsp') {
    calls.length = 0;
    context.showCorrelogramFT();
    assert.ok(calls[0][1].startsWith('d3/correlogram.jsp?'));
  }
}
console.log('Graph table to plot action tests passed');

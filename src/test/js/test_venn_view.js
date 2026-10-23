/* Venn statistics and layout regression fixtures (no graph backend). */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../../ws/d3/venn.js'), 'utf8');

function fixture() {
  const state = {options:{}, sets:null, target:null};
  const ctx = vm.createContext({
    venn:{VennDiagram:()=>{
      const chart = ()=>{};
      for (const property of ['orientation','width','height','padding','fontSize']) {
        chart[property] = value=>{state.options[property]=value;return chart};
      }
      return chart;
    }},
    d3:{select:target=>({datum(sets) {state.target=target;state.sets=sets;return this},
      call(chart) {chart();return this}, classed(name, enabled) {state.classes = state.classes || {}; state.classes[name]=enabled;return this},
      select() {return {attr() {return this}}}, selectAll() {return {remove() {state.removed=true}}}})}
  });
  vm.runInContext(source, ctx);
  return {showVenn:ctx.showVenn, state};
}

function row(html, symbol) {
  const escaped = symbol.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const match = html.match(new RegExp('<tr[^>]*>\\s*<t[dh][^>]*>\\s*'+escaped+'\\s*</t[dh]>\\s*<td[^>]*>([^<]*)</td>\\s*<td[^>]*>([^<]*)</td>', 'i'));
  assert.ok(match, `missing aligned ${symbol} row`);
  return match.slice(1);
}

function testCountsAndPercentages() {
  const {showVenn, state} = fixture();
  const html = showVenn(12, 9, 4, 'Long:classifier name', 'Second:classifier name');
  assert.deepEqual(row(html,'A'), ['12','70.6%']);
  assert.deepEqual(row(html,'B'), ['9','52.9%']);
  assert.deepEqual(row(html,'A ∩ B'), ['4','23.5%']);
  assert.deepEqual(row(html,'A only'), ['8','47.1%']);
  assert.deepEqual(row(html,'B only'), ['5','29.4%']);
  assert.deepEqual(row(html,'A ∪ B'), ['17','100.0%']);
  assert.equal(state.options.orientation, 0, 'circles should stack vertically');
  assert.ok(state.options.height > state.options.width);
  assert.deepEqual(Array.from(state.sets, s=>s.sets.join(',')), ['A','B','A,B'],
    'compact diagram labels avoid long text collisions; names remain in legend');
  assert.match(html, /Long:classifier name/);
  assert.match(html, /Second:classifier name/);
}

function testFractionalAndEmptyCounts() {
  const {showVenn} = fixture();
  const html = showVenn('0.3', '0.2', '0.1', 'A <img src=x>', 'B');
  assert.deepEqual(row(html,'A only'), ['0.2','50.0%']);
  assert.deepEqual(row(html,'B only'), ['0.1','25.0%']);
  assert.doesNotMatch(html, /<img/);
  const empty = showVenn(0,0,0,'empty A','empty B');
  assert.deepEqual(row(empty,'A'), ['0','—']);
  assert.doesNotMatch(empty, /NaN|Infinity/);
}

function testPopupUsesSameRenderer() {
  const {showVenn, state} = fixture();
  const popup = {document:{getElementById:id=>({id})}};
  showVenn(100, 25, 10, 'A','B',popup);
  assert.equal(state.target.id, 'vennPopup');
}

function testContainedSetsHideCoincidentLabels() {
  const {showVenn, state} = fixture();
  const html = showVenn(10,10,10,'Classifier A','Classifier B');
  assert.equal(state.classes?.['venn-contained'], true);
  assert.match(html, /A and B contain the same objects/);
  assert.deepEqual(row(html,'A ∪ B'), ['10','100.0%']);
  assert.match(showVenn(2,4,2,'A','B'), /A is contained in B/);
  assert.equal(state.classes?.['venn-contained'], true);
  showVenn(10,9,4,'A','B');
  assert.equal(state.classes?.['venn-contained'], false);
}

function testTinyCountsRemainVisible() {
  const {showVenn} = fixture();
  const html = showVenn(0.001, 0.001, 0.0005, 'A','B');
  assert.deepEqual(row(html,'A'), ['0.001','66.7%']);
  assert.deepEqual(row(html,'A ∩ B'), ['0.0005','33.3%']);
  assert.deepEqual(row(showVenn(1e-8,1e-8,0,'A','B'),'A'), ['1.00e-8','50.0%']);
}

function testOverflowIsUnavailableNotNaN() {
  const {showVenn, state} = fixture();
  const html = showVenn(1e308,1e308,0,'A','B');
  assert.match(html,/Overlap counts are unavailable/);
  assert.doesNotMatch(html,/NaN|Infinity/);
  assert.equal(state.removed,true);
  assert.equal(state.sets,null);
}

testCountsAndPercentages();
testFractionalAndEmptyCounts();
testPopupUsesSameRenderer();
testContainedSetsHideCoincidentLabels();
testTinyCountsRemainVisible();
testOverflowIsUnavailableNotNaN();
console.log('Venn view tests passed');

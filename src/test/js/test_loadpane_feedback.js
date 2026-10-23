'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../../ws/LoadPane.js'), 'utf8');

async function testPaneLoadLogsTextNotMarkup() {
  const events = [];
  const feedback = {innerHTML:'', appendChild: node => events.push(node), scrollHeight:20, scrollTop:0};
  const pane = {innerHTML:''};
  const ctx = vm.createContext({
    document:{getElementById:id=>({feedback,top:pane})[id]||null,
      createTextNode:text=>({type:'text',text}), createElement:tag=>({type:tag})},
    $:()=>({load:()=>{}}), sleep:async()=>{}, console, encodeURI
  });
  vm.runInContext(source, ctx);
  await ctx.loadPane('top', 'Top.jsp?name=<img onerror=alert(1)>');
  assert.equal(feedback.innerHTML, '');
  assert.deepEqual(events.map(e=>e.type), ['text','br']);
  assert.equal(events[0].text, 'Loading top : Top.jsp?name=<img onerror=alert(1)>');
}

testPaneLoadLogsTextNotMarkup().then(()=>console.log('LoadPane feedback test passed'),
  error=>{console.error(error);process.exitCode=1});

'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../../webservice/LoadPane.js'), 'utf8');
const activity = fs.readFileSync(path.join(__dirname, '../../webservice/activity.js'), 'utf8');

async function testPaneLoadLogsTextNotMarkup() {
  const events = [];
  const feedback = {innerHTML:'', appendChild: node => events.push(node), scrollHeight:20, scrollTop:0};
  const pane = {innerHTML:''};
  const ctx = vm.createContext({
    document:{getElementById:id=>({feedback,top:pane})[id]||null,
      createTextNode:text=>({type:'text',text}), createElement:tag=>({type:tag,children:[],
        appendChild(child){this.children.push(child)}})},
    $:()=>({load:()=>{}}), sleep:async()=>{}, console, encodeURI
  });
  ctx.window = ctx;
  vm.runInContext(activity, ctx);
  vm.runInContext(source, ctx);
  await ctx.loadPane('top', 'Top.jsp?name=<img onerror=alert(1)>');
  assert.equal(feedback.innerHTML, '');
  assert.deepEqual(events.map(e=>e.type), ['details']);
  assert.equal(events[0].children[1].textContent, 'Loading top : Top.jsp?name=<img onerror=alert(1)>');
  assert.equal(events[0].className, 'activity-entry activity-message');
}

async function testEncodedPlotURLIsNotEncodedTwice() {
  const pane = {innerHTML:''};
  const calls = [];
  const ctx = vm.createContext({
    document:{getElementById:id=>({plot:pane})[id]||null},
    $:()=>({}), sleep:async()=>{}, console, encodeURI,
    showTab:()=>{}, w2ui:{layoutMain:{panels:[{tabs:{click:()=>{}}}]}}
  });
  vm.runInContext(source, ctx);
  ctx.postUrlToIframe = url=>calls.push(url);
  await ctx.loadPane('plot', 'd3/scatterplot.jsp?tdata=%5B%7B%22x%22%3A0%7D%5D', true, 400, true);
  assert.equal(calls[0], 'd3/scatterplot.jsp?tdata=%5B%7B%22x%22%3A0%7D%5D');
}

testPaneLoadLogsTextNotMarkup().then(testEncodedPlotURLIsNotEncodedTwice)
  .then(()=>console.log('LoadPane feedback and encoded URL tests passed'),
  error=>{console.error(error);process.exitCode=1});

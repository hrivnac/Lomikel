'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../../webservice');
const read = file => fs.readFileSync(path.join(root, file), 'utf8');

for (const [page, controller] of [['GraphView.jsp','GraphView.js'], ['GraphViewNew.jsp','GraphViewNew.js']]) {
  const html = read(page);
  assert.match(html, /<label[^>]*for="edgeCurvature"[^>]*>Edge curvature<\/label>/);
  assert.match(html, /<input[^>]*type="range"[^>]*id="edgeCurvature"[^>]*min="0"[^>]*max="100"[^>]*value="20"[^>]*oninput="switchEdgeCurvature\(\)"/);
  assert.match(html, /<output[^>]*id="edgeCurvatureValue"[^>]*aria-live="polite"[^>]*>20%<\/output>/);
  const controls = Object.fromEntries(['vis','filter','add2graph','clusterize','zoom','stabilize',
    'physics','glayout','glayout_direction','glayout_method','edgeCurvature','edgeCurvatureValue']
    .map(id => [id, {value:'', checked:false}]));
  controls.edgeCurvature.value = '20';
  const networks=[];
  class Network {
    constructor(container, data, options) { this.options={edges:{smooth:{...options.edges.smooth}}}; this.calls=[]; networks.push(this); }
    setOptions(options) {
      this.calls.push(options.edges ? {edges:{smooth:{...options.edges.smooth}}} : options);
      if (options.layout?.hierarchical) this.options.edges.smooth.enabled=false; // vis 8.3.2 overrides it
      if (options.edges?.smooth) this.options.edges.smooth={...options.edges.smooth};
    }
    once() {} on() {} destroy() {} getScale() { return 1; }
    getViewPosition() { return {x:0,y:0}; } moveTo() {}
  }
  const context=vm.createContext({console, vis:{Network}, document:{getElementById:id=>controls[id]||null},
    options4Graph:undefined});
  context.window=context;
  vm.runInContext(read('OptionsDefault.js'),context);
  vm.runInContext(read(controller),context,{filename:controller});
  const smooth = () => ({...context.options4Graph.edges.smooth});
  assert.deepEqual(smooth(), {enabled:true,type:'curvedCW',roundness:0.2}, controller+' default');
  context.show(null);
  assert.deepEqual(networks.at(-1).options.edges.smooth, smooth());
  for (const [percent, expected] of [['0',{enabled:false,type:'curvedCW',roundness:0}],
    ['50',{enabled:true,type:'curvedCW',roundness:0.5}],
    ['100',{enabled:true,type:'curvedCW',roundness:1}]]) {
    controls.edgeCurvature.value=percent;
    const before=networks.length;
    context.switchEdgeCurvature(); // actual inline input handler, without graph query/redraw
    assert.equal(controls.edgeCurvatureValue.textContent,percent+'%');
    assert.deepEqual(smooth(),expected,controller+' '+percent);
    assert.deepEqual(networks.at(-1).calls.at(-1).edges.smooth,expected);
    assert.equal(networks.length,before);
  }
  controls.edgeCurvature.value='35'; context.switchEdgeCurvature();
  controls.glayout.checked=true; context.switchLayout();
  assert.deepEqual(networks.at(-1).options.edges.smooth,smooth(),controller+' hierarchical actual smooth');
  controls.glayout.checked=false; context.switchLayout();
  assert.deepEqual(smooth(),{enabled:true,type:'curvedCW',roundness:0.35});
  context.show(null);
  assert.deepEqual(networks.at(-1).options.edges.smooth,smooth(),controller+' rerender');
  // Reset replaces the pane controls without replacing the shared options object.
  controls.edgeCurvature.value='20'; controls.edgeCurvatureValue.textContent='20%';
  vm.runInContext(read(controller),context,{filename:controller+' reloaded'});
  assert.equal(controls.edgeCurvature.value,'35',controller+' reset slider matches retained graph');
  assert.equal(controls.edgeCurvatureValue.textContent,'35%');
  delete controls.edgeCurvature; delete controls.edgeCurvatureValue;
  assert.doesNotThrow(()=>context.switchEdgeCurvature(),controller+' old fixture');
}
console.log('Graph View edge curvature controls passed');

'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const calls = [];
const sky = {innerHTML: '<iframe src="old-map"></iframe>', replaceChildren() { this.innerHTML = ''; calls.push('clear skyview'); }};
const window = {
  loadPane: (pane, url) => calls.push([pane, url]),
  disposeGraphView: () => calls.push('dispose'),
  showTab: tab => calls.push(['tab', tab])
};
const context = {window, document: {getElementById: id => id === 'skyview' ? sky : null},
  loadPane: (...args) => window.loadPane(...args), showTab: (...args) => window.showTab(...args),
  $: () => {}, URL, URLSearchParams};
vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../webservice/workspace.js'), 'utf8'), context);
window.reset();
assert.equal(sky.innerHTML, '', 'reset must remove the stale map iframe');
assert.deepEqual(calls, ['dispose', ['feedback', 'Feedback.jsp'], ['top', 'webservice/Top.jsp'],
  ['graph', 'webservice/GraphView.jsp'], ['image', 'Image.jsp'], ['plot', 'Plot.jsp'],
  ['result', 'Result.jsp'], 'clear skyview', ['tab', 'graph']]);
console.log('SkyView reset clears stale pane and preserves legacy actions');

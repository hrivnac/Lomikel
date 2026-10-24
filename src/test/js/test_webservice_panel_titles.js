'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const source = fs.readFileSync(path.resolve(__dirname, '../../webservice/workspace.js'), 'utf8');
for (const [label, help] of [
  ['Connection & actions', 'Top'],
  ['Graph', 'GraphView'],
  ['Analysis', 'Result'],
  ['Activity & commands', 'Feedback']
]) {
  const title = source.match(new RegExp("title: '([^']*data-ws-help=\"" + help + "\"[^']*)'"))?.[1];
  assert.ok(title, `missing ${label} panel title or help control`);
  assert.ok(title.startsWith(label + ' <button'), `${label} panel title has a numeric prefix or separator`);
  assert.match(title, /aria-label="[^"]+ help"/, 'keep the accessible help button');
}
for (const id of ['layoutLeft', 'layoutMain', 'resultTab', 'tableTab', 'imageTab', 'plotTab', 'skyviewTab']) {
  assert.match(source, new RegExp(id), `keep ${id} layout/tab identifier`);
}
console.log('Preview panel titles omit numeric prefixes and retain help/actions');

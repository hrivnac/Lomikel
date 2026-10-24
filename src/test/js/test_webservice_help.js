'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../../webservice');
const shell = fs.readFileSync(path.join(root, 'workspace.js'), 'utf8');
const index = fs.readFileSync(path.join(root, 'index.jsp'), 'utf8');
const names = ['Top', 'GraphView', 'Result', 'Table', 'Image', 'Plot', 'SkyView', 'Feedback'];
for (const name of names) {
  const html = fs.readFileSync(path.join(root, `Help-${name}.html`), 'utf8');
  assert.match(html, /rel="title"/);
  assert.match(html, /id="ws-help-popup-title"/);
  assert.match(html, /rel="body"/);
  assert.match(html, /ws-help-document/);
  assert.match(html, /rel="buttons"/);
  assert.doesNotMatch(html, /\b(?:heighest|customisea|alows|hiararchy|quckly|substrich|thee|firm)\b/i);
}
const skyHelp = fs.readFileSync(path.join(root, 'Help-SkyView.html'), 'utf8');
assert.match(skyHelp, /<strong>Sky<\/strong>.*<code>ra<\/code>.*<code>dec<\/code>/);
assert.match(skyHelp, /<strong>SkyView<\/strong>.*graph.*object/i);
assert.match(skyHelp, /Map settings.*Plotted object coordinates/);
assert.doesNotMatch(skyHelp, /SkyView action/);
assert.match(shell, /data-ws-help="Top"/);
assert.match(shell, /data-ws-help="GraphView"/);
assert.match(shell, /data-ws-help="Result"/);
assert.match(shell, /data-ws-help="Feedback"/);
for (const name of ['Result', 'Table', 'Image', 'Plot', 'SkyView']) {
  assert.match(shell, new RegExp(`(?:${name.toLowerCase()}Tab.*?${name}|${name}.*?${name.toLowerCase()}Tab)`));
}
assert.match(index, /webservice\/help\.js/);
assert.match(index, /webservice\/help\.css/);
const button = {
  attrs: {'data-ws-help': 'Result'},
  getAttribute(key) { return this.attrs[key]; },
  setAttribute(key, value) { this.attrs[key] = value; }
};
let listener, popup;
let focused = false;
const popupElement = {
  attrs: {}, querySelector: selector => selector === '#ws-help-popup-title' ? {} : null,
  setAttribute(key, value) { this.attrs[key] = value; }, focus() { focused = true; }
};
const layout = {contains: target => target === button,
  addEventListener: (name, callback) => { if (name === 'click') listener = callback; }};
const document = {getElementById: id => id === 'layout' ? layout : id === 'w2ui-popup' ? popupElement : null,
  querySelector: () => button};
let originalCalls = [];
const window = {showTab(name) { originalCalls.push(name); }};
vm.runInNewContext(fs.readFileSync(path.join(root, 'help.js'), 'utf8'), {
  window, document, w2popup: {load: options => { popup = options; }}
});
window.wsInstallHelp();
for (const [tab, name] of Object.entries({result: 'Result', table: 'Table', image: 'Image',
                                             plot: 'Plot', skyview: 'SkyView'})) {
  window.showTab(tab);
  assert.equal(button.attrs['data-ws-help'], name);
  let stopped = false;
  listener({target: {closest: () => button}, preventDefault() {}, stopPropagation() { stopped = true; }});
  assert.equal(popup.url, `webservice/Help-${name}.html`);
  assert.equal(stopped, true);
  focused = false;
  const event = {phase: 'before'};
  popup.onOpen(event);
  assert.equal(focused, false);
  assert.equal(typeof event.onComplete, 'function');
  event.onComplete();
  assert.equal(focused, true);
  assert.equal(popupElement.attrs.role, 'dialog');
  assert.equal(popupElement.attrs['aria-modal'], 'true');
  assert.equal(popupElement.attrs['aria-labelledby'], 'ws-help-popup-title');
}
assert.deepEqual(originalCalls, ['result', 'table', 'image', 'plot', 'skyview']);
button.attrs['data-ws-help'] = '../bad';
popup = null;
listener({target: {closest: () => button}, preventDefault() {}, stopPropagation() {}});
assert.equal(popup, null, 'unknown names must not create arbitrary help URLs');
console.log('webservice panel help: all eight targets, routing and assets present');

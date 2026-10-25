'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const root=path.resolve(__dirname,'../../webservice');
const page=fs.readFileSync(path.join(root,'index.jsp'),'utf8');
const css=fs.readFileSync(path.join(root,'workspace.css'),'utf8');
assert.match(page,/<base href="<%= request\.getContextPath\(\) %>\/">/);
assert.match(page,/<nav class="workspace-app-links" aria-label="Standalone applications">/);
for (const [route,label,icon] of [
  ['AlertsView','Alerts View','alerts'],
  ['LightCurveView','LSST Light Curve Visualizer','light-curve'],
  ['ClassificationView','Classification Neighborhood','classification']
]) {
  assert.ok(fs.existsSync(path.resolve(root,'../ws/FinkBrowser',route,'index.html')),
    'destination exists in the packaged legacy tree');
  const expected='<a href="'+route+'/index.html" target="_blank" rel="noopener noreferrer" title="'+label+'" aria-label="'+label+' (opens in a new tab)">';
  assert.ok(page.includes(expected),route+' uses a relative, safely opened new tab with a tooltip');
  const svg=fs.readFileSync(path.join(root,'icons',icon+'.svg'),'utf8');
  assert.match(page,new RegExp('src="webservice/icons/'+icon+'\\.svg"'));
  assert.match(svg,/<svg[^>]*viewBox="0 0 32 32"/);
  assert.match(svg,new RegExp('<title>'+label+'</title>'));
  assert.doesNotMatch(svg,/<script|onload=|href="https?:/i);
}
assert.match(css,/\.workspace-app-links\s*\{/);
assert.match(css,/\.workspace-app-links a:focus-visible/);
console.log('header launchers link to all three WAR applications with descriptive accessible icons');

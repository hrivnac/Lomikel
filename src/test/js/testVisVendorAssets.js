// Verify that FinkBrowser loads self-hosted, usable vis bundles from the WAR.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../../..');
const html = fs.readFileSync(path.join(root, 'src/webservice/index.jsp'), 'utf8');
for (const [library, version, script, style, exported] of [
  ['vis-network', '10.1.5', 'vis-network.min.js', 'vis-network.min.css', 'Network'],
  ['vis-timeline', '8.5.6', 'vis-timeline-graph2d.min.js', 'vis-timeline-graph2d.min.css', 'Timeline'],
]) {
  const dir = `${library}-${version}`;
  const js = `${dir}/standalone/umd/${script}`;
  const css = `${dir}/styles/${style}`;
  assert.ok(html.includes(`src="${js}"`), `index.jsp must load local ${js}`);
  assert.ok(html.includes(`href="${css}"`), `index.jsp must load local ${css}`);
  for (const asset of [js, css]) {
    const file = path.join(root, 'extlib/webservice', asset);
    assert.ok(fs.statSync(file).size > 1000, `missing or empty ${asset}`);
  }
  const bundle = fs.readFileSync(path.join(root, 'extlib/webservice', js), 'utf8');
  // UMD entry point must expose the browser API used by GraphView and plotting pages.
  assert.ok(bundle.includes(exported), `${library} bundle lacks ${exported}`);
  assert.ok(fs.existsSync(path.join(root, 'extlib/webservice', dir, 'package.json')));
  assert.equal(JSON.parse(fs.readFileSync(path.join(root, 'extlib/webservice', dir, 'package.json'))).version, version);
}
assert.doesNotMatch(html, /vis-network-8\.3\.2|vis-timeline-7\.3\.9/);
console.log('vis vendor asset references, versions and bundles OK');

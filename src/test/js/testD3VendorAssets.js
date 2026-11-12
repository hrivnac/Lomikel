// Verify that FinkBrowser pages load one self-hosted, usable d3 release.
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../../..');
const version = '7.9.2';
const dir = `d3-v${version}`;
const ext = path.join(root, 'extlib/webservice');
const web = path.join(root, 'src/webservice');
const read = (file) => fs.readFileSync(file, 'utf8');

const bundleFile = path.join(ext, dir, 'd3.min.js');
assert.ok(fs.statSync(bundleFile).size > 100000, `missing or empty ${dir}/d3.min.js`);
for (const file of ['LICENSE', 'package.json']) {
  assert.ok(fs.existsSync(path.join(ext, dir, file)), `missing ${dir}/${file}`);
}
assert.equal(JSON.parse(read(path.join(ext, dir, 'package.json'))).version, version);
assert.equal(fs.existsSync(path.join(ext, 'd3-v6.0.0')), false, 'old d3-v6.0.0 must not be shipped');

// Each d3 page must load the new local bundle (relative to its own location).
const pages = {
  'Venn.jsp': `src="${dir}/d3.min.js"`,
  'd3/venn.jsp': `src="<%=request.getContextPath() %>/${dir}/d3.min.js"`,
  'd3/lightcurve.jsp': `src="../${dir}/d3.min.js"`,
  'd3/correlogram.jsp': `src="../${dir}/d3.min.js"`,
  'd3/scatterplot.jsp': `src="../${dir}/d3.min.js"`,
};
for (const [page, ref] of Object.entries(pages)) {
  const text = read(path.join(web, page));
  assert.ok(text.includes(ref), `${page} must load ${ref}`);
  assert.doesNotMatch(text, /d3-v6/, `${page} still references d3 v6`);
}

// The standalone ClassificationView keeps a regular-file copy of the same bundle.
const classification = path.join(web, 'ClassificationView/d3.min.js');
assert.ok(fs.readFileSync(classification).equals(fs.readFileSync(bundleFile)),
  'ClassificationView/d3.min.js must equal the vendored d3 bundle');
assert.match(read(path.join(web, 'ClassificationView/index.html')), new RegExp(`src="d3\\.min\\.js\\?v=${version.replace(/\./g, '\\.')}"`));

// d3-celestial 0.7.x is written against the d3 v3 API: keep its own d3 3.5.17.
assert.match(read(path.join(ext, 'd3-celestial-0.7.32/lib/d3.min.js')), /version:"3\.5\.17"/);
assert.match(read(path.join(web, 'd3/skyview.jsp')), /d3-celestial-0\.7\.32\/lib\/d3\.min\.js/);

// The bundle must expose every d3 API used by Lomikel pages and venn.js.
const ctx = { console, setTimeout, clearTimeout };
ctx.window = ctx;
vm.runInNewContext(read(bundleFile), ctx);
assert.equal(ctx.d3.version, version);
for (const name of ['select', 'selectAll', 'pointer', 'scaleLinear', 'scaleOrdinal', 'scaleSqrt',
  'scaleTime', 'scaleUtc', 'axisBottom', 'axisLeft', 'extent', 'max', 'min', 'line',
  'timeFormat', 'utcFormat', 'symbol', 'zoom', 'forceSimulation', 'forceLink',
  'forceManyBody', 'forceCenter', 'transition']) {
  assert.equal(typeof ctx.d3[name], 'function', `d3.${name} missing`);
}
for (const scheme of ['schemeTableau10', 'schemeCategory10']) {
  assert.ok(Array.isArray(ctx.d3[scheme]) && ctx.d3[scheme].length === 10, `d3.${scheme} missing`);
}
assert.ok(ctx.d3.symbolStar && ctx.d3.zoomIdentity, 'd3.symbolStar/zoomIdentity missing');
assert.equal(typeof ctx.d3.selection.prototype.transition, 'function', 'venn.js needs selection.transition');
vm.runInNewContext(read(path.join(ext, 'venn-v.0.2.14/venn.js')), ctx);
assert.equal(typeof ctx.venn.VennDiagram, 'function', 'venn.js must initialize on the new d3');
console.log(`d3 ${version} vendor assets, page references and APIs OK`);

'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../../webservice');
const original = path.resolve(root, 'images/Star.png');
const stylesheet = fs.readFileSync(path.join(root, 'SurveyStylesheet.js'), 'utf8');
const style = {nodes:{object:{graphics:{shape:'image',image:'Star.png'},actions:[]}}};
const context = {stylesheet:style};
vm.runInNewContext(stylesheet, context);
const selection = style.nodes.object.graphics.image;
assert.equal(typeof selection.js,'string', 'legacy stylesheetValue must choose the image per vertex');
for (const [objectId, name] of [
  ['ZTF21aaxtctv','Star-ZTF.png'], ['313761043604045880','Star-LSST.png'],
  ['unknown','Star.png'], ['', 'Star.png'], [null,'Star.png']
]) {
  context.eMap = new Map([['objectId',objectId]]);
  const value = vm.runInNewContext(selection.js,context);
  assert.equal(value, name, 'GraphView.js supplies the images/ prefix');
  assert.equal('images/' + value, 'images/' + name);
}
assert.match(fs.readFileSync(path.join(root, 'GraphView.jsp'), 'utf8'),
  /Stylesheet\.js\?v=survey-colors-/);
function chunks(file) {
  const data=fs.readFileSync(file);
  assert.equal(data.subarray(0,8).toString('hex'),'89504e470d0a1a0a');
  let offset=8; const found={};
  while(offset<data.length){
    const size=data.readUInt32BE(offset),type=data.toString('ascii',offset+4,offset+8);
    (found[type] ||= []).push(data.subarray(offset+8,offset+8+size));
    offset+=12+size;
    assert.ok(offset<=data.length);
  }
  return found;
}
const base=chunks(original);
const skyStyle=fs.readFileSync(path.join(root,'d3/skyview.css'),'utf8');
for (const name of ['ZTF','LSST']) {
  const rgb=skyStyle.match(new RegExp('\\.sky-legend-'+name.toLowerCase()+'::before \\{background:#([0-9a-f]{6})\\}'))?.[1];
  assert.ok(rgb, 'SkyView must define the survey color');
  const icon=chunks(path.join(root,'images','Star-'+name+'.png'));
  assert.equal(icon.IHDR[0].toString('hex'),base.IHDR[0].toString('hex'));
  assert.equal(Buffer.concat(icon.IDAT).toString('hex'),Buffer.concat(base.IDAT).toString('hex'),
    'palette recoloring must preserve all source shape pixels');
  assert.equal(icon.tRNS[0].toString('hex'),base.tRNS[0].toString('hex'),
    'the transparency and antialiased silhouette must stay unchanged');
  assert.ok(icon.PLTE[0].toString('hex').includes(rgb),name+' icon contains its SkyView color');
}
// OCol vertices: survey-colored copies of the original OCol icon, chosen by the OCol "survey" property.
const ocolStyle = {nodes:{object:{graphics:{shape:'image',image:'Star.png'},actions:[]},
  OCol:{graphics:{shape:'image',image:'StarsSources.png'},actions:[]}}};
const ocolContext = {stylesheet:ocolStyle};
vm.runInNewContext(stylesheet, ocolContext);
const ocolSelection = ocolStyle.nodes.OCol.graphics.image;
assert.equal(ocolSelection.js, "finkSurveyOColImage(eMap.get('survey'))");
for (const [survey, name] of [['ZTF','StarsSources-ZTF.png'], ['LSST','StarsSources-LSST.png'], [' LSST ','StarsSources-LSST.png'],
                              ['ANY','StarsSources.png'], ['', 'StarsSources.png'], [null,'StarsSources.png'], ['../x','StarsSources.png']]) {
  ocolContext.eMap = new Map([['survey',survey]]);
  assert.equal(vm.runInNewContext(ocolSelection.js, ocolContext), name, String(survey));
}
// GraphView.js whitelists the expression and keeps the plain-PNG check.
const graphView = fs.readFileSync(path.join(root, 'GraphView.js'), 'utf8');
assert.match(graphView, /case "finkSurveyOColImage\(eMap\.get\('survey'\)\)":\n\s+return typeof finkSurveyOColImage === 'function' \? finkSurveyOColImage\(eMap\.get\('survey'\)\) : '';/);
// The recolored icons keep the original shape: same size, same alpha everywhere, and only the yellow star
// pixels change (black outlines, cyan and red dots identical); they contain the survey color.
const zlib = require('node:zlib');
function rgba(file) {
  const c = chunks(file), h = c.IHDR[0];
  const width = h.readUInt32BE(0), height = h.readUInt32BE(4), depth = h[8], type = h[9];
  assert.ok(depth === 8 && (type === 6 || type === 3), file + ': 8-bit RGBA or palette PNG');
  const bpp = type === 6 ? 4 : 1, raw = zlib.inflateSync(Buffer.concat(c.IDAT)), stride = width * bpp;
  const out = Buffer.alloc(width * height * 4); let prev = Buffer.alloc(stride);
  for (let y = 0; y < height; y++) {
    const f = raw[y * (stride + 1)], line = Buffer.from(raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1)));
    for (let x = 0; x < stride; x++) {
      const a = x >= bpp ? line[x - bpp] : 0, b = prev[x], cc = x >= bpp ? prev[x - bpp] : 0;
      const p = a + b - cc, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - cc);
      line[x] = (line[x] + [0, a, b, (a + b) >> 1, pa <= pb && pa <= pc ? a : pb <= pc ? b : cc][f]) & 255;
    }
    for (let x = 0; x < width; x++) {
      if (type === 6) line.copy(out, (y * width + x) * 4, x * 4, x * 4 + 4);
      else { const i = line[x]; c.PLTE[0].copy(out, (y * width + x) * 4, i * 3, i * 3 + 3);
             out[(y * width + x) * 4 + 3] = c.tRNS && i < c.tRNS[0].length ? c.tRNS[0][i] : 255; }
    }
    prev = line;
  }
  return {width, height, out};
}
function isYellow(r, g, b) {
  const max = Math.max(r, g, b), min = Math.min(r, g, b);
  if (max < 24 || max - min < 0.25 * max) return false; // near-black outline pixels count as outline
  const h = max === r ? 60 * (((g - b) / (max - min)) % 6) : max === g ? 60 * ((b - r) / (max - min) + 2) : 60 * ((r - g) / (max - min) + 4);
  return h >= 30 && h <= 75;
}
const ocolBase = rgba(path.join(root, 'images/StarsSources.png'));
for (const name of ['ZTF','LSST']) {
  const rgb = skyStyle.match(new RegExp('\\.sky-legend-'+name.toLowerCase()+'::before \\{background:#([0-9a-f]{6})\\}'))[1];
  const icon = rgba(path.join(root, 'images', 'StarsSources-' + name + '.png'));
  assert.equal(icon.width, ocolBase.width); assert.equal(icon.height, ocolBase.height);
  let changed = 0, survey = 0, yellowLeft = 0;
  for (let i = 0; i < icon.out.length; i += 4) {
    const o = ocolBase.out, n = icon.out;
    assert.equal(n[i + 3], o[i + 3], name + ': alpha (shape) unchanged');
    if (o[i + 3] === 0) continue;
    const same = n[i] === o[i] && n[i + 1] === o[i + 1] && n[i + 2] === o[i + 2];
    if (!isYellow(o[i], o[i + 1], o[i + 2])) assert.ok(same, name + ': only yellow pixels may change');
    else { changed += !same; yellowLeft += isYellow(n[i], n[i + 1], n[i + 2]) && n[i + 3] > 128 ? 1 : 0; }
    const target = [0, 2, 4].map(k => parseInt(rgb.slice(k, k + 2), 16));
    if (target.every((v, k) => Math.abs(n[i + k] - v) <= 12)) survey++;
  }
  assert.ok(changed > 30000, name + ': the star fills were recolored');
  assert.equal(yellowLeft, 0, name + ': no yellow left on visible pixels');
  assert.ok(survey > 1000, name + ' OCol icon uses its SkyView color (shaded faces)');
}
console.log('survey-specific graph stars and OCol icons preserve the original silhouettes and route by object ID / OCol survey');

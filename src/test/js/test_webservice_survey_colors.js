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
console.log('survey-specific graph stars preserve the original silhouette and route by object ID');

// Run with: node --test src/ws/FinkBrowser/ClassificationView/test-app.cjs
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const dir = __dirname;
const html = fs.readFileSync(path.join(dir, 'index.html'), 'utf8');

function harness(neighborhoodFetch) {
  const requests = [];
  const renders = {details: [], drawings: []};
  const elements = new Map();
  const el = id => elements.get(id);
  function select(id) {
    const node = {options: [], value: '', onchange: null,
      replaceChildren(...children) {this.options = children; this.value = children[0]?.value || '';},
      add(child) {this.options.push(child); if (this.options.length === 1) this.value = child.value;}};
    elements.set(id, node);
  }
  for (const id of ['survey', 'classifier', 'reclassifier', 'metric']) select(id);
  el('survey').value = 'ZTF'; // Browser selects the first HTML option by default.
  for (const id of ['objectId', 'nmax', 'nmaxValue', 'showBtn', 'resetBtn']) elements.set(id, {value:'', textContent:'', onclick:null, oninput:null, dispatchEvent(e){this.oninput?.(e);}});
  for (const id of ['controls-header','controls','list-header','list']) elements.set(id,{addEventListener:()=>{},style:{}});
  const document = {getElementById:id => el(id), createElement:() => ({value:'', textContent:''})};
  const context = vm.createContext({document, URLSearchParams, Event: class {constructor(type){this.type=type;}},
    showSpinner:()=>{}, showObjectNeighborhood:data=>renders.drawings.push(data),
    updateDetailsPanel:(data,survey)=>renders.details.push({data,survey}), resetZoom:()=>{},
    window: {alert:()=>{},addEventListener:()=>{}}, console,
    fetch:async url => {requests.push(url); if (url.includes('Classifiers.jsp')) return {ok:true, json:async()=>[
       {classifier:'FINK', flavor:'', survey:'ZTF'}, {classifier:'XMATCH', flavor:'', survey:'ZTF'},
       {classifier:'FEATURES', flavor:'2025/13-50', survey:'ZTF'},
       {classifier:'FEATURES', flavor:'2024/13-60', survey:'ZTF'},
       {classifier:'TAG', flavor:'', survey:'ANY'}]};
      return neighborhoodFetch ? neighborhoodFetch(url) :
        {ok:true,json:async()=>({objectId:el('objectId').value,objects:{},objectClassification:{}})};
    }});
  for (const file of ['menu.js','data.js','app.js']) vm.runInContext(fs.readFileSync(path.join(dir,file),'utf8'),context,{filename:file});
  return {el,requests,context,renders};
}
const settle = () => new Promise(resolve => setTimeout(resolve, 10));

test('a delayed ZTF neighborhood cannot replace the latest LSST object render', async () => {
  const pending = new Map();
  const {el,requests,context,renders} = harness(url => new Promise(resolve => pending.set(
    new URL(url,'http://localhost').searchParams.get('objectId'), resolve)));
  const ztfId = 'ZTF17aackceb';
  const lsstId = '170028526873870371';
  assert.equal(pending.has(ztfId), true);
  el('objectId').value = lsstId;
  el('objectId').oninput();
  const latest = vm.runInContext('loadNeighborhood()', context);
  assert.equal(new URL(requests.at(-1),'http://localhost').searchParams.get('survey'), 'LSST');
  const lsstData = {objectId:lsstId,objects:{},objectClassification:{LSST:1}};
  pending.get(lsstId)({ok:true,json:async()=>lsstData});
  await latest;
  assert.deepEqual(renders.details, [{data:lsstData,survey:'LSST'}]);
  assert.deepEqual(renders.drawings, [lsstData]);
  const ztfData = {objectId:ztfId,objects:{},objectClassification:{ZTF:1}};
  pending.get(ztfId)({ok:true,json:async()=>ztfData});
  await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(renders.details, [{data:lsstData,survey:'LSST'}]);
  assert.deepEqual(renders.drawings, [lsstData]);
});

test('defaults are JS-only and initial request is ZTF with requested values', async () => {
  for (const id of ['objectId','nmax','survey','classifier','metric']) {
    const tag = html.match(new RegExp(`<(?:(?:input)|(?:select))[^>]*id="${id}"[^>]*>`))?.[0] || '';
    assert.ok(!/\bvalue=|\bselected\b/.test(tag), `${id} has hard-coded default`);
  }
  const {el,requests} = harness(); await settle();
  assert.equal(el('objectId').value,'ZTF17aackceb');
  assert.equal(el('survey').value,'ZTF');
  assert.deepEqual(Array.from(el('classifier').options,o=>o.value),['FINK','XMATCH','FEATURES=2025/13-50','FEATURES=2024/13-60','TAG']);
  assert.equal(el('metric').value,'JensenShannon');
  assert.equal(el('nmaxValue').textContent,'20');
  const query = new URL(requests.find(x=>x.includes('Neighborhood.jsp')),'http://localhost').searchParams;
  assert.equal(query.get('survey'),'ZTF'); assert.equal(query.get('objectId'),'ZTF17aackceb');
  assert.equal(query.get('nmax'),'20'); assert.equal(query.get('metric'),'JensenShannon');
});

test('numeric ID selects LSST and switches classifiers, then ZTF restores choices', async () => {
  const {el,requests,context} = harness(); await settle();
  el('objectId').value='170028526873870371'; el('objectId').oninput(); await settle();
  assert.equal(el('survey').value,'LSST');
  assert.deepEqual(Array.from(el('classifier').options,o=>o.value),['FINK','TAG']);
  assert.deepEqual(Array.from(el('reclassifier').options,o=>o.value),['none','FINK','TAG']);
  await vm.runInContext('loadNeighborhood()',context);
  assert.equal(new URL(requests.at(-1),'http://localhost').searchParams.get('survey'),'LSST');
  el('survey').value='ZTF'; el('survey').onchange({target:el('survey')}); await settle();
  assert.deepEqual(Array.from(el('classifier').options,o=>o.value),['FINK','XMATCH','FEATURES=2025/13-50','FEATURES=2024/13-60','TAG']);
});

test('navigation by object ID switches survey and submits that ID', async () => {
  const {el,requests,context} = harness(); await settle();
  await vm.runInContext("loadNeighborhood('170028526873870371')",context);
  assert.equal(el('survey').value,'LSST'); assert.equal(el('objectId').value,'170028526873870371');
  const query=new URL(requests.at(-1),'http://localhost').searchParams;
  assert.equal(query.get('survey'),'LSST'); assert.equal(query.get('objectId'),'170028526873870371');
});

test('neighbor links receive their survey and overlap cache separates surveys', () => {
  const drawing = fs.readFileSync(path.join(dir,'drawing.js'),'utf8');
  const overlaps = fs.readFileSync(path.join(dir,'overlaps.js'),'utf8');
  assert.match(drawing, /false, survey\)/);
  assert.match(overlaps, /overlapCache\[cacheKey\]/);
});

test('an invalid ID cannot submit an ambiguous survey', async () => {
  const {el,requests,context} = harness(); await settle();
  const before=requests.filter(x=>x.includes('Neighborhood.jsp')).length;
  el('objectId').value='invalid';
  await vm.runInContext('loadNeighborhood()',context);
  assert.equal(requests.filter(x=>x.includes('Neighborhood.jsp')).length,before);
});

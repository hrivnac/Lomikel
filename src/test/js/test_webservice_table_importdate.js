'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const jsp = fs.readFileSync(path.resolve(__dirname, '../../webservice/Nodes.jsp'), 'utf8');
const script = jsp.match(/<script type="text\/javascript">([\s\S]*?)<\/script>/)[1];
const sample = '2026-11-04 16:10:54.223';
const ctx = {
  nodes: [{id: 1, title: 'object:ZTF17aabgsqa'}],
  gr: 'g',
  findObjectByKey: (items, key, id) => items.find(item => item[key] === id),
  callGremlinValues: () => [`[lbl:[object], objectId:[ZTF17aabgsqa], importDate:[${sample}]]`],
  plotTableEscape: value => value
};
vm.createContext(ctx);
vm.runInContext(script.replace('<%=id%>', '1'), ctx);
assert.equal(ctx.tdata.length, 1);
assert.equal(ctx.tdata[0].importDate, sample, 'the Table action must keep the full timestamp, including minutes, seconds and milliseconds');
console.log('Graph object Table preserves the full importDate timestamp');

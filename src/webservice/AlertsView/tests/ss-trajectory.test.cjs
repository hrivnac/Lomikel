"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

const directory = path.resolve(__dirname, "..");
function loadTrajectory({fetch, nAlerts = 3} = {}) {
  const context = {
    AbortController,
    nAlerts,
    fetch: fetch || (async () => { throw new Error("Unexpected request"); }),
    document: {getElementById: () => context.status},
    status: {textContent: "", hidden: true},
    console: {error() {}},
  };
  vm.createContext(context);
  vm.runInContext(fs.readFileSync(path.join(directory, "trajectory.js"), "utf8"), context, {filename: "trajectory.js"});
  return context;
}

test("sorts observation-level SS sources by MJD without joining independent ES arrays", () => {
  const context = loadTrajectory();
  context.payload = {
    objectId: "21163615655113561",
    sources: [
      {sourceId: "source-2", mjd: 61234.5, ra: 359, dec: 3},
      {sourceId: "source-1", mjd: 61233.5, ra: 1, dec: 4},
      {sourceId: "source-bad", mjd: 61230, ra: "", dec: 5},
    ],
  };
  const result = vm.runInContext("normalizeSsTrajectory(payload)", context);
  assert.deepEqual(JSON.parse(JSON.stringify(result)), {
    objectId: "21163615655113561", latestMjd: 61234.5,
    points: [
      {sourceId: "source-1", mjd: 61233.5, ra: 1, dec: 4},
      {sourceId: "source-2", mjd: 61234.5, ra: 359, dec: 3},
    ],
  });
});

test("rejects a dated trajectory whose paired source rows do not cover Elasticsearch history", () => {
  const context = loadTrajectory();
  context.payload = {objectId: "123", esCoverageComplete: false,
    sources: [{sourceId: "1", mjd: 10, ra: 100, dec: 0}]};
  assert.throws(() => vm.runInContext('normalizeSsTrajectory(payload)', context), /coverage/i);
});

test("SS requests start only after activation and accumulate objects one response at a time", async () => {
  const calls = [];
  let releaseSecond;
  const context = loadTrajectory({fetch: async (url) => {
    calls.push(url);
    if (url.includes("list=1")) return {ok: true, json: async () => ({ids: ["123", "456"]})};
    if (url.includes("id=123")) return {ok: true, json: async () => ({objectId: "123", sources: [{sourceId: "1", mjd: 10, ra: 180, dec: 1}]})};
    return new Promise(resolve => { releaseSecond = () => resolve({ok: true, json: async () => ({objectId: "456", sources: [{sourceId: "2", mjd: 11, ra: 181, dec: 2}]})}); });
  }});
  assert.equal(calls.length, 0);
  const pending = vm.runInContext("startSsTrajectoryLoad()", context);
  assert.equal(context.status.hidden, false);
  for (let i = 0; i < 20 && !releaseSecond; i++) await new Promise(resolve => setImmediate(resolve));
  assert.equal(typeof releaseSecond, "function");
  assert.equal(vm.runInContext("ssTrajectories.size", context), 1);
  releaseSecond();
  await pending;
  assert.equal(vm.runInContext("ssTrajectories.size", context), 2);
  assert.deepEqual(calls, ["SSTrajectory.jsp?list=1&n=3", "SSTrajectory.jsp?id=123", "SSTrajectory.jsp?id=456"]);
});

test("SS loading can be stopped and stale replies cannot repaint trajectories", async () => {
  let release;
  const context = loadTrajectory({fetch: async (url) => {
    if (url.includes("list=1")) return {ok: true, json: async () => ({ids: ["123"]})};
    return new Promise(resolve => { release = () => resolve({ok: true, json: async () => ({objectId: "123", sources: [{sourceId: "1", mjd: 10, ra: 180, dec: 0}]})}); });
  }});
  const pending = vm.runInContext("startSsTrajectoryLoad()", context);
  for (let i = 0; i < 20 && !release; i++) await new Promise(resolve => setImmediate(resolve));
  vm.runInContext("stopSsTrajectoryLoad()", context);
  release();
  await pending;
  assert.equal(vm.runInContext("ssTrajectories.size", context), 0);
  assert.equal(vm.runInContext("ssTrajectoryEnabled", context), false);
});

test("trajectory segments wrap at RA=0 and duplicate new-alert positions are not painted twice", () => {
  const marks = [];
  const segments = [];
  let from = null;
  let to = null;
  const noop = () => {};
  const context = loadTrajectory();
  Object.assign(context, {
    canvas: {width: 1200, height: 600},
    camera: {currentCenter: {ra: 180, dec: 0}, currentZoom: 1},
    ctx: {beginPath: noop, closePath: noop, fill: noop, fillText: noop,
      moveTo: (x, y) => { from = [x, y]; },
      lineTo: (x, y) => { to = [x, y]; },
      stroke: () => { if (from && to) segments.push([from, to]); },
      save: noop, restore: noop},
    classes: {},
    flashes: [],
  });
  for (const file of ["consts.js", "utils.js", "drawing.js"]) {
    vm.runInContext(fs.readFileSync(path.join(directory, file), "utf8"), context, {filename: file});
  }
  context.drawStar = (...args) => marks.push(args);
  context.trajectory = {objectId: "123", points: [{sourceId: "1", mjd: 10, ra: 359, dec: 0}, {sourceId: "2", mjd: 11, ra: 0, dec: 1}]};
  vm.runInContext('ssTrajectories.set("123", trajectory); ssTrajectoryEnabled = true', context);
  context.visibleMarkers = [{alert: {survey: "LSST", objectId: "0", sourceId: "1", jd: 10, ra: 359, dec: 0}, color: "12,34,56"}];
  vm.runInContext('drawSsTrajectories(visibleMarkers)', context);
  assert.equal(marks.length, 2); // one physical historical point, repeated across the seam
  assert.equal(marks.every(mark => mark[3] === "12,34,56"), true);
  assert.equal(context.ctx.strokeStyle, 'rgba(12,34,56,0.8)');
  // The two sources are ~4.7 px apart: the star shrinks to its floor so the track stays visible.
  assert.equal(marks.every(mark => mark[2] === 2.5), true);
  assert.equal(marks.every(mark => Math.abs(mark[0] - 1200 / 360) > 1), true);
  assert.equal(segments.length > 0, true);
  assert.equal(segments.every(([a, b]) => Math.abs(a[0] - b[0]) < 30), true);
});

test("a trajectory absent from the fresh-alert pool has one current marker and shrunken history", () => {
  const marks = [];
  const noop = () => {};
  const context = loadTrajectory();
  Object.assign(context, {
    canvas: {width: 1200, height: 600},
    camera: {currentCenter: {ra: 100, dec: 0}, currentZoom: 2},
    ctx: {save: noop, restore: noop, beginPath: noop, moveTo: noop, lineTo: noop, stroke: noop},
    classes: {}, flashes: [],
  });
  for (const file of ["consts.js", "utils.js", "drawing.js"]) {
    vm.runInContext(fs.readFileSync(path.join(directory, file), "utf8"), context, {filename: file});
  }
  context.drawStar = (...args) => marks.push(args);
  context.trajectory = {objectId: "123", points: [
    {sourceId: "old", mjd: 10, ra: 100, dec: 0},
    {sourceId: "new", mjd: 20, ra: 101, dec: 0},
  ]};
  vm.runInContext('ssTrajectories.set("123", trajectory); ssTrajectoryEnabled = true; drawSsTrajectories([])', context);
  // 1 degree at zoom 2 is 6.67 px: history gets a quarter of the gap, the latest source its floor.
  assert.equal(Math.abs(marks[0][2] - 6.6667 * 0.25) < 0.001, true);
  assert.equal(marks[1][2], 2.5);
  assert.equal(vm.runInContext("ssVisibleMarkers.length", context), 2);
});

test("repeated historical coordinates and a visible current alert never double-paint stars", () => {
  const marks = [];
  const noop = () => {};
  const context = loadTrajectory();
  Object.assign(context, {
    canvas: {width: 1200, height: 600},
    camera: {currentCenter: {ra: 100, dec: 0}, currentZoom: 2},
    ctx: {save: noop, restore: noop, beginPath: noop, moveTo: noop, lineTo: noop, stroke: noop},
    classes: {}, flashes: [],
  });
  for (const file of ["consts.js", "utils.js", "drawing.js"]) {
    vm.runInContext(fs.readFileSync(path.join(directory, file), "utf8"), context, {filename: file});
  }
  context.drawStar = (...args) => marks.push(args);
  context.trajectory = {objectId: "123", points: [
    {sourceId: "old-1", mjd: 10, ra: 100, dec: 0},
    {sourceId: "old-2", mjd: 11, ra: 100, dec: 0},
    {sourceId: "new", mjd: 20, ra: 101, dec: 0},
  ]};
  context.primary = [{alert: {survey: "LSST", objectId: "0", jd: 20, ra: 101, dec: 0}, color: "10,20,30"}];
  vm.runInContext('ssTrajectories.set("123", trajectory); ssTrajectoryEnabled = true; drawSsTrajectories(primary)', context);
  assert.equal(marks.length, 1);
  assert.equal(Math.abs(marks[0][2] - 6.6667 * 0.25) < 0.001, true, "the gap is measured to the nearest distinct position");
  assert.equal(vm.runInContext('ssVisibleMarkers.length', context), 1);
});

test("SS endpoint is read-only, bounded, and restricted to fixed SS indexes and numeric IDs", () => {
  const jsp = fs.readFileSync(path.join(directory, "SSTrajectory.jsp"), "utf8");
  assert.match(jsp, /Math\.min\(1000, Math\.max\(100, limit \* 10\)\)/);
  assert.match(jsp, /times\.length\(\) < 2/);
  assert.match(jsp, /ss_mjd\/_search/);
  assert.match(jsp, /ss_mjd\/_mget/);
  assert.match(jsp, /ss_radec\/_mget/);
  assert.match(jsp, /\/api\/v1\/resolver/);
  assert.match(jsp, /\/api\/v1\/sso/);
  assert.match(jsp, /r:midpointMjdTai/);
  assert.match(jsp, /r:diaSourceId/);
  assert.match(jsp, /esCoverageComplete/);
  assert.match(jsp, /getParameter\("list"\)/);
  assert.match(jsp, /getParameter\("id"\)/);
  assert.match(jsp, /matches\("\\\\d\{1,64\}"\)/);
  assert.match(jsp, /Math\.min\(100, Math\.max\(1, limit\)\)/);
  assert.doesNotMatch(jsp, /getParameter\("(?:server|url|index)"\)/);
});

test("static mode never exposes an enabled trajectory control", () => {
  const html = fs.readFileSync(path.join(directory, "index.html"), "utf8");
  assert.match(html, /id="btnSsTrajectory"[^>]*disabled[^>]*>SS trajectory<\/button>/);
  assert.match(html, /src="trajectory.js(\?v=[\w-]+)?"/);
});

function loadDrawing(zoom = 1, center = {ra: 100, dec: 0}) {
  const noop = () => {};
  const marks = [];
  const context = loadTrajectory();
  Object.assign(context, {
    canvas: {width: 1200, height: 600},
    camera: {currentCenter: center, currentZoom: zoom},
    ctx: {save: noop, restore: noop, beginPath: noop, moveTo: noop, lineTo: noop, stroke: noop},
    classes: {}, flashes: [],
  });
  for (const file of ["consts.js", "utils.js", "drawing.js"]) {
    vm.runInContext(fs.readFileSync(path.join(directory, file), "utf8"), context, {filename: file});
  }
  context.drawStar = (...args) => marks.push(args);
  return {context, marks};
}
const track = (spacing, count = 6) => ({objectId: "9", points: Array.from({length: count},
  (_, i) => ({sourceId: String(i + 1), mjd: 10 + i, ra: 100 + i * spacing, dec: 0}))});

test("star size follows the on-screen gap: dense stars shrink, sparse stars keep full size", () => {
  const radii = spacing => {
    const {context, marks} = loadDrawing();
    context.trajectory = track(spacing);
    vm.runInContext('ssTrajectories.set("9", trajectory); ssTrajectoryEnabled = true; drawSsTrajectories([])', context);
    return marks.map(mark => mark[2]);
  };
  const dense = radii(0.3), medium = radii(2), sparse = radii(20);
  assert.equal(Math.max(...dense) <= 2.5, true, "dense track: only small stars");
  assert.equal(dense.slice(0, -1).every(r => r === 1.5), true, "history never vanishes");
  assert.equal(dense.at(-1), 2.5, "the latest source stays a little larger");
  assert.equal(medium[1] > dense[1] && medium[1] < sparse[1], true, "size grows with distance");
  assert.deepEqual(sparse.slice(0, -1), [4, 4, 4, 4, 4]);
  assert.equal(sparse.at(-1), 7);
});

test("stars grow back as the camera zooms in and never overlap more than a quarter of the gap", () => {
  const radius = zoom => {
    const {context, marks} = loadDrawing(zoom);
    context.trajectory = track(0.5, 4);
    vm.runInContext('ssTrajectories.set("9", trajectory); ssTrajectoryEnabled = true; drawSsTrajectories([])', context);
    return marks[1][2];
  };
  assert.equal(radius(1) < radius(4) && radius(4) < radius(8), true);
  const {context, marks} = loadDrawing(8);
  context.trajectory = track(0.5, 4);
  vm.runInContext('ssTrajectories.set("9", trajectory); ssTrajectoryEnabled = true; drawSsTrajectories([])', context);
  const gap = 0.5 / 360 * 1200 * 8;
  assert.equal(marks[1][2] <= gap * 0.25 + 1e-9 || marks[1][2] === 4, true);
});

test("a single-source or wrapped trajectory still gets a finite star size", () => {
  const {context, marks} = loadDrawing(1, {ra: 0, dec: 0});
  context.trajectory = {objectId: "9", points: [{sourceId: "1", mjd: 10, ra: 359.9, dec: 0}]};
  vm.runInContext('ssTrajectories.set("9", trajectory); ssTrajectoryEnabled = true; drawSsTrajectories([])', context);
  assert.deepEqual(marks.map(mark => mark[2]), [7]);
  const wrapped = loadDrawing(1, {ra: 0, dec: 0});
  wrapped.context.trajectory = {objectId: "9", points: [
    {sourceId: "1", mjd: 10, ra: 359.9, dec: 0}, {sourceId: "2", mjd: 11, ra: 0.1, dec: 0}]};
  vm.runInContext('ssTrajectories.set("9", trajectory); ssTrajectoryEnabled = true; drawSsTrajectories([])', wrapped.context);
  assert.equal(wrapped.marks.every(mark => Number.isFinite(mark[2]) && mark[2] <= 7), true);
  // 0.2 degrees across the RA=0 seam is 0.2/360*1200 = 0.67 px, not almost the full canvas width.
  assert.equal(wrapped.marks[0][2] <= 2.5, true);
});

test("camera anchors cover the whole of every visible trajectory and vanish when hidden", () => {
  const context = loadTrajectory();
  const points = Array.from({length: 1000}, (_, i) => ({sourceId: String(i), mjd: 10 + i, ra: 100 + i / 100,
    dec: Math.sin(i / 100) * 5}));
  context.trajectory = {objectId: "9", points};
  vm.runInContext('ssTrajectories.set("9", trajectory)', context);
  assert.equal(vm.runInContext("getSsTrajectoryCameraAnchors().length", context), 0, "hidden trajectories do not move the camera");
  vm.runInContext("ssTrajectoryEnabled = true", context);
  const anchors = vm.runInContext("getSsTrajectoryCameraAnchors()", context);
  assert.equal(anchors.length < 300, true, "thinned");
  const ras = anchors.map(a => a.alert.ra), decs = anchors.map(a => a.alert.dec);
  assert.equal(Math.min(...ras), 100);
  assert.equal(Math.max(...ras), points.at(-1).ra);
  assert.equal(Math.max(...decs), Math.max(...points.map(p => p.dec)), "dec extreme is kept");
  assert.equal(Math.min(...decs), Math.min(...points.map(p => p.dec)));
  assert.equal(vm.runInContext("getSsTrajectoryCameraAnchors()", context), anchors, "memoized while unchanged");
});

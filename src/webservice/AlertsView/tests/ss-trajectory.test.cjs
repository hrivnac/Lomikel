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

test("SS history requests start only for arriving SS alerts and accumulate one response at a time", async () => {
  const calls = [];
  let releaseSecond;
  const context = loadTrajectory({fetch: async (url) => {
    calls.push(url);
    if (url.includes("id=123")) return {ok: true, json: async () => ({objectId: "123", sources: [{sourceId: "1", mjd: 10, ra: 180, dec: 1}]})};
    return new Promise(resolve => { releaseSecond = () => resolve({ok: true, json: async () => ({objectId: "456", sources: [{sourceId: "2", mjd: 11, ra: 181, dec: 2}]})}); });
  }});
  vm.runInContext("startSsTrajectoryLoad()", context);
  assert.equal(calls.length, 0, "the button alone loads nothing");
  const pending = vm.runInContext(`updateSsAlertCandidates([
    {objectId: "123", latestMjd: 10, fallback: null}, {objectId: "456", latestMjd: 11, fallback: null}])`, context);
  assert.equal(context.status.hidden, false);
  for (let i = 0; i < 20 && !releaseSecond; i++) await new Promise(resolve => setImmediate(resolve));
  assert.equal(typeof releaseSecond, "function");
  assert.equal(vm.runInContext("ssTrajectories.size", context), 1);
  assert.equal(vm.runInContext("getSsAlertRows().length", context), 1);
  releaseSecond();
  await pending;
  assert.equal(vm.runInContext("ssTrajectories.size", context), 2);
  assert.deepEqual(calls, ["SSTrajectory.jsp?id=123", "SSTrajectory.jsp?id=456"]);
  assert.match(context.status.textContent, /SS histories: 2\/2/);
});

test("a refresh replaces the SS alerts and stale history replies cannot repaint them", async () => {
  let release;
  const context = loadTrajectory({fetch: async (url) => {
    if (url.includes("id=123")) {
      return new Promise(resolve => { release = () => resolve({ok: true, json: async () => ({objectId: "123", sources: [{sourceId: "1", mjd: 10, ra: 180, dec: 0}]})}); });
    }
    return {ok: true, json: async () => ({objectId: "789", sources: [{sourceId: "7", mjd: 30, ra: 20, dec: 0}]})};
  }});
  const first = vm.runInContext('updateSsAlertCandidates([{objectId: "123", latestMjd: 10, fallback: null}])', context);
  for (let i = 0; i < 20 && !release; i++) await new Promise(resolve => setImmediate(resolve));
  const second = vm.runInContext('updateSsAlertCandidates([{objectId: "789", latestMjd: 30, fallback: null}])', context);
  release();
  await Promise.all([first, second]);
  assert.deepEqual([...vm.runInContext("ssTrajectories.keys()", context)], ["789"]);
  assert.deepEqual(JSON.parse(JSON.stringify(vm.runInContext("getSsAlertRows()", context))).map(row => row["r:ssObjectId"]), ["789"]);
});

test("latest SS payload yields validated objects; only a single ES position is used before the history", () => {
  const context = loadTrajectory();
  context.payload = {
    ssMjdHits: [
      {_id: "111", _source: {mjd: [61350.1, 61359.2]}},
      {_id: "222", _source: {mjd: 61359.25}},
      {_id: "333", _source: {mjd: [61359.3]}},
      {_id: "not-numeric", _source: {mjd: [1]}},
      {_id: "444", _source: {mjd: ["x"]}},
      {_id: "111", _source: {mjd: [61359.2]}},
    ],
    ssRadecDocs: [
      {_id: "222", found: true, _source: {location: {lon: -170, lat: 5}}},
      {_id: "333", found: true, _source: {location: [{lon: 1, lat: 2}, {lon: 3, lat: 4}]}},
    ],
  };
  const result = JSON.parse(JSON.stringify(vm.runInContext("normalizeLatestSsPayload(payload)", context)));
  assert.deepEqual(result, [
    {objectId: "111", latestMjd: 61359.2, fallback: null},
    {objectId: "222", latestMjd: 61359.25, fallback: {ra: 10, dec: 5}},
    {objectId: "333", latestMjd: 61359.3, fallback: null},
  ]);
  assert.deepEqual(JSON.parse(JSON.stringify(vm.runInContext("normalizeLatestSsPayload({mjdHits: []})", context))), [],
    "older JSP responses without SS objects stay valid");
});

test("an SS alert sits on the paired source at its ES MJD, else on the latest paired source", () => {
  const context = loadTrajectory();
  vm.runInContext(`ssTrajectories.set("1", {objectId: "1", designation: "K25A01B", latestMjd: 30, points: [
      {sourceId: "a", mjd: 10, ra: 1, dec: 1}, {sourceId: "b", mjd: 20, ra: 2, dec: 2}, {sourceId: "c", mjd: 30, ra: 3, dec: 3}]});
    ssTrajectories.set("2", {objectId: "2", latestMjd: 5, points: [{sourceId: "z", mjd: 5, ra: 9, dec: 9}]});
    ssAlertCandidates = new Map([
      ["1", {objectId: "1", latestMjd: 20.000001, fallback: null}],
      ["2", {objectId: "2", latestMjd: 99, fallback: null}],
      ["3", {objectId: "3", latestMjd: 7, fallback: null}]]);`, context);
  const rows = JSON.parse(JSON.stringify(vm.runInContext("getSsAlertRows()", context)));
  assert.deepEqual(rows, [
    {"v:survey": "LSST", "v:classification": "LSST SS source", "r:ssObjectId": "1", "r:diaSourceId": "b",
      "r:midpointMjdTai": 20, "r:ra": 2, "r:dec": 2, "v:designation": "K25A01B"},
    {"v:survey": "LSST", "v:classification": "LSST SS source", "r:ssObjectId": "2", "r:diaSourceId": "z",
      "r:midpointMjdTai": 5, "r:ra": 9, "r:dec": 9},
  ], "an object without history or unambiguous position is not placed");
});

test("Follow alerts shows a history only while its SS alert flashes; All alerts shows every loaded one", () => {
  const context = loadTrajectory();
  vm.runInContext(`ssTrajectoryEnabled = true; classes = {};
    ssTrajectories.set("1", {objectId: "1", points: [{sourceId: "a", mjd: 1, ra: 1, dec: 1}, {sourceId: "b", mjd: 2, ra: 2, dec: 2}]});
    ssTrajectories.set("2", {objectId: "2", points: [{sourceId: "c", mjd: 1, ra: 5, dec: 5}]});
    var showAllAlerts = false;`, context);
  context.flash = {alert: {survey: "LSST", class: "LSST SS source", objectId: "1", sourceId: "b", ra: 2, dec: 2}, color: "1,2,3"};
  const follow = vm.runInContext("getVisibleSsTrajectories([flash])", context);
  assert.deepEqual(Array.from(follow, item => item.trajectory.objectId), ["1"]);
  assert.equal(follow[0].color, "1,2,3");
  assert.deepEqual(Array.from(follow[0].points, item => item.point.sourceId), ["a"], "the current source is the flash itself");
  assert.equal(vm.runInContext("getVisibleSsTrajectories([]).length", context), 0);
  vm.runInContext("showAllAlerts = true", context);
  assert.equal(vm.runInContext("getVisibleSsTrajectories([]).length", context), 2);
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

test("SS trajectories keep a safe designation for the Fink Portal link", () => {
  const context = loadTrajectory();
  const make = designation => {
    context.payload = {objectId: "20892028060578637", designation,
      sources: [{sourceId: "1", mjd: 10, ra: 1, dec: 1}]};
    return vm.runInContext("normalizeSsTrajectory(payload)", context).designation;
  };
  assert.equal(make("J90W03M"), "J90W03M");
  assert.equal(make(" 1990 WM3 "), "1990 WM3");
  for (const bad of [undefined, null, 5, "", "../x", "a/b", "x?y=1", "<script>", "a".repeat(80)]) {
    assert.equal(make(bad), undefined, String(bad));
  }
});

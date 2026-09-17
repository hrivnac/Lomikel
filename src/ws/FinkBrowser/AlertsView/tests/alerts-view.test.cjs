"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

const alertsView = path.resolve(__dirname, "..");

function loadUtils(overrides = {}) {
  const context = {
    URLSearchParams,
    canvas: {width: 3600, height: 1800},
    camera: {currentCenter: {ra: 180, dec: 0}, currentZoom: 1},
    fetchPeriod: 10,
    fetchStart: 48,
    nAlerts: 10,
    magMax: 6,
    fetchLSST: false,
    window: {location: {search: ""}},
    ...overrides,
  };
  vm.createContext(context);
  vm.runInContext(
    fs.readFileSync(path.join(alertsView, "utils.js"), "utf8"),
    context,
    {filename: "utils.js"},
  );
  return context;
}

test("sky projection uses one wrapped right-to-left RA convention", () => {
  const context = loadUtils();

  assert.equal(context.signedRaDelta(190, 180), 10);
  assert.equal(context.signedRaDelta(170, 180), -10);
  assert.equal(context.signedRaDelta(359, 1), -2);
  assert.equal(context.raDecToXY(190, 0).x, 1700);
  assert.equal(context.raDecToXY(170, 0).x, 1900);
});

test("circular RA bounds keep a seam-crossing group compact", () => {
  const context = loadUtils();
  const bounds = context.getCircularRaBounds([359, 1]);

  assert.equal(bounds.span, 2);
  assert.equal(bounds.center, 0);
  assert.equal(context.interpolateRa(359, 1, 0.5), 0);
});

test("projected sky polylines split at the moving RA seam", () => {
  const context = loadUtils({
    camera: {currentCenter: {ra: 42, dec: 0}, currentZoom: 2},
    canvas: {width: 1200, height: 600},
  });
  const projected = [
    context.raDecToXY(221, 0),
    context.raDecToXY(223, 0),
  ];
  const segments = context.splitProjectedPolyline(projected, 1200);

  assert.equal(Math.abs(projected[1].x - projected[0].x) > 1200, true);
  assert.deepEqual(Array.from(segments, segment => segment.length), [1, 1]);
});

test("Fink start dates are formatted in UTC", () => {
  const context = loadUtils();
  const now = new Date("2026-07-01T12:00:00+02:00");

  assert.equal(context.formatStartDateUtc(48, now), "2026-06-29 10:00:00");
});

async function loadData() {
  const requests = [];
  const elements = new Map();
  let timerCallback;
  const context = {
    URLSearchParams,
    clearInterval() {},
    console: {error() {}, log() {}},
    d3: {csv: async () => []},
    document: {
      getElementById(id) {
        if (!elements.has(id)) elements.set(id, {innerHTML: "", textContent: ""});
        return elements.get(id);
      },
    },
    fetch: async url => {
      if (!String(url).startsWith("http")) {
        return {ok: true, status: 200, async json() { return {features: []}; }};
      }
      requests.push(url);
      const parsed = new URL(url);
      const cls = parsed.searchParams.get("class");
      if (!cls) throw new Error(`Unexpected non-ZTF request: ${url}`);
      return {
        ok: true,
        status: 200,
        async json() {
          return [{
            "i:objectId": `object-${cls}`,
            "i:jd": 2460000.5,
            "i:ra": 42,
            "i:dec": 10,
          }];
        },
      };
    },
    setInterval(callback) {
      timerCallback = callback;
      return 1;
    },
    window: {location: {search: ""}},
  };
  vm.createContext(context);
  for (const file of ["params.js", "utils.js", "update.js", "data.js"]) {
    vm.runInContext(
      fs.readFileSync(path.join(alertsView, file), "utf8"),
      context,
      {filename: file},
    );
  }
  await vm.runInContext("initialRefreshPromise", context);
  return {context, requests, timerCallback};
}

test("refresh scheduling calls ZTF once and leaves stopped LSST paused", async () => {
  const {context, requests, timerCallback} = await loadData();

  assert.equal(typeof timerCallback, "function");
  assert.equal(requests.length, 5);
  assert.equal(requests.every(url => url.includes("api.ztf.fink-portal.org")), true);
  assert.equal(vm.runInContext("surveyStatus.LSST.state", context), "paused");
  assert.equal(vm.runInContext("alertsPool.length", context), 5);
});

test("refresh replaces the survey snapshot instead of accumulating duplicates", async () => {
  const {context, requests, timerCallback} = await loadData();

  await timerCallback();
  assert.equal(requests.length, 10);
  assert.equal(vm.runInContext("alertsPool.length", context), 5);
});

test("status code loads before data startup invokes it", () => {
  const html = fs.readFileSync(path.join(alertsView, "index.html"), "utf8");
  assert.equal(html.indexOf('src="update.js"') < html.indexOf('src="data.js"'), true);
});

test("bundled stellar catalogue is compact and magnitude limited", () => {
  const compactPath = path.join(alertsView, "hyg_v38_mag6.csv");
  const originalPath = path.join(alertsView, "hyg_v38.csv");

  assert.equal(fs.existsSync(originalPath), false);
  assert.equal(fs.statSync(compactPath).size < 200_000, true);
  const lines = fs.readFileSync(compactPath, "utf8").trim().split("\n");
  assert.equal(lines[0], "ra,dec,mag,proper");
  assert.equal(lines.length - 1, 5019);
  for (const line of lines.slice(1)) {
    const [ra, , mag] = line.split(",", 4);
    assert.notEqual(Number(ra), 0);
    assert.equal(Number(mag) < 6, true);
  }
});

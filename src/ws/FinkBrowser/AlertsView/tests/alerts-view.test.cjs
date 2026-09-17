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

test("portal links allow only known surveys and encode API object identifiers", () => {
  const context = loadUtils();
  const hostileId = 'bad\"><img src=x onerror=alert(1)>';

  assert.equal(
    context.getPortalUrl({survey: "ZTF", objectId: hostileId}),
    `https://ztf.fink-portal.org/${encodeURIComponent(hostileId)}`,
  );
  assert.equal(context.getPortalUrl({survey: "LSST", objectId: "123"}), "https://lsst.fink-portal.org/123");
  assert.equal(context.getPortalUrl({survey: "unknown", objectId: "123"}), null);
});

function loadDrawing(overrides = {}) {
  const noop = () => {};
  const legend = {replaceChildren: noop};
  const context = loadUtils({
    classes: {},
    flashes: [],
    document: {
      createElement: () => ({append: noop, style: {}}),
      getElementById: () => legend,
    },
    ctx: {
      arc: noop, beginPath: noop, closePath: noop, fill: noop,
      fillText: noop, lineTo: noop, moveTo: noop, restore: noop,
      save: noop, stroke: noop,
    },
    ...overrides,
  });
  for (const file of ["consts.js", "drawing.js"]) {
    vm.runInContext(
      fs.readFileSync(path.join(alertsView, file), "utf8"),
      context,
      {filename: file},
    );
  }
  return context;
}

test("static sky geometry is generated once and reused by draw calls", () => {
  const context = loadDrawing();
  const before = vm.runInContext("staticSkyGeometry", context);

  vm.runInContext("drawEcliptic(); drawEcliptic(); drawEclipticMonths(); drawGalacticPlane();", context);
  const after = vm.runInContext("staticSkyGeometry", context);

  assert.equal(after, before);
  assert.equal(before.ecliptic.length, 361);
  assert.equal(before.eclipticMonths.length, 12);
  assert.equal(before.galacticPlane.length, 361);
});

test("generated sky curves stay seam-safe across camera centers and zooms", () => {
  const context = loadDrawing();
  const curves = vm.runInContext("[staticSkyGeometry.ecliptic, staticSkyGeometry.galacticPlane]", context);

  for (const center of [0, 42, 90, 180, 270, 359]) {
    for (const zoom of [1, 2, 8]) {
      context.camera.currentCenter.ra = center;
      context.camera.currentZoom = zoom;
      for (const curve of curves) {
        context.curve = curve;
        const segments = vm.runInContext(
          "splitProjectedPolyline(curve.map(([ra, dec]) => raDecToXY(ra, dec)), canvas.width)",
          context,
        );
        for (const segment of segments) {
          for (let i = 1; i < segment.length; i++) {
            assert.equal(Math.abs(segment[i].x - segment[i - 1].x) < context.canvas.width / 2, true);
          }
        }
      }
    }
  }
});

test("reduced-motion stars do not sparkle between frames", () => {
  let now = 0;
  const points = [];
  const noop = () => {};
  const context = loadDrawing({
    Date: {now: () => now},
    ctx: {
      beginPath: noop, closePath: noop, fill: noop,
      lineTo: (x, y) => points.push([x, y]),
    },
  });

  vm.runInContext('drawStar(0, 0, 10, "255,255,255", 1, 0, 5, false);', context);
  const firstFrame = points.splice(0);
  now = 100;
  vm.runInContext('drawStar(0, 0, 10, "255,255,255", 1, 0, 5, false);', context);
  assert.deepEqual(points, firstFrame);
});

test("legend DOM is rebuilt only when the active classes change", () => {
  let replacements = 0;
  const legend = {
    replaceChildren(...children) {
      replacements += 1;
      this.children = children;
    },
  };
  const context = loadDrawing({
    classes: {A: "1,2,3", B: "4,5,6"},
    flashes: [{alert: {class: "A"}}],
    document: {
      createElement: tagName => ({tagName, append() {}, style: {}}),
      getElementById: () => legend,
    },
  });

  vm.runInContext("updateLegend(); updateLegend();", context);
  assert.equal(replacements, 1);
  vm.runInContext("flashes.push({alert: {class: 'B'}}); updateLegend();", context);
  assert.equal(replacements, 2);
  assert.equal(legend.children.length, 2);
});

function loadAppForInteraction() {
  const noop = () => {};
  const listeners = new Map();
  const timers = new Map();
  let nextTimerId = 1;
  const makeElement = () => ({
    hidden: true,
    style: {},
    attributes: new Map(),
    eventListeners: new Map(),
    addEventListener(type, handler) {
      this.eventListeners.set(type, handler);
      listeners.set(type, handler);
    },
    append: noop,
    emit(type, event = {}) { this.eventListeners.get(type)?.(event); },
    getAttribute(name) { return this.attributes.get(name) ?? null; },
    getBoundingClientRect: () => ({left: 0, top: 0, width: 1200, height: 600}),
    getContext: () => ({
      arc: noop, beginPath: noop, clearRect: noop, fill: noop, fillRect: noop,
      fillText: noop, lineTo: noop, moveTo: noop, restore: noop, save: noop,
      stroke: noop, strokeRect: noop,
    }),
    replaceChildren: noop,
    setAttribute(name, value) { this.attributes.set(name, String(value)); },
  });
  const elements = new Map([
    ["sky", makeElement()], ["overview", makeElement()], ["tooltip", makeElement()],
    ["btnDynamic", makeElement()], ["btnWhole", makeElement()],
    ["helpButton", makeElement()], ["logo-help", makeElement()],
    ["recentAlerts", makeElement()], ["viewInfo", makeElement()],
  ]);
  elements.get("sky").width = 1200;
  elements.get("sky").height = 600;
  elements.get("overview").width = 200;
  elements.get("overview").height = 100;
  const context = {
    Math, Date,
    alertsPool: [], classes: {},
    clearTimeout: id => timers.delete(id),
    document: {
      addEventListener: noop,
      createElement: makeElement,
      createTextNode: text => ({textContent: text}),
      getElementById: id => elements.get(id) || makeElement(),
    },
    drawConstellationLabels: noop, drawConstellations: noop, drawEcliptic: noop,
    drawEclipticMonths: noop, drawGalacticPlane: noop, drawOverview: noop,
    drawStar: noop, drawStars: noop, getCircularRaBounds: () => ({center: 0, span: 0}),
    interpolateRa: (a, b) => b, normalizeRa: value => value,
    raDecToXY: () => ({x: 0, y: 0}), requestAnimationFrame: noop,
    setTimeout(callback, delay) {
      const id = nextTimerId++;
      timers.set(id, {callback, delay});
      return id;
    },
    updateLegend: noop,
    window: {
      addEventListener: noop,
      innerHeight: 600,
      innerWidth: 1200,
      matchMedia: () => ({matches: false}),
    },
  };
  vm.createContext(context);
  vm.runInContext(fs.readFileSync(path.join(alertsView, "app.js"), "utf8"), context, {filename: "app.js"});
  const runTimersAtDelay = delay => {
    const due = [...timers.entries()].filter(([, timer]) => timer.delay === delay);
    for (const [id, timer] of due) {
      timers.delete(id);
      timer.callback();
    }
  };
  return {context, elements, listeners, runTimersAtDelay, timers};
}

test("view and help controls expose their state to keyboard and assistive technology", () => {
  const {context, elements} = loadAppForInteraction();

  vm.runInContext('setCameraMode("whole");', context);
  assert.equal(elements.get("btnDynamic").getAttribute("aria-pressed"), "false");
  assert.equal(elements.get("btnWhole").getAttribute("aria-pressed"), "true");
  vm.runInContext("toggleHelp();", context);
  assert.equal(elements.get("logo-help").hidden, false);
  assert.equal(elements.get("helpButton").getAttribute("aria-expanded"), "true");
});

test("pointer can move from a sky marker into the tooltip without hiding it", () => {
  const {elements, runTimersAtDelay} = loadAppForInteraction();
  const canvas = elements.get("sky");
  const tooltip = elements.get("tooltip");
  tooltip.style.display = "flex";

  canvas.emit("pointerleave", {pointerType: "mouse"});
  tooltip.emit("pointerenter");
  runTimersAtDelay(200);

  assert.equal(tooltip.style.display, "flex");
});

test("touch pointerleave keeps the tooltip available for a second tap", () => {
  const {elements, runTimersAtDelay} = loadAppForInteraction();
  const canvas = elements.get("sky");
  const tooltip = elements.get("tooltip");
  tooltip.style.display = "flex";

  canvas.emit("pointerleave", {pointerType: "touch"});
  runTimersAtDelay(200);

  assert.equal(tooltip.style.display, "flex");
});

async function loadData({failClasses = [], emptyClasses = []} = {}) {
  const requests = [];
  const elements = new Map();
  let timerCallback;
  const context = {
    URLSearchParams,
    clearInterval() {},
    console: {error() {}, log() {}},

    document: {
      getElementById(id) {
        if (!elements.has(id)) elements.set(id, {innerHTML: "", textContent: ""});
        return elements.get(id);
      },
    },
    fetch: async url => {
      if (!String(url).startsWith("http")) {
        return {
          ok: true,
          status: 200,
          async json() { return {features: []}; },
          async text() { return "ra,dec,mag,proper\n"; },
        };
      }
      requests.push(url);
      const parsed = new URL(url);
      const cls = parsed.searchParams.get("class");
      if (!cls) throw new Error(`Unexpected non-ZTF request: ${url}`);
      if (failClasses.includes(cls)) {
        return {ok: false, status: 503, async json() { return []; }};
      }
      return {
        ok: true,
        status: 200,
        async json() {
          if (emptyClasses.includes(cls)) return [];
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

test("simultaneous survey refreshes are coalesced", async () => {
  const {context, requests} = await loadData();
  const before = requests.length;

  const results = await vm.runInContext('Promise.all([fetchAlerts("ZTF"), fetchAlerts("ZTF")])', context);
  assert.deepEqual(Array.from(results), [true, false]);
  assert.equal(requests.length - before, 5);
});

test("partial, empty, and failed API responses have distinct states", async () => {
  const classNames = [
    "Microlensing candidate",
    "Early SN Ia candidate",
    "SN candidate",
    "Solar System candidate",
    "Solar System MPC",
  ];
  const partial = await loadData({failClasses: [classNames[0]]});
  assert.equal(vm.runInContext("surveyStatus.ZTF.state", partial.context), "partial");
  assert.equal(vm.runInContext("surveyStatus.ZTF.count", partial.context), 4);

  const empty = await loadData({emptyClasses: classNames});
  assert.equal(vm.runInContext("surveyStatus.ZTF.state", empty.context), "empty");
  assert.equal(vm.runInContext("surveyStatus.ZTF.count", empty.context), 0);

  const failed = await loadData({failClasses: classNames});
  assert.equal(vm.runInContext("surveyStatus.ZTF.state", failed.context), "error");
  assert.equal(vm.runInContext("surveyStatus.ZTF.errors.length", failed.context), 5);
});

test("status code loads before data startup invokes it", () => {
  const html = fs.readFileSync(path.join(alertsView, "index.html"), "utf8");
  assert.equal(html.indexOf('src="update.js"') < html.indexOf('src="data.js"'), true);
});

test("page self-hosts scripts and exposes keyboard-accessible status and help", () => {
  const html = fs.readFileSync(path.join(alertsView, "index.html"), "utf8");
  const productionJs = ["app.js", "drawing.js", "update.js"]
    .map(file => fs.readFileSync(path.join(alertsView, file), "utf8"))
    .join("\n");

  assert.match(html, /name="viewport"/);
  assert.match(html, /http-equiv="Content-Security-Policy"/);
  assert.doesNotMatch(html, /<script[^>]+src="https?:/);
  assert.doesNotMatch(html, /d3(?:\.v|\.js|\.)/i);
  assert.doesNotMatch(fs.readFileSync(path.join(alertsView, "data.js"), "utf8"), /\bd3\./);
  assert.match(html, /id="sky"[^>]+role="img"/);
  assert.match(html, /id="btnDynamic"[^>]+aria-pressed="true"/);
  assert.match(html, /id="helpButton"[^>]+aria-expanded="false"/);
  assert.match(html, /id="statusPanel"[^>]+aria-live="polite"/);
  assert.match(html, /id="recentAlerts"/);
  assert.doesNotMatch(productionJs, /\.innerHTML\s*=/);

  const css = fs.readFileSync(path.join(alertsView, "style.css"), "utf8");
  assert.match(css, /:focus-visible/);
  assert.match(css, /@media \(max-width: 700px\)/);
  assert.match(css, /prefers-reduced-motion: reduce/);
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

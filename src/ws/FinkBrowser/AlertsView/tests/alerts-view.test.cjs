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

test("screen positions repeat across the horizontal sky boundary", () => {
  const context = loadUtils({
    canvas: {width: 1200, height: 600},
    camera: {currentCenter: {ra: 180, dec: 0}, currentZoom: 1},
  });
  const positions = context.getWrappedScreenPositions({x: 1200, y: 300}, 20);
  const visited = [];
  context.forEachWrappedScreenPosition(
    {x: 1200, y: 300},
    20,
    (x, y) => visited.push([x, y]),
  );

  assert.deepEqual(Array.from(positions, point => point.x), [0, 1200]);
  assert.equal(positions.every(point => point.y === 300), true);
  assert.deepEqual(visited, [[0, 300], [1200, 300]]);
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

test("runtime alert settings accept only nonblank bounded integers", () => {
  const context = loadUtils();
  const valid = vm.runInContext(`parseAlertSettings({
    fetchPeriod: "15",
    fetchStart: "72",
    nAlerts: "20",
    magMax: "5"
  })`, context);

  assert.deepEqual(JSON.parse(JSON.stringify(valid)), {
    fetchPeriod: 15,
    fetchStart: 72,
    nAlerts: 20,
    magMax: 5,
  });
  const serverContext = loadUtils({latestAlertsAvailable: true});
  assert.deepEqual(
    JSON.parse(JSON.stringify(vm.runInContext(`parseAlertSettings({
      fetchPeriod: "0", fetchStart: "0", nAlerts: "10", magMax: "6"
    })`, serverContext))),
    {fetchPeriod: 0, fetchStart: 0, nAlerts: 10, magMax: 6},
  );
  assert.throws(
    () => vm.runInContext(`parseAlertSettings({
      fetchPeriod: "0", fetchStart: "0", nAlerts: "10", magMax: "6"
    })`, context),
    /fetchStart must be between 1 and 720/,
  );
  for (const candidate of [
    {fetchPeriod: "", fetchStart: "48", nAlerts: "10", magMax: "6"},
    {fetchPeriod: "1.5", fetchStart: "48", nAlerts: "10", magMax: "6"},
    {fetchPeriod: "-1", fetchStart: "48", nAlerts: "10", magMax: "6"},
    {fetchPeriod: "10", fetchStart: "-1", nAlerts: "10", magMax: "6"},
    {fetchPeriod: "10", fetchStart: "721", nAlerts: "10", magMax: "6"},
    {fetchPeriod: "10", fetchStart: "48", nAlerts: "101", magMax: "6"},
    {fetchPeriod: "10", fetchStart: "48", nAlerts: "10", magMax: "7"},
  ]) {
    context.candidate = candidate;
    assert.throws(() => vm.runInContext("parseAlertSettings(candidate)", context), /must be an integer|between/);
  }
});

test("safe standalone defaults are used before server capability detection", () => {
  const context = {};
  vm.createContext(context);
  vm.runInContext(fs.readFileSync(path.join(alertsView, "params.js"), "utf8"), context, {filename: "params.js"});

  assert.equal(vm.runInContext("fetchPeriod", context), 10);
  assert.equal(vm.runInContext("fetchStart", context), 48);
  assert.equal(vm.runInContext("fetchLSST", context), false);
  assert.equal(vm.runInContext("latestAlertsAvailable", context), false);
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

test("background stars repeat across the horizontal sky boundary", () => {
  const arcs = [];
  const noop = () => {};
  const context = loadDrawing({
    prefersReducedMotion: true,
    stars: [{ra: 0, dec: 0, r: 2, proper: "Boundary", alpha: 1, twinkleSpeed: 0}],
    ctx: {
      arc: (x, y) => arcs.push([x, y]),
      beginPath: noop, fill: noop, fillText: noop,
    },
    canvas: {width: 1200, height: 600},
    camera: {currentCenter: {ra: 180, dec: 0}, currentZoom: 1},
  });

  vm.runInContext("drawStars();", context);
  assert.deepEqual(Array.from(arcs, point => point[0]), [0, 1200]);
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

function loadAppForInteraction({withUtils = false} = {}) {
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
    Math, Date, URLSearchParams,
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
    signedRaDelta: (ra, center) => ((ra - center + 540) % 360) - 180,
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
      location: {search: ""},
      matchMedia: () => ({matches: false}),
    },
  };
  vm.createContext(context);
  if (withUtils) {
    vm.runInContext(fs.readFileSync(path.join(alertsView, "utils.js"), "utf8"), context, {filename: "utils.js"});
  }
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

test("camera zooms out before crossing the RA seam so all active alerts stay visible", () => {
  const {context} = loadAppForInteraction();
  const positions = vm.runInContext(`(() => {
    interpolateRa = (current, target, amount) => ((current + ((((target - current) % 360) + 540) % 360 - 180) * amount) % 360 + 360) % 360;
    raDecToXY = (ra, dec) => {
      const dx = (((ra - camera.currentCenter.ra) % 360) + 540) % 360 - 180;
      return {
        x: canvas.width / 2 - dx / 360 * canvas.width * camera.currentZoom,
        y: canvas.height / 2 - (dec - camera.currentCenter.dec) / 180 * canvas.height * camera.currentZoom
      };
    };
    flashes = [
      {alert: {ra: 359, dec: 0}},
      {alert: {ra: 1, dec: 0}}
    ];
    camera.currentCenter = {ra: 180, dec: 0};
    camera.targetCenter = {ra: 0, dec: 0};
    camera.currentZoom = 8;
    camera.targetZoom = 8;
    smoothCamera();
    return flashes.map(flash => raDecToXY(flash.alert.ra, flash.alert.dec).x);
  })()`, context);

  assert.equal(positions.every(x => x >= 0 && x <= context.window.innerWidth), true);
});

test("alert markers draw and hit-test on both sides of the horizontal boundary", () => {
  const {context} = loadAppForInteraction();
  const result = vm.runInContext(`(() => {
    raDecToXY = () => ({x: canvas.width, y: 300});
    getWrappedScreenPositions = (position, padding) => [
      {...position, x: 0},
      position
    ];
    const flash = new Flash({survey: "ZTF", ra: 0, dec: 0, class: "SN candidate", objectId: "wrapped", jd: 1});
    flash.startTime = Date.now() - 1000;
    flash.draw();
    flashes = [flash];
    return {
      xs: flash.positions.map(position => position.x),
      hitLeft: findFlashAt(0, 300) === flash,
      hitRight: findFlashAt(canvas.width, 300) === flash
    };
  })()`, context);

  assert.deepEqual(Array.from(result.xs), [0, 1200]);
  assert.equal(result.hitLeft, true);
  assert.equal(result.hitRight, true);
});

test("Flash uses the real wrap helper for drawing and hit-testing", () => {
  const {context} = loadAppForInteraction({withUtils: true});
  const result = vm.runInContext(`(() => {
    camera.currentCenter = {ra: 180, dec: 0};
    camera.currentZoom = 1;
    const flash = new Flash({survey: "ZTF", ra: 0, dec: 0, class: "SN candidate", objectId: "integrated", jd: 1});
    flash.startTime = Date.now() - 1000;
    flash.draw();
    flashes = [flash];
    return {
      xs: flash.positions.map(position => position.x),
      hitLeft: findFlashAt(0, canvas.height / 2) === flash,
      hitRight: findFlashAt(canvas.width, canvas.height / 2) === flash
    };
  })()`, context);

  assert.deepEqual(Array.from(result.xs), [0, 1200]);
  assert.equal(result.hitLeft, true);
  assert.equal(result.hitRight, true);
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

async function loadData({failClasses = [], emptyClasses = [], search = "", jspMode = "available"} = {}) {
  const requests = [];
  const probeRequests = [];
  const replacedUrls = [];
  const elements = new Map();
  const timerState = {callback: null, delay: null, cleared: []};
  let probeTimeoutCallback = null;
  const context = {
    AbortController, URLSearchParams,
    clearTimeout() {},
    clearInterval(id) { timerState.cleared.push(id); },
    console: {error() {}, log() {}},

    document: {
      getElementById(id) {
        if (!elements.has(id)) elements.set(id, {innerHTML: "", textContent: ""});
        return elements.get(id);
      },
    },
    fetch: async (url, options = {}) => {
      if (String(url).startsWith("LatestAlerts.jsp?probe=1")) {
        probeRequests.push(url);
        if (jspMode === "timeout") {
          return new Promise((_resolve, reject) => {
            options.signal.addEventListener("abort", () => reject(new Error("aborted")));
          });
        }
        if (jspMode === "network-error") throw new TypeError("Failed to fetch");
        if (jspMode === "missing") {
          return {ok: false, status: 404, async json() { return {}; }};
        }
        if (jspMode === "source") {
          return {
            ok: true,
            status: 200,
            async json() { throw new SyntaxError("Unexpected token '<'"); },
          };
        }
        return {
          ok: true,
          status: 200,
          async json() { return {latestAlerts: true}; },
        };
      }
      if (String(url).startsWith("LatestAlerts.jsp")) {
        requests.push(url);
        return {
          ok: true,
          status: 200,
          async json() {
            return {
              mjdHits: [{_id: "lsst-dia-object", _source: {mjd: [61235.4]}}],
              radecDocs: [{
                _id: "lsst-dia-object",
                found: true,
                _source: {location: {lon: 133.1, lat: -24}},
              }],
            };
          },
        };
      }
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
    setInterval(callback, delay) {
      timerState.callback = callback;
      timerState.delay = delay;
      return 1;
    },
    setTimeout(callback, delay) {
      probeTimeoutCallback = callback;
      timerState.probeTimeoutDelay = delay;
      return 2;
    },
    window: {
      location: {search, pathname: "/AlertsView/", hash: ""},
      history: {replaceState(_state, _title, url) { replacedUrls.push(url); }},
    },
  };
  vm.createContext(context);
  for (const file of ["params.js", "utils.js", "update.js", "data.js"]) {
    vm.runInContext(
      fs.readFileSync(path.join(alertsView, file), "utf8"),
      context,
      {filename: file},
    );
  }
  if (jspMode === "timeout") probeTimeoutCallback();
  await vm.runInContext("initialRefreshPromise", context);
  return {context, elements, probeRequests, requests, timerCallback: timerState.callback, timerState, replacedUrls};
}

test("startup installs the refresh timer before waiting for network data", () => {
  const source = fs.readFileSync(path.join(alertsView, "data.js"), "utf8");
  const initialize = source.slice(source.indexOf("async function initializeAlertsData"));
  const refreshStart = initialize.indexOf("const initialRefresh = refreshEnabledSurveys();");
  const schedule = initialize.indexOf("scheduleRefreshTimer();", refreshStart);
  const wait = initialize.indexOf("await initialRefresh;", schedule);
  assert.equal(refreshStart >= 0 && schedule > refreshStart && wait > schedule, true);
});

test("refresh scheduling calls ZTF once and leaves stopped LSST paused", async () => {
  const {context, requests, timerCallback} = await loadData({
    search: "?fetchPeriod=10&fetchStart=48&fetchLSST=false",
  });

  assert.equal(typeof timerCallback, "function");
  assert.equal(requests.length, 5);
  assert.equal(requests.every(url => url.includes("api.ztf.fink-portal.org")), true);
  assert.equal(vm.runInContext("surveyStatus.LSST.state", context), "paused");
  assert.equal(vm.runInContext("alertsPool.length", context), 5);
});

test("JSP capability enables default latest mode for both surveys", async () => {
  const {context, probeRequests, requests, timerCallback, timerState} = await loadData();
  const ztfRequests = requests.filter(url => String(url).includes("api.ztf.fink-portal.org"));
  const lsstRequests = requests.filter(url => String(url).startsWith("LatestAlerts.jsp"));

  assert.deepEqual(probeRequests, ["LatestAlerts.jsp?probe=1"]);
  assert.equal(ztfRequests.length, 5);
  assert.equal(ztfRequests.every(url => !new URL(url).searchParams.has("startdate")), true);
  assert.deepEqual(lsstRequests, ["LatestAlerts.jsp?n=10"]);
  assert.equal(timerCallback, null);
  assert.equal(timerState.delay, null);
  assert.equal(vm.runInContext("latestAlertsAvailable", context), true);
  assert.equal(vm.runInContext("fetchStart", context), 0);
  assert.equal(vm.runInContext("fetchPeriod", context), 0);
  assert.equal(vm.runInContext("fetchLSST", context), true);
  assert.equal(vm.runInContext("surveyStatus.LSST.state", context), "ready");
  assert.equal(vm.runInContext("alertsPool.length", context), 6);
});

test("missing or unexecuted JSP selects standalone timed defaults", async () => {
  for (const jspMode of ["missing", "source", "network-error", "timeout"]) {
    const {context, probeRequests, requests, timerCallback, timerState} = await loadData({jspMode});
    const ztfRequests = requests.filter(url => String(url).includes("api.ztf.fink-portal.org"));

    assert.deepEqual(probeRequests, ["LatestAlerts.jsp?probe=1"]);
    assert.equal(requests.some(url => String(url).startsWith("LatestAlerts.jsp")), false);
    assert.equal(ztfRequests.length, 5);
    assert.equal(ztfRequests.every(url => new URL(url).searchParams.has("startdate")), true);
    assert.equal(vm.runInContext("latestAlertsAvailable", context), false);
    assert.equal(vm.runInContext("fetchStart", context), 48);
    assert.equal(vm.runInContext("fetchPeriod", context), 10);
    assert.equal(vm.runInContext("fetchLSST", context), false);
    assert.equal(vm.runInContext("surveyStatus.LSST.state", context), "paused");
    assert.equal(timerState.delay, 10 * 60 * 1000);
    assert.equal(typeof timerCallback, "function");
  }
});

test("standalone mode replaces an unsupported zero look-back with 48 hours", async () => {
  const {context, requests, replacedUrls} = await loadData({
    jspMode: "missing",
    search: "?fetchStart=0&fetchPeriod=0&fetchLSST=false",
  });

  assert.equal(vm.runInContext("fetchStart", context), 48);
  assert.equal(vm.runInContext("fetchPeriod", context), 0);
  assert.equal(vm.runInContext("fetchLSST", context), false);
  assert.equal(requests.every(url => !String(url).startsWith("LatestAlerts.jsp")), true);
  assert.match(replacedUrls.at(-1), /fetchStart=48/);
});

test("detected mode updates the parameter controls and explanation", async () => {
  const server = await loadData();
  assert.equal(server.elements.get("fetchStartInput").min, "0");
  assert.match(server.elements.get("runtimeModeInfo").textContent, /latest alerts \(server\)/);
  assert.match(server.elements.get("fetchStartLabel").textContent, /0 = latest available/);

  const standalone = await loadData({jspMode: "missing"});
  assert.equal(standalone.elements.get("fetchStartInput").min, "1");
  assert.match(standalone.elements.get("runtimeModeInfo").textContent, /time window \(standalone\)/);
  assert.doesNotMatch(standalone.elements.get("fetchStartLabel").textContent, /0 = latest available/);
});

test("refresh replaces the survey snapshot instead of accumulating duplicates", async () => {
  const {context, requests, timerCallback} = await loadData({
    search: "?fetchPeriod=10&fetchStart=48&fetchLSST=false",
  });

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

test("runtime settings can rebuild stars and replace the refresh interval", async () => {
  const {context, timerState} = await loadData({
    search: "?fetchPeriod=10&fetchStart=48&fetchLSST=false",
  });
  const counts = vm.runInContext(`(() => {
    stellarCatalog.push(
      {ra: 15, dec: 1, mag: 3, proper: "bright"},
      {ra: 30, dec: 2, mag: 5, proper: "faint"}
    );
    magMax = 4;
    rebuildStars();
    const brightOnly = stars.length;
    magMax = 6;
    rebuildStars();
    fetchPeriod = 23;
    scheduleRefreshTimer();
    return [brightOnly, stars.length];
  })()`, context);

  assert.deepEqual(Array.from(counts), [1, 2]);
  assert.deepEqual(timerState.cleared, [1]);
  assert.equal(timerState.delay, 23 * 60 * 1000);
  assert.equal(typeof timerState.callback, "function");
});

test("changing parameters aborts and invalidates an in-flight alert refresh", async () => {
  const {context} = await loadData();
  let aborted = false;
  context.fetch = (_url, options = {}) => new Promise((resolve, reject) => {
    if (!options.signal) {
      reject(new Error("missing abort signal"));
      return;
    }
    options.signal.addEventListener("abort", () => {
      aborted = true;
      const error = new Error("aborted");
      error.name = "AbortError";
      reject(error);
    }, {once: true});
  });

  const pending = vm.runInContext('fetchAlerts("ZTF")', context);
  await Promise.resolve();
  vm.runInContext("invalidateAlertRefreshes()", context);
  const result = await pending;

  assert.equal(aborted, true);
  assert.equal(result, false);
  assert.equal(vm.runInContext("refreshInProgress.size", context), 0);
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

test("latest LSST payload joins stationary positions by object ID", async () => {
  const {context} = await loadData();
  context.latestPayload = {
    mjdHits: [
      {_id: "A", _source: {mjd: [10, 14, 12]}},
      {_id: "missing", _source: {mjd: [20]}},
    ],
    radecDocs: [
      {_id: "other", found: true, _source: {location: {lon: -40, lat: 3}}},
      {_id: "A", found: true, _source: {location: {lon: 179.5, lat: -24}}},
    ],
  };

  const rows = vm.runInContext("normalizeLatestLsstPayload(latestPayload)", context);
  assert.deepEqual(JSON.parse(JSON.stringify(rows)), [{
    "r:diaObjectId": "A",
    "r:midpointMjdTai": 14,
    "r:ra": 359.5,
    "r:dec": -24,
    "v:classification": "LSST DIA source",
  }]);
  context.latestPayload = {mjdHits: null, radecDocs: []};
  assert.throws(
    () => vm.runInContext("normalizeLatestLsstPayload(latestPayload)", context),
    /invalid latest LSST response/,
  );
});

test("same-origin LSST latest endpoint uses fixed read-only Elasticsearch indexes", () => {
  const endpointPath = path.join(alertsView, "LatestAlerts.jsp");
  assert.equal(fs.existsSync(endpointPath), true);
  const jsp = fs.readFileSync(endpointPath, "utf8");

  assert.match(jsp, /http:\/\/134\.158\.243\.139:24499/);
  for (const pathSuffix of ["dia_mjd/_search", "dia_radec/_mget"]) {
    assert.equal(jsp.includes(pathSuffix), true);
  }
  assert.doesNotMatch(jsp, /ss_mjd|ss_radec/);
  assert.doesNotMatch(jsp, /SmallHttpClient/);
  assert.match(jsp, /HttpURLConnection/);
  assert.match(jsp, /setConnectTimeout\s*\(/);
  assert.match(jsp, /setReadTimeout\s*\(/);
  assert.match(jsp, /disconnect\s*\(\s*\)/);
  assert.match(jsp, /getParameter\s*\(\s*["']probe["']\s*\)/);
  assert.match(jsp, /put\s*\(\s*["']latestAlerts["']\s*,\s*true\s*\)/);
  assert.doesNotMatch(jsp, /getParameter\s*\(\s*["']server["']/);
  assert.match(jsp, /Math\.min\s*\(\s*100\s*,\s*Math\.max\s*\(\s*1/);
  assert.match(jsp, /application\/json/);
});

test("local AlertsView target runs the JSP-capable FinkBrowser WAR", () => {
  const build = fs.readFileSync(path.resolve(alertsView, "../../../..", "ant/build.xml"), "utf8");
  const target = build.match(/<target name="start-AlertsView"[\s\S]*?<\/target>/)?.[0] || "";

  assert.match(target, /depends="war"/);
  assert.match(target, /FinkBrowser\.war/);
  assert.match(target, /\/FinkBrowser\/AlertsView/);
  assert.doesNotMatch(target, /http\.server/);
});

test("status code loads before data startup invokes it", () => {
  const html = fs.readFileSync(path.join(alertsView, "index.html"), "utf8");
  assert.equal(html.indexOf('src="update.js"') < html.indexOf('src="data.js"'), true);
});

test("displayed alert parameters open an accessible editing dialog", () => {
  const html = fs.readFileSync(path.join(alertsView, "index.html"), "utf8");
  assert.match(html, /id="paramsButton"[^>]+aria-haspopup="dialog"[^>]+aria-controls="paramsDialog"/);
  assert.match(html, /<dialog id="paramsDialog"[^>]+aria-labelledby="paramsHeading"/);
  assert.match(html, /<form id="paramsForm"/);
  for (const name of ["fetchPeriod", "fetchStart", "nAlerts", "magMax"]) {
    assert.match(html, new RegExp(`id="${name}Input"[^>]+name="${name}"`));
  }
  assert.match(html, /id="fetchPeriodInput"[^>]+min="0"/);
  assert.match(html, /0 = load once/);
  assert.match(html, /id="runtimeModeInfo"/);
  assert.match(html, /id="fetchStartLabel"/);
  assert.match(html, /id="fetchStartInput"[^>]*min="1"/);
  assert.match(html, /id="paramsError"[^>]+role="alert"/);
  assert.equal(html.indexOf('src="data.js"') < html.indexOf('src="settings.js"'), true);
  const settings = fs.readFileSync(path.join(alertsView, "settings.js"), "utf8");
  assert.match(settings, /paramsDialog\.addEventListener\("cancel"[\s\S]*preventDefault\(\)[\s\S]*paramsButton\.focus\(\)/);
});

function loadSettings() {
  const elements = new Map();
  const makeElement = () => ({
    hidden: true,
    textContent: "",
    value: "",
    listeners: new Map(),
    addEventListener(type, handler) { this.listeners.set(type, handler); },
    close() { this.open = false; },
    focus() {},
    showModal() { this.open = true; },
  });
  for (const id of [
    "paramsButton", "paramsDialog", "paramsForm", "paramsError", "paramsCancel",
    "fetchPeriodInput", "fetchStartInput", "nAlertsInput", "magMaxInput",
  ]) elements.set(id, makeElement());
  const calls = {invalidate: 0, rebuild: 0, schedule: 0, status: 0, refresh: 0, url: null};
  const context = {
    URLSearchParams,
    fetchPeriod: 10,
    fetchStart: 48,
    nAlerts: 10,
    magMax: 6,
    document: {getElementById: id => elements.get(id)},
    invalidateAlertRefreshes: () => { calls.invalidate += 1; },
    rebuildStars: () => { calls.rebuild += 1; },
    scheduleRefreshTimer: () => { calls.schedule += 1; },
    updateStatusPanel: () => { calls.status += 1; },
    refreshEnabledSurveys: async () => { calls.refresh += 1; },
    window: {
      history: {replaceState: (_state, _title, url) => { calls.url = url; }},
      location: {hash: "#sky", pathname: "/AlertsView/", search: "?fetchLSST=false"},
    },
  };
  vm.createContext(context);
  vm.runInContext(fs.readFileSync(path.join(alertsView, "utils.js"), "utf8"), context, {filename: "utils.js"});
  vm.runInContext(fs.readFileSync(path.join(alertsView, "settings.js"), "utf8"), context, {filename: "settings.js"});
  return {calls, context, elements};
}

test("applying alert settings updates data, stars, timer, URL, and refresh", async () => {
  const {calls, context} = loadSettings();
  await vm.runInContext(`applyAlertSettings({
    fetchPeriod: "15", fetchStart: "72", nAlerts: "20", magMax: "5"
  })`, context);

  assert.equal(context.fetchPeriod, 15);
  assert.equal(context.fetchStart, 72);
  assert.equal(context.nAlerts, 20);
  assert.equal(context.magMax, 5);
  assert.deepEqual(calls, {
    invalidate: 1, rebuild: 1, schedule: 1, status: 1, refresh: 1,
    url: "/AlertsView/?fetchLSST=false&fetchPeriod=15&fetchStart=72&nAlerts=20&magMax=5#sky",
  });
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

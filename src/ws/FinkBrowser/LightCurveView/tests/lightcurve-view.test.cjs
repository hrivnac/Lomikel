const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

const root = path.resolve(__dirname, "..");

function loadScripts(files, additions = {}) {
  const context = vm.createContext({
    console,
    Math,
    Number,
    Object,
    Array,
    JSON,
    ...additions,
  });
  for (const file of files) {
    vm.runInContext(fs.readFileSync(path.join(root, file), "utf8"), context, {filename: file});
  }
  return context;
}

function sixBands(valuesByBand) {
  return Object.fromEntries(["Y", "z", "g", "i", "u", "r"].map(band => [band, valuesByBand(band)]));
}

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return {promise, resolve, reject};
}

test("normalization excludes missing samples and preserves a real zero magnitude", () => {
  const context = loadScripts(["consts.js", "utils.js"]);
  context.input = sixBands(() => ({
    times: [3, 1, 2, 4, 5],
    values: [13, null, 12, Number.NaN, 0],
  }));

  const normalized = vm.runInContext("normalizeLightcurve(input)", context);

  for (const band of ["Y", "z", "g", "i", "u", "r"]) {
    assert.deepEqual(Array.from(normalized[band].times), [2, 3, 5]);
    assert.deepEqual(Array.from(normalized[band].values), [12, 13, 0]);
  }
});

test("normalization sorts samples and averages duplicate timestamps deterministically", () => {
  const context = loadScripts(["consts.js", "utils.js"]);
  context.input = sixBands(() => ({
    times: [3, 1, 2, 1],
    values: [30, 12, 20, 10],
  }));

  const normalized = vm.runInContext("normalizeLightcurve(input)", context);

  for (const band of ["Y", "z", "g", "i", "u", "r"]) {
    assert.deepEqual(Array.from(normalized[band].times), [1, 2, 3]);
    assert.deepEqual(Array.from(normalized[band].values), [11, 20, 30]);
  }
});

test("projection uses the common observed domain and includes both endpoints", () => {
  const context = loadScripts(["consts.js", "utils.js"], {xTime: false});
  const starts = {Y: 61040.1426, z: 61050.5, g: 61060.25, i: 61089.2576, u: 61070.75, r: 61080.125};
  const ends = {Y: 61377.2585, z: 61300.5, g: 61250.25, i: 61200.75, u: 61115.1852, r: 61220.125};
  context.input = Object.fromEntries(Object.keys(starts).map(band => [band, {
    times: [starts[band], (starts[band] + ends[band]) / 2, ends[band]],
    values: [20, 19, 18],
  }]));
  context.coefficients = {
    x: Object.fromEntries(Object.keys(starts).map(band => [band, 1])),
    y: Object.fromEntries(Object.keys(starts).map(band => [band, 0])),
  };

  const result = vm.runInContext("projectXY(normalizeLightcurve(input), coefficients)", context);

  assert.equal(result.startJD, starts.i);
  assert.equal(result.endJD, ends.u);
  assert.equal(result.M.length, 201);
  assert.equal(result.M[0].t, 0);
  assert.equal(result.M.at(-1).t, result.endJD - result.startJD);
  assert.equal(Object.hasOwn(context, "mode"), false);
});

test("projection returns no trajectory when any LSST band is absent", () => {
  const context = loadScripts(["consts.js", "utils.js"], {xTime: false});
  context.input = sixBands(band => band === "u"
    ? {times: [], values: []}
    : {times: [1, 2], values: [20, 19]});
  context.coefficients = {
    x: Object.fromEntries(["Y", "z", "g", "i", "u", "r"].map(band => [band, 1])),
    y: Object.fromEntries(["Y", "z", "g", "i", "u", "r"].map(band => [band, 1])),
  };

  const result = vm.runInContext("projectXY(normalizeLightcurve(input), coefficients)", context);

  assert.deepEqual(Array.from(result.M), []);
  assert.deepEqual(Array.from(result.missingBands), ["u"]);
  assert.equal(result.startJD, null);
  assert.equal(result.endJD, null);
});

test("bundled light curves mark missing values distinctly from real magnitudes", () => {
  const context = loadScripts(["consts.js", "utils.js"], {xTime: false});
  context.input = JSON.parse(fs.readFileSync(path.join(root, "39702066.json"), "utf8"));
  context.coefficients = {
    x: Object.fromEntries(["Y", "z", "g", "i", "u", "r"].map(band => [band, 1])),
    y: Object.fromEntries(["Y", "z", "g", "i", "u", "r"].map(band => [band, 0])),
  };

  const result = vm.runInContext("projectXY(normalizeLightcurve(input), coefficients)", context);

  assert.equal(result.startJD, 61089.2576);
  assert.equal(result.endJD, 61115.1852);
});

test("Fink LSST sources become sorted six-band AB-magnitude light curves", () => {
  const context = loadScripts(["consts.js", "utils.js", "data.js"]);
  context.rows = [
    {"r:band": "y", "r:midpointMjdTai": 3, "r:psfFlux": 1000},
    {"r:band": "u", "r:midpointMjdTai": 2, "r:psfFlux": 100},
    {"r:band": "g", "r:midpointMjdTai": 1, "r:psfFlux": 10},
    {"r:band": "r", "r:midpointMjdTai": 1, "r:psfFlux": 0},
    {"r:band": "i", "r:midpointMjdTai": 1, "r:psfFlux": -5},
    {"r:band": "z", "r:midpointMjdTai": 1, "r:psfFlux": null},
  ];

  const curve = vm.runInContext("finkSourcesToLightcurve(rows)", context);

  assert.deepEqual(Array.from(curve.Y.times), [3]);
  assert.ok(Math.abs(curve.Y.values[0] - 23.9) < 1e-12);
  assert.deepEqual(Array.from(curve.u.times), [2]);
  assert.ok(Math.abs(curve.u.values[0] - 26.4) < 1e-12);
  assert.deepEqual(Array.from(curve.g.times), [1]);
  assert.ok(Math.abs(curve.g.values[0] - 28.9) < 1e-12);
  assert.deepEqual(Array.from(curve.r.times), []);
  assert.deepEqual(Array.from(curve.i.times), []);
  assert.deepEqual(Array.from(curve.z.times), []);
});

test("LSST object identifiers stay exact and reject malformed input", () => {
  const context = loadScripts(["consts.js", "utils.js", "data.js"]);
  context.values = [
    " 313761043604045880 ",
    "313761043604045880x",
    "1e17",
    "",
    313761043604045880,
    "1234567890123456789012345678901",
  ];

  const results = vm.runInContext("values.map(normalizeLsstObjectId)", context);

  assert.equal(results[0], "313761043604045880");
  assert.deepEqual(Array.from(results.slice(1)), [null, null, null, null, null]);
});

test("loading a Fink object posts an exact ID and installs its live light curve", async () => {
  const calls = [];
  const plotted = [];
  const status = {textContent: "", dataset: {}};
  const portalLink = {href: "", hidden: true};
  const rows = ["y", "z", "g", "i", "u", "r"].map((band, index) => ({
    "r:band": band,
    "r:midpointMjdTai": 61000 + index,
    "r:psfFlux": 1000 + index,
  }));
  const context = loadScripts(["consts.js", "utils.js", "data.js"], {
    activeSNID: null,
    document: {getElementById: id => id === "status" ? status : (id === "fink-portal-link" ? portalLink : null)},
    fetch: async (url, options) => {
      calls.push({url, options});
      return {ok: true, json: async () => rows};
    },
    plotLightCurves: curve => plotted.push(curve),
    resetRandom: () => {},
    updateSNIDHighlight: () => {},
  });

  const loaded = await vm.runInContext("loadFinkObject(' 313761043604045880 ')", context);

  assert.equal(loaded, true);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, "https://api.lsst.fink-portal.org/api/v1/sources");
  assert.equal(calls[0].options.method, "POST");
  assert.deepEqual(JSON.parse(calls[0].options.body), {
    diaObjectId: "313761043604045880",
    columns: "r:diaObjectId,r:midpointMjdTai,r:band,r:psfFlux,r:psfFluxErr",
    "output-format": "json",
  });
  assert.equal(context.activeSNID, "fink:313761043604045880");
  assert.equal(plotted.length, 1);
  assert.match(status.textContent, /6 sources/i);
  assert.equal(portalLink.href, "https://lsst.fink-portal.org/313761043604045880");
  assert.equal(portalLink.hidden, false);
});

test("a partial Fink light curve loads but reports bands missing from the projection", async () => {
  const status = {textContent: "", dataset: {}};
  const plotted = [];
  const rows = ["g", "r"].map((band, index) => ({
    "r:band": band,
    "r:midpointMjdTai": 61000 + index,
    "r:psfFlux": 1000,
  }));
  const context = loadScripts(["consts.js", "utils.js", "data.js"], {
    activeSNID: null,
    document: {getElementById: id => id === "status" ? status : null},
    fetch: async () => ({ok: true, json: async () => rows}),
    plotLightCurves: curve => plotted.push(curve),
    resetRandom: () => {}, updateSNIDHighlight: () => {},
  });

  const loaded = await vm.runInContext("loadFinkObject('313761043604045880')", context);

  assert.equal(loaded, true);
  assert.equal(plotted.length, 1);
  assert.equal(status.dataset.state, "warning");
  assert.match(status.textContent, /missing Y, z, i, u/i);
  assert.match(status.textContent, /projection/i);
});

test("a stalled Fink request is aborted and reports a timeout", async () => {
  const status = {textContent: "", dataset: {}};
  let aborted = false;
  class TestAbortController {
    constructor() { this.signal = {aborted: false}; }
    abort() { aborted = true; this.signal.aborted = true; }
  }
  const context = loadScripts(["consts.js", "utils.js", "data.js"], {
    activeSNID: null,
    AbortController: TestAbortController,
    clearTimeout: () => {},
    document: {getElementById: id => id === "status" ? status : null},
    fetch: async (_url, options) => {
      assert.equal(options.signal.aborted, true);
      const error = new Error("aborted");
      error.name = "AbortError";
      throw error;
    },
    setTimeout: callback => { callback(); return 1; },
    plotLightCurves: () => {}, resetRandom: () => {}, updateSNIDHighlight: () => {},
  });

  const loaded = await vm.runInContext("loadFinkObject('313761043604045880')", context);

  assert.equal(loaded, false);
  assert.equal(aborted, true);
  assert.equal(status.dataset.state, "error");
  assert.match(status.textContent, /timed out/i);
});

test("selecting a generated demo hides a stale Fink Portal link", () => {
  const portalLink = {href: "https://lsst.fink-portal.org/313761043604045880", hidden: false};
  const context = loadScripts(["consts.js", "utils.js", "data.js"], {
    activeSNID: null,
    document: {getElementById: id => id === "fink-portal-link" ? portalLink : null},
    resetRandom: () => {}, updateSNIDHighlight: () => {}, plotLightCurves: () => {},
  });

  vm.runInContext("loadDemo('random')", context);

  assert.equal(portalLink.href, "");
  assert.equal(portalLink.hidden, true);
});

test("the latest sample selection wins when fetches resolve out of order", async () => {
  const requests = new Map();
  const plotted = [];
  const portalLink = {href: "https://lsst.fink-portal.org/313761043604045880", hidden: false};
  const context = loadScripts(["consts.js", "utils.js", "data.js"], {
    activeSNID: null,
    bandColors: {},
    document: {getElementById: id => id === "fink-portal-link" ? portalLink : null},
    fetch: url => {
      const request = deferred();
      requests.set(url, request);
      return request.promise;
    },
    plotLightCurves: data => plotted.push(data),
    resetRandom: () => {},
    updateSNIDHighlight: () => {},
  });
  const makeCurve = value => sixBands(() => ({times: [1, 2], values: [null, value]}));

  vm.runInContext("loadSNID(111); loadSNID(222);", context);
  requests.get("222.json").resolve({ok: true, json: async () => makeCurve(22)});
  await new Promise(resolve => setImmediate(resolve));
  requests.get("111.json").resolve({ok: true, json: async () => makeCurve(11)});
  await new Promise(resolve => setImmediate(resolve));

  assert.equal(context.activeSNID, "222");
  assert.equal(vm.runInContext("lightcurve.Y.values[0]", context), 22);
  assert.equal(plotted.at(-1).Y.values[0], 22);
  assert.equal(portalLink.hidden, true);
});

test("the peak demo is a correlated astronomical brightness peak in all six bands", () => {
  const deterministicMath = Object.create(Math);
  deterministicMath.random = () => 0.5;
  const context = loadScripts(["consts.js", "utils.js", "data.js"], {
    Math: deterministicMath,
    plotLightCurves: () => {},
  });

  const curve = vm.runInContext("generateDemoData('peak')", context);

  for (const band of ["Y", "z", "g", "i", "u", "r"]) {
    const middle = curve[band].values[Math.floor(curve[band].values.length / 2)];
    const baseline = curve[band].values[0];
    assert.ok(middle < baseline, `${band}: expected ${middle} < ${baseline}`);
  }
});

test("light-curve rendering uses incremental Plotly updates and an inverted magnitude axis", () => {
  const calls = [];
  const context = loadScripts(["consts.js", "params.js", "update.js"], {
    Plotly: {
      newPlot: (...args) => calls.push({method: "newPlot", args}),
      react: (...args) => calls.push({method: "react", args}),
    },
    window: {},
  });
  context.input = sixBands(() => ({times: [10, 11], values: [20, 19]}));

  vm.runInContext("plotLightCurves(input)", context);

  assert.equal(calls.length, 1);
  assert.equal(calls[0].method, "react");
  assert.equal(calls[0].args[2].yaxis.autorange, "reversed");
});

test("projection rendering uses Plotly.react and labels elapsed time correctly", () => {
  const calls = [];
  const curve = sixBands(() => ({times: [10, 11], values: [20, 19]}));
  const coefficients = {
    x: Object.fromEntries(["Y", "z", "g", "i", "u", "r"].map(band => [band, 1])),
    y: Object.fromEntries(["Y", "z", "g", "i", "u", "r"].map(band => [band, 1])),
  };
  const context = loadScripts(["consts.js", "utils.js", "update.js"], {
    Plotly: {
      newPlot: (...args) => calls.push({method: "newPlot", args}),
      react: (...args) => calls.push({method: "react", args}),
    },
    activeX: [], activeY: [], coeffs: coefficients, demo: curve,
    lightcurve: null, window: {}, xTime: false,
  });

  vm.runInContext("updatePlot()", context);

  assert.equal(calls.length, 1);
  assert.equal(calls[0].method, "react");
  assert.equal(calls[0].args[1][0].marker.colorbar.title, "ΔMJD (days)");
});

test("projection explains when a six-band trajectory cannot be computed", () => {
  const calls = [];
  const context = loadScripts(["consts.js", "update.js"], {
    Plotly: {react: (...args) => calls.push(args)},
    activeX: [], activeY: [], coeffs: {x: {}, y: {}}, demo: {},
    lightcurve: null, window: {}, xTime: false,
    projectXY: () => ({L: [], M: [], R: [], startJD: null, endJD: null, missingBands: ["u", "Y"]}),
  });

  vm.runInContext("updatePlot()", context);

  const layout = calls[0][2];
  assert.match(layout.annotations[0].text, /u, Y/);
  assert.match(layout.annotations[0].text, /all six/i);
});

test("projection redraw requests are coalesced to one update per animation frame", () => {
  const frames = [];
  const calls = [];
  const curve = sixBands(() => ({times: [10, 11], values: [20, 19]}));
  const coefficients = {
    x: Object.fromEntries(["Y", "z", "g", "i", "u", "r"].map(band => [band, 1])),
    y: Object.fromEntries(["Y", "z", "g", "i", "u", "r"].map(band => [band, 1])),
  };
  const context = loadScripts(["consts.js", "utils.js", "update.js"], {
    Plotly: {react: (...args) => calls.push(args)},
    activeX: [], activeY: [], coeffs: coefficients, demo: curve,
    lightcurve: null, window: {}, xTime: false,
    requestAnimationFrame: callback => frames.push(callback),
  });

  vm.runInContext("schedulePlotUpdate(); schedulePlotUpdate(); schedulePlotUpdate();", context);
  assert.equal(frames.length, 1);
  assert.equal(calls.length, 0);
  frames[0]();
  assert.equal(calls.length, 1);
});

test("invalid saved presets are discarded without breaking initialization", () => {
  for (const stored of ["{broken", "null", "[]"]) {
    const removed = [];
    const context = loadScripts(["buttons.js"], {
      document: {},
      localStorage: {
        getItem: () => stored,
        removeItem: key => removed.push(key),
      },
      savedPresets: {},
    });

    assert.doesNotThrow(() => vm.runInContext("loadPresets()", context));
    assert.deepEqual(removed, ["savedPresets"]);
    assert.deepEqual(vm.runInContext("Object.keys(savedPresets)", context), []);
  }
});

test("a sample load failure is visible and does not replace the current curve", async () => {
  const status = {textContent: "", dataset: {}};
  let consoleErrors = 0;
  const context = loadScripts(["consts.js", "utils.js", "data.js"], {
    activeSNID: "current",
    console: {error: () => { consoleErrors += 1; }},
    document: {getElementById: id => id === "status" ? status : null},
    fetch: async () => ({ok: false, status: 404}),
    plotLightCurves: () => {}, resetRandom: () => {}, updateSNIDHighlight: () => {},
  });
  context.current = sixBands(() => ({times: [1], values: [20]}));
  vm.runInContext("lightcurve = current", context);

  await vm.runInContext("loadSNID(404)", context);

  assert.match(status.textContent, /failed/i);
  assert.equal(status.dataset.state, "error");
  assert.equal(context.activeSNID, "current");
  assert.equal(vm.runInContext("lightcurve.Y.values[0]", context), 20);
  assert.equal(consoleErrors, 0);
});

test("the page is mobile-ready and uses only local executable dependencies", () => {
  const html = fs.readFileSync(path.join(root, "index.html"), "utf8");

  assert.match(html, /<meta name="viewport" content="width=device-width, initial-scale=1">/);
  assert.match(html, /<script src="vendor\/plotly-gl2d-3\.1\.0\.min\.js"><\/script>/);
  assert.doesNotMatch(html, /<script[^>]+src="https?:\/\//);
  assert.doesNotMatch(html, /d3/i);
  assert.doesNotMatch(html, /<\/script>\s*\|/);
});

test("the page offers an exact LSST object-ID form and permits only the Fink API connection", () => {
  const html = fs.readFileSync(path.join(root, "index.html"), "utf8");

  assert.match(html, /connect-src 'self' https:\/\/api\.lsst\.fink-portal\.org/);
  assert.match(html, /<form id="fink-object-form"/);
  assert.match(html, /<input id="fink-object-id"[^>]+inputmode="numeric"[^>]+pattern="\[0-9\]\+"/);
  assert.match(html, /<a id="fink-portal-link"/);
  assert.match(html, /rel="noopener noreferrer"/);
});

test("coefficient controls do not require D3 and expose keyboard slider semantics", () => {
  const source = fs.readFileSync(path.join(root, "sliders.js"), "utf8");

  assert.doesNotMatch(source, /\bd3\b/);
  assert.match(source, /role: "slider"/);
  assert.match(source, /addEventListener\("keydown"/);
  assert.match(source, /aria-valuetext/);
});

test("coefficient reset refreshes the native slider handles", () => {
  const source = fs.readFileSync(path.join(root, "update.js"), "utf8");

  assert.match(source, /handles\.forEach\(updateCoefficientHandle\)/);
  assert.doesNotMatch(source, /handles\.attr\(/);
});

test("the control layout stacks on phones and keeps touch targets usable", () => {
  const css = fs.readFileSync(path.join(root, "style.css"), "utf8");

  assert.match(css, /@media \(max-width: 800px\)/);
  assert.match(css, /#application\s*\{[^}]*display:\s*flex/s);
  assert.match(css, /flex-direction:\s*column/);
  assert.match(css, /min-height:\s*44px/);
  assert.match(css, /#sliders svg\s*\{[^}]*width:\s*100%/s);
});

test("the Fink object loader remains usable in the narrow control column", () => {
  const css = fs.readFileSync(path.join(root, "style.css"), "utf8");

  assert.match(css, /\.object-loader\s*\{[^}]*display:\s*flex/s);
  assert.match(css, /#fink-object-id\s*\{[^}]*min-width:\s*0/s);
  assert.match(css, /#fink-object-id\s*\{[^}]*min-height:\s*44px/s);
});

test("startup initializes each chart once after the DOM is ready", () => {
  let ready;
  let resetCalls = 0;
  let lightCurveCalls = 0;
  const context = loadScripts(["app.js"], {
    window: {addEventListener: (name, callback) => { if (name === "DOMContentLoaded") ready = callback; }},
    document: {getElementById: () => ({addEventListener: () => {}})},
    generateDemoData: () => ({Y: {times: [1], values: [20]}}),
    resetRandom: () => { resetCalls += 1; }, resetZero: () => {}, resetRainbow: () => {},
    plotLightCurves: () => { lightCurveCalls += 1; },
    initSaveButton: () => {}, loadPresets: () => {}, initSliders: () => {}, createSNIDButtons: () => {},
  });

  assert.equal(resetCalls, 0);
  assert.equal(lightCurveCalls, 0);
  ready();
  assert.equal(resetCalls, 1);
  assert.equal(lightCurveCalls, 1);
});

test("submitting the Fink form loads the exact text identifier", async () => {
  let ready;
  let submit;
  let requested;
  const elements = {
    "fink-object-form": {addEventListener: (name, callback) => { if (name === "submit") submit = callback; }},
    "fink-object-id": {value: "313761043604045880"},
    resetRandom: {addEventListener: () => {}},
    resetZero: {addEventListener: () => {}},
    resetRainbow: {addEventListener: () => {}},
  };
  loadScripts(["app.js"], {
    window: {addEventListener: (name, callback) => { if (name === "DOMContentLoaded") ready = callback; }},
    document: {getElementById: id => elements[id]},
    generateDemoData: () => ({Y: {times: [1], values: [20]}}),
    loadFinkObject: async value => { requested = value; },
    resetRandom: () => {}, resetZero: () => {}, resetRainbow: () => {}, plotLightCurves: () => {},
    initSaveButton: () => {}, loadPresets: () => {}, initSliders: () => {}, createSNIDButtons: () => {},
  });

  ready();
  let prevented = false;
  await submit({preventDefault: () => { prevented = true; }});

  assert.equal(prevented, true);
  assert.equal(requested, "313761043604045880");
});

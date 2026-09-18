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

test("the latest sample selection wins when fetches resolve out of order", async () => {
  const requests = new Map();
  const plotted = [];
  const context = loadScripts(["consts.js", "utils.js", "data.js"], {
    activeSNID: null,
    bandColors: {},
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

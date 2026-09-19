const assert = require("node:assert/strict");
const childProcess = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

const root = path.resolve(__dirname, "..");

function loadScripts(files, additions = {}) {
  const context = vm.createContext({
    console, Math, Number, Object, Array, JSON, ...additions,
  });
  for (const file of files) {
    vm.runInContext(fs.readFileSync(path.join(root, file), "utf8"), context, {filename: file});
  }
  return context;
}

function sixBands(valuesByBand) {
  return Object.fromEntries(["Y", "z", "g", "i", "u", "r"].map(band => [band, valuesByBand(band)]));
}

function seededMath(seed) {
  let state = seed >>> 0;
  const math = Object.create(Math);
  math.random = () => {
    state = (1664525 * state + 1013904223) >>> 0;
    return state / 4294967296;
  };
  return math;
}

test("automatic analysis avoids Array.at for older supported browsers", () => {
  const source = fs.readFileSync(path.join(root, "analysis.js"), "utf8");
  assert.doesNotMatch(source, /\.at\(/);
});

test("automatic rendering uses only the PCA-selected bands and interval", () => {
  const context = loadScripts(["consts.js", "utils.js", "analysis.js"], {xTime: false});
  const commonTimes = [0, 2, 4, 6, 8, 10];
  context.input = {
    Y: {times: commonTimes, values: commonTimes.map(time => time)},
    z: {times: commonTimes, values: commonTimes.map(time => time * time)},
    g: {times: commonTimes, values: commonTimes.map(time => Math.sin(time))},
    r: {times: [20, 25, 30], values: [0, 1, 0]},
  };

  const result = vm.runInContext("analyzeTrajectory(input)", context);
  context.projection = result.projection;
  const rendered = vm.runInContext("projectXY(input, projection)", context);

  assert.equal(result.status, "ok");
  assert.deepEqual(Array.from(result.bands), ["Y", "z", "g"]);
  assert.equal(rendered.M.length, 201);
  assert.ok(rendered.M.every(point => Number.isFinite(point.x) && Number.isFinite(point.y)));
});

test("automatic analysis ranks subsets after removing locally constant bands", () => {
  const context = loadScripts(["consts.js", "utils.js", "analysis.js"]);
  context.input = {
    Y: {times: [0, 4, 6, 10], values: [0, 4, 6, 10]},
    z: {times: [0, 4, 6, 10], values: [0, 16, 36, 100]},
    g: {times: [0, 4, 6, 10], values: [1, 0, 0, 1]},
    i: {times: [0, 4, 6, 10], values: [2, 0, 0, 2]},
    u: {times: [0, 4, 6, 10], values: [0, 1, -1, 0]},
    r: {times: [4, 5, 6], values: [0, 1, 0]},
  };

  const result = vm.runInContext("analyzeTrajectory(input)", context);

  assert.equal(result.status, "ok");
  assert.deepEqual(Array.from(result.bands), ["Y", "z", "g", "i", "u"]);
  assert.deepEqual({...result.interval}, {start: 0, end: 10});
});

test("the demo calibration has an executable deterministic contract", () => {
  const script = path.join(root, "tests", "calibrate-analysis.cjs");
  const run = childProcess.spawnSync(process.execPath, [script, "--runs", "2"], {encoding: "utf8"});

  assert.equal(run.status, 0, run.stderr);
  const result = JSON.parse(run.stdout);
  assert.equal(result.method.prng, "LCG(1664525,1013904223,2^32)");
  assert.deepEqual(result.method.seeds, [1, 2]);
  assert.equal(result.method.percentiles, "floor((n - 1) * p) on sorted samples");
  assert.deepEqual(Object.keys(result.demos), ["random", "peak", "periodic"]);
});

test("automatic projection requires three informative bands", () => {
  const context = loadScripts(["consts.js", "utils.js", "analysis.js"]);
  context.input = {
    g: {times: [0, 1, 2, 3], values: [1, 2, 1, 0]},
    r: {times: [0, 1, 2, 3], values: [0, 1, 2, 1]},
  };

  const result = vm.runInContext("analyzeTrajectory(input)", context);

  assert.equal(result.status, "insufficientBands");
});

test("Jacobi PCA converges for a dense seeded periodic demo", () => {
  const context = loadScripts(["consts.js", "utils.js", "data.js", "analysis.js"], {
    Math: seededMath(1),
  });

  const result = vm.runInContext("analyzeTrajectory(generateDemoData('periodic'))", context);

  assert.equal(result.status, "ok");
  assert.equal(result.points.length, 201);
  assert.ok(result.points.every(point => Number.isFinite(point.x) && Number.isFinite(point.y)));
});

test("automatic projection applies coefficients and renders honest diagnostics", () => {
  const elements = Object.fromEntries([
    "analysis-results", "analysis-pattern", "analysis-bands", "analysis-variance-text",
    "analysis-variance", "analysis-line", "analysis-winding", "analysis-monotonicity",
    "analysis-closure", "analysis-radial", "analysis-roughness",
  ].map(id => [id, {id, hidden: true, textContent: "", value: 0}]));
  let updates = 0;
  let status = "";
  const context = loadScripts(["consts.js", "utils.js", "analysis.js"], {
    coeffs: {x: {}, y: {}},
    document: {getElementById: id => elements[id] || null},
    lightcurve: null,
    demo: null,
    setLoadStatus: message => { status = message; },
    update: () => { updates += 1; },
    xTime: true,
  });
  const times = Array.from({length: 41}, (_, index) => index / 40);
  const loadings = {
    Y: [1.0, 0.2], z: [0.7, 0.6], g: [0.2, 1.0],
    i: [-0.4, 0.8], u: [-0.9, 0.1], r: [-0.5, -0.7],
  };
  context.input = sixBands(band => ({
    times,
    values: times.map(time => 20
      + loadings[band][0] * Math.cos(2 * Math.PI * time)
      + loadings[band][1] * Math.sin(2 * Math.PI * time)),
  }));
  vm.runInContext("lightcurve = normalizeLightcurve(input)", context);

  const applied = vm.runInContext("applyAutomaticProjection()", context);

  assert.equal(applied, true);
  assert.equal(updates, 1);
  assert.equal(context.xTime, false);
  assert.ok(Number.isFinite(context.coeffs.offsetX));
  assert.ok(Number.isFinite(context.coeffs.offsetY));
  assert.deepEqual(Array.from(context.coeffs.bands), ["Y", "z", "g", "i", "u", "r"]);
  assert.deepEqual({...context.coeffs.interval}, {start: 0, end: 1});
  assert.equal(elements["analysis-results"].hidden, false);
  assert.equal(elements["analysis-pattern"].textContent, "Loop-like (demo-calibrated)");
  assert.match(elements["analysis-variance-text"].textContent, /%/);
  assert.ok(elements["analysis-variance"].value > 0.99);
  assert.match(status, /automatic PCA projection/i);
});

test("standardized PCA finds a deterministic two-dimensional periodic subspace", () => {
  const context = loadScripts(["consts.js", "utils.js", "analysis.js"]);
  const times = Array.from({length: 41}, (_, index) => index / 40);
  const loadings = {
    Y: [1.0, 0.2], z: [0.7, 0.6], g: [0.2, 1.0],
    i: [-0.4, 0.8], u: [-0.9, 0.1], r: [-0.5, -0.7],
  };
  context.input = sixBands(band => ({
    times,
    values: times.map(time => 20
      + loadings[band][0] * Math.cos(2 * Math.PI * time)
      + loadings[band][1] * Math.sin(2 * Math.PI * time)),
  }));

  const result = vm.runInContext("analyzeTrajectory(normalizeLightcurve(input))", context);

  assert.equal(result.status, "ok");
  assert.deepEqual(Array.from(result.bands), ["Y", "z", "g", "i", "u", "r"]);
  assert.equal(result.points.length, 201);
  assert.ok(result.scores.explainedVariance > 0.999999);
  assert.ok(result.scores.lineScore < 0.5);
  assert.ok(result.scores.winding > 0.9);
  assert.ok(result.scores.angularMonotonicity > 0.9);
  assert.equal(result.scores.pattern, "loop-like");
  for (const axis of ["x", "y"]) {
    for (const band of result.bands) {
      assert.ok(Number.isFinite(result.projection[axis][band]));
      assert.ok(Math.abs(result.projection[axis][band]) <= 2);
    }
  }
  const repeated = vm.runInContext("analyzeTrajectory(normalizeLightcurve(input))", context);
  assert.equal(JSON.stringify(repeated), JSON.stringify(result));
});

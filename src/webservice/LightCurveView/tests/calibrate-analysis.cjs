#!/usr/bin/env node

const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const root = path.resolve(__dirname, "..");
const scripts = ["consts.js", "utils.js", "data.js", "analysis.js"];
const demos = ["random", "peak", "periodic"];
const metrics = [
  "explainedVariance", "lineScore", "closure", "radialCv",
  "winding", "angularMonotonicity", "roughness",
];

function parseRuns(argv) {
  const index = argv.indexOf("--runs");
  if (index < 0) return 200;
  const value = Number(argv[index + 1]);
  if (!Number.isSafeInteger(value) || value < 1 || value > 10000) {
    throw new TypeError("--runs must be an integer from 1 to 10000");
  }
  return value;
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

function analyzeDemo(demo, seed) {
  const context = vm.createContext({
    Array, console, JSON, Math: seededMath(seed), Number, Object,
  });
  for (const file of scripts) {
    vm.runInContext(fs.readFileSync(path.join(root, file), "utf8"), context, {filename: file});
  }
  context.demoName = demo;
  return vm.runInContext("analyzeTrajectory(generateDemoData(demoName))", context);
}

function percentile(values, probability) {
  const sorted = values.slice().sort((left, right) => left - right);
  return sorted[Math.floor((sorted.length - 1) * probability)];
}

function rounded(value) {
  return Number(value.toFixed(4));
}

function summarize(rows) {
  const labels = {};
  for (const row of rows) labels[row.pattern] = (labels[row.pattern] || 0) + 1;
  const summary = {labels};
  for (const metric of metrics) {
    const values = rows.map(row => row[metric]);
    summary[metric] = [0.05, 0.5, 0.95].map(probability => rounded(percentile(values, probability)));
  }
  return summary;
}

function main() {
  const runs = parseRuns(process.argv.slice(2));
  const output = {
    method: {
      prng: "LCG(1664525,1013904223,2^32)",
      seeds: [1, runs],
      seedRange: "inclusive",
      percentiles: "floor((n - 1) * p) on sorted samples",
      percentileOrder: [0.05, 0.5, 0.95],
    },
    demos: {},
  };
  for (const demo of demos) {
    const rows = [];
    for (let seed = 1; seed <= runs; seed++) {
      const result = analyzeDemo(demo, seed);
      if (result.status !== "ok") throw new Error(`${demo} seed ${seed}: ${result.status}`);
      rows.push(result.scores);
    }
    output.demos[demo] = summarize(rows);
  }
  process.stdout.write(`${JSON.stringify(output, null, 2)}\n`);
}

main();

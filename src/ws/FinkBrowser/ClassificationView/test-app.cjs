"use strict";
// Run with: node --test src/ws/FinkBrowser/ClassificationView/test-app.cjs
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");
const dir = __dirname;
const html = fs.readFileSync(path.join(dir, "index.html"), "utf8");

test("classifier choices are a local script, with no JSP request or duplicate HTML options", async () => {
  assert.match(html, /<script src="classifiers\.js"><\/script>[\s\S]*<script src="app\.js"><\/script>/);
  const catalog = fs.readFileSync(path.join(dir, "classifiers.js"), "utf8");
  assert.match(catalog, /FEATURES=2025\/13-50/);
  assert.match(catalog, /FEATURES=2024\/13-60/);
  assert.doesNotMatch(html, /<option value="(?:FINK|XMATCH|TAG|FEATURES=|LIGHTCURVES=)/);
  const h = harness();
  h.el("objectId").value = "170028526873870371";
  h.el("objectId").fire("input");
  await settle();
  assert.deepEqual(h.requests, []);
  assert.deepEqual(h.el("classifier").options.map((option) => option.value), ["FINK", "TAG"]);
});

function harness({ query = "", graph = async (id) => ({ objectId: id, objects: {}, objectClassification: {} }) } = {}) {
  const elements = new Map();
  const requests = [];
  const renders = [];
  function element(id) {
    const handlers = {};
    const node = { value: "", textContent: "", dataset: {}, disabled: false, options: [], hidden: true,
      addEventListener(type, fn) { handlers[type] = fn; },
      fire(type) { handlers[type]?.({ target: node }); },
      replaceChildren(...children) { node.options = children; node.value = children[0]?.value || ""; },
    };
    elements.set(id, node);
    return node;
  }
  for (const id of ["survey", "objectId", "classifier", "reclassifier", "metric", "nmaxValue", "status", "viz", "objectList", "resetBtn"]) element(id);
  elements.get("survey").value = "LSST";
  const document = { getElementById: (id) => elements.get(id), createElement: () => ({}) };
  const context = vm.createContext({ document, window: { location: { search: query } }, URLSearchParams, Object,
    AbortController, console, hideTooltip() {}, showSpinner() {},
    validateNeighborhoodData: (data) => data,
    parseNeighborhoodLimit: (value) => Number(value),
    showObjectNeighborhood: async (data) => { renders.push(data); return {}; },
    updateDetailsPanel() {},
    fetch: async (url) => { requests.push(url); throw new Error("Unexpected catalog request"); },
    LomikelGraph: { objectNeighborhood2JSON: graph },
  });
  for (const file of ["classifiers.js", "data.js", "app.js"]) vm.runInContext(fs.readFileSync(path.join(dir, file), "utf8"), context, { filename: file });
  return { el: (id) => elements.get(id), requests, renders, run: (code) => vm.runInContext(code, context) };
}
const settle = () => new Promise((resolve) => setImmediate(resolve));

test("startup defaults come only from app.js and do not start slow graph work", async () => {
  for (const id of ["objectId", "survey", "classifier", "metric", "nmaxValue"]) {
    const tag = html.match(new RegExp(`<(?:(?:input)|(?:select))[^>]*id="${id}"[^>]*>`))?.[0] || "";
    assert.doesNotMatch(tag, /\bvalue=|\bselected\b/, id);
  }
  let calls = 0;
  const h = harness({ graph: async () => { calls++; } });
  await settle();
  assert.equal(h.el("objectId").value, "ZTF17aackceb");
  assert.equal(h.el("survey").value, "ZTF");
  assert.equal(h.el("nmaxValue").value, "20");
  assert.equal(h.el("metric").value, "JensenShannon");
  assert.equal(calls, 0);
});

test("typed numeric ID changes survey and both classifier menus from the local catalog", () => {
  const h = harness();
  assert.ok(h.el("classifier").options.some((option) => option.value === "FEATURES=2025/13-50"));
  assert.ok(h.el("classifier").options.some((option) => option.value === "LIGHTCURVES=Latent"));
  h.el("objectId").value = "170028526873870371";
  h.el("objectId").fire("input");
  assert.equal(h.el("survey").value, "LSST");
  assert.deepEqual(h.el("classifier").options.map((option) => option.value), ["FINK", "TAG"]);
  assert.deepEqual(h.el("reclassifier").options.map((option) => option.value), ["none", "FINK", "TAG"]);
});

test("URL object ID overrides conflicting survey and navigation infers survey", async () => {
  const calls = [];
  const h = harness({ query: "?survey=ZTF&objectId=170028526873870371", graph: async (id, classifier, options) => {
    calls.push({ id, classifier, options });
    return { objectId: id, objects: {} };
  } });
  assert.equal(h.el("survey").value, "LSST");
  await h.run("loadNeighborhood('ZTF17aackceb')");
  assert.equal(h.el("survey").value, "ZTF");
  assert.equal(h.el("objectId").value, "ZTF17aackceb");
  assert.equal(calls[0].options.nmax, 20);
  assert.equal(calls[0].options.metric, "JensenShannon");
  assert.equal(calls[0].options.graphUrl, "http://157.136.253.253:24444");
});

test("late graph responses cannot overwrite the latest survey", async () => {
  const pending = new Map();
  const h = harness({
    graph: (id) => new Promise((resolve) => pending.set(id, resolve)),
  });
  const old = h.run("loadNeighborhood()");
  h.el("objectId").value = "170028526873870371";
  h.el("objectId").fire("input");
  const latest = h.run("loadNeighborhood()");
  await settle();
  pending.get("170028526873870371")({ objectId: "170028526873870371", objects: {} });
  await latest;
  pending.get("ZTF17aackceb")({ objectId: "ZTF17aackceb", objects: {} });
  await old;
  await settle();
  assert.deepEqual(h.renders.map((data) => data.objectId), ["170028526873870371"]);
  assert.deepEqual(h.el("classifier").options.map((option) => option.value), ["FINK", "TAG"]);
});

test("invalid ID is rejected before graph access", async () => {
  let calls = 0;
  const h = harness({ graph: async () => { calls++; } });
  h.el("objectId").value = "not-an-id";
  await h.run("loadNeighborhood()");
  assert.equal(calls, 0);
  assert.equal(h.el("status").dataset.state, "error");
});

test("overlap cache separates surveys and graph endpoints", async () => {
  const source = fs.readFileSync(path.join(dir, "overlaps.js"), "utf8");
  const calls = [];
  const ctx = vm.createContext({ Map, GRAPH_ENDPOINTS: { ZTF: { graphUrl: "ztf" }, LSST: { graphUrl: "lsst" } },
    LomikelGraph: { overlaps2JSON: async (classifier, options) => { calls.push(options.graphUrl); return []; } }, module: { exports: {} } });
  vm.runInContext(source, ctx);
  await vm.runInContext('Promise.all([loadOverlaps("ZTF", "FINK", GRAPH_ENDPOINTS.ZTF), loadOverlaps("ZTF", "FINK", GRAPH_ENDPOINTS.ZTF), loadOverlaps("LSST", "FINK", GRAPH_ENDPOINTS.LSST)])', ctx);
  assert.deepEqual(calls, ["ztf", "lsst"]);
});

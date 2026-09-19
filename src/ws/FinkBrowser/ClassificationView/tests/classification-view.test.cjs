"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.resolve(__dirname, "..");
const read = (name) => fs.readFileSync(path.join(root, name), "utf8");

test("standalone page has no JSP, tracker, or remote D3 dependencies", () => {
  const html = read("index.html");
  assert.doesNotMatch(html, /Neighborhood\.jsp|Overlaps\.jsp|record\.pl|https:\/\/d3js\.org/);
  assert.match(html, /fink_graph\.js/);
  assert.match(html, /Content-Security-Policy/);
});

test("application loads LomikelGraph before graph consumers", () => {
  const html = read("index.html");
  const graph = html.indexOf('src="fink_graph.js"');
  const data = html.indexOf('src="data.js"');
  const overlaps = html.indexOf('src="overlaps.js"');
  assert.ok(graph >= 0 && graph < data && graph < overlaps);
});

test("ClassificationView calls reusable graph APIs with all query controls", () => {
  const data = read("data.js");
  const overlaps = read("overlaps.js");
  assert.match(data, /LomikelGraph\.objectNeighborhood2JSON/);
  assert.match(data, /objectId/);
  assert.match(data, /classifier/);
  assert.match(data, /reclassifier/);
  assert.match(data, /nmax/);
  assert.match(data, /metric/);
  assert.match(data, /graphUrl/);
  assert.match(overlaps, /LomikelGraph\.overlaps2JSON/);
  assert.match(overlaps, /survey/);
  assert.match(overlaps, /classifier/);
});

test("application renders errors instead of fabricating demo science", () => {
  const all = ["data.js", "overlaps.js", "drawing.js", "list.js"]
    .map(read).join("\n");
  assert.doesNotMatch(all, /demo data|using demo|fake/i);
  assert.doesNotMatch(all, /innerHTML|\.html\(/);
});

test("graph helper keeps exact request body and slash classifiers browser-safe", () => {
  const graph = fs.readFileSync(
    path.resolve(root, "../../../js/FinkTasks/fink_graph.js"),
    "utf8",
  );
  assert.match(graph, /Content-Type.*text\/plain;charset=UTF-8/);
  assert.match(read("index.html"), /FEATURES=2025\/13-50/);
});
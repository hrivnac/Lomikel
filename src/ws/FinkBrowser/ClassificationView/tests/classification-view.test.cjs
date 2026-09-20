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

test("form reads the nmax input value rather than empty element text", () => {
  const data = read("data.js");
  assert.match(data, /parseNeighborhoodLimit\(document\.getElementById\("nmaxValue"\)\.value\)/);
  assert.doesNotMatch(data, /getElementById\("nmaxValue"\)\.textContent/);
});

test("slow graph work is explicit, cancellable, and not started on page load", () => {
  assert.doesNotMatch(read("app.js"), /^\s*loadNeighborhood\(\)/m);
  assert.match(read("index.html"), /id="cancelBtn"/);
  assert.match(read("data.js"), /AbortController/);
});

test("cancellation also invalidates non-abortable layout work", () => {
  const data = read("data.js");
  assert.match(
    data,
    /function cancelNeighborhoodLoad[\s\S]*neighborhoodRequestSerial \+= 1;[\s\S]*showSpinner\(false\)/,
  );
});

test("help modal has a visible non-hidden state", () => {
  assert.match(read("style.css"), /\.modal:not\(\[hidden\]\)/);
});

test("controls submit by keyboard and expose cancellation", () => {
  const menu = read("menu.js");
  assert.match(menu, /controlsForm.*addEventListener\("submit"/);
  assert.match(menu, /cancelBtn.*cancelNeighborhoodLoad/);
});

test("panel dragging handles pointer cancellation", () => {
  assert.match(read("menu.js"), /pointercancel/);
});

test("map stars support keyboard recentering without rebuilding tooltip on move", () => {
  const drawing = read("drawing.js");
  assert.match(drawing, /\.on\("keydown"/);
  assert.match(drawing, /event\.key === "Enter"/);
  assert.match(drawing, /\.on\("keyup"/);
  assert.match(drawing, /event\.key === "Tab"/);
  assert.match(drawing, /\.on\("pointerenter", showTooltip\)/);
  assert.doesNotMatch(drawing, /\.on\("pointermove"/);
});

test("drawing uses the tested graph-distance-aware layout", () => {
  assert.match(read("drawing.js"), /computeObjectLayout\(/);
  assert.match(read("index.html"), /<script src="layout\.js"><\/script>/);
});

test("neighbor list provides an explicit recenter action", () => {
  const list = read("list.js");
  assert.match(list, /centerButton/);
  assert.match(list, /centerButton\.addEventListener\("click", \(\) => loadNeighborhood\(objectId\)\)/);
});

test("help dialog closes with Escape", () => {
  const help = read("help.js");
  assert.match(help, /event\.key === "Escape"/);
  assert.match(help, /closeHelp\(\)/);
});

test("strict CSP interactions avoid inline style mutation", () => {
  assert.doesNotMatch(read("drawing.js"), /\.style\./);
  assert.doesNotMatch(read("menu.js"), /\.style\./);
  assert.match(read("menu.js"), /\.animate\(/);
});

test("interactive SVG descendants are not flattened as an image", () => {
  const drawing = read("drawing.js");
  assert.match(drawing, /\.attr\("role", "group"\)/);
  assert.doesNotMatch(drawing, /\.attr\("role", "img"\)/);
});

test("help dialog contains focus and makes the background inert", () => {
  const help = read("help.js");
  assert.match(help, /querySelectorAll\(\s*"header, #workspace, #tooltip, #status, #loading-spinner"/);
  assert.match(help, /event\.key === "Tab"/);
  assert.match(help, /\.inert = true/);
  assert.match(help, /\.inert = false/);
});

test("touch tooltip supports outside-pointer and Escape dismissal", () => {
  const drawing = read("drawing.js");
  assert.match(drawing, /document\.addEventListener\("pointerdown"/);
  assert.match(drawing, /document\.addEventListener\("keydown"/);
  assert.match(drawing, /event\.key === "Escape"/);
  assert.match(drawing, /closest\("\.object-symbol, #tooltip"\)/);
  assert.doesNotMatch(drawing, /function populateTooltip[\s\S]*objectLink\(/);
  assert.match(read("index.html"), /id="tooltip" role="tooltip"/);
});

test("details summarize classifications without one DOM row per weight", () => {
  const list = read("list.js");
  assert.match(list, /class-weight-summary/);
  assert.match(list, /\.join\(" · "\)/);
  assert.doesNotMatch(list, /forEach\(\(\{ name, weight \}\)/);
});

test("visible neighbor-limit help distinguishes count one from cutoffs", () => {
  const html = read("index.html");
  assert.match(html, /0 &lt; cutoff &lt; 1/);
  assert.doesNotMatch(html, /0–1: relative cutoff/);
});

test("the class boundary is a circle, not unsupported overlap edges", () => {
  assert.match(read("drawing.js"), /append\("circle"\)[\s\S]*attr\("class", "link-line class-ring"\)/);
});
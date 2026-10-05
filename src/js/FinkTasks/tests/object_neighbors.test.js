"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");
const { objectNeighbors } = require("../object_neighbors.js");
const map = (...pairs) => ({ "@type": "g:Map", "@value": pairs });
const scalar = (value) => ({ "@type": "g:Double", "@value": value });
const graph = (entries, status = 200) => ({
  status: { code: { "@value": status } },
  result: { data: { "@type": "g:List", "@value": entries.map(([id, distance, classes = {}]) =>
    map(map(id, scalar(distance)), map(...Object.entries(classes).flatMap(([k, v]) => [k, scalar(v)])))) } },
});
const reply = (value, status = 200) => ({ ok: status === 200, status, redirected: false, json: async () => value, text: async () => JSON.stringify(value) });
const positions = (ids, locations = {}) => ids.map(id => ({ "r:diaObjectId": id, "r:ra": (locations[id] || [0, 0])[0], "r:dec": (locations[id] || [0, 0])[1] }));
const base = { graphUrl: "https://graph.example/", apiUrl: "https://api.example/", results: 0 };

test("parses GraphSON and returns Python-shaped JSON, preserving string IDs", async () => {
  const calls = [];
  const id = "170028486134595648";
  const fetchImpl = async (url, init) => {
    calls.push({ url, init, body: JSON.parse(init.body) });
    return reply(calls.length === 1 ? graph([[id, .25, { FINK: .75 }]]) : positions(["target", id], { [id]: [1, 0] }));
  };
  const result = await objectNeighbors("target", { ...base, restColumns: ["r:flux,r:flux"], fetchImpl });
  assert.equal(calls[0].body.gremlin, "gr.objectNeighborhood('target','FINK',0,'JensenShannon')");
  assert.deepEqual(calls[1].body, { diaObjectId: `target,${id}`, columns: "r:diaObjectId,r:ra,r:dec,r:flux", "output-format": "json" });
  assert.equal(calls[1].url, "https://api.example/api/v1/objects");
  assert.ok(Math.abs(result.results[0].angular_distance_arcsec - 3600) < 1e-6);
  assert.ok(Math.abs(result.results[0].angular_distance_arcmin - 60) < 1e-8);
  assert.deepEqual({ ...result, results: result.results.map(({ angular_distance_arcsec, angular_distance_arcmin, ...row }) => row) }, { object_id: "target", classifier: "FINK", distance_measure: "JensenShannon", results_parameter: 0, target: { ra_deg: 0, dec_deg: 0, rest: { "r:flux": null } }, rest_columns: ["r:flux"], graph_candidates_considered: 1, returned: 1, results: [{ object_id: id, janusgraph_distance: .25, ra_deg: 1, dec_deg: 0, classification: { FINK: .75 }, rest: { "r:flux": null } }] });
  assert.equal(calls[0].init.redirect, "error");
  assert.equal(calls[1].init.redirect, "error");
});

test("count mode probes until boundary tie complete before angular ranking", async () => {
  const probes = [];
  const fetchImpl = async (url, init) => {
    const body = JSON.parse(init.body);
    if (body.gremlin) {
      probes.push(body.gremlin);
      return reply(graph(probes.length === 1 ? Array.from({ length: 512 }, (_, i) => [`id${i}`, .1]) : [["far", .1], ["near", .1], ["worse", .2]]));
    }
    return reply(positions(["target", "far", "near", "worse"], { far: [2, 0], near: [.01, 0], worse: [0, 0] }));
  };
  const result = await objectNeighbors("target", { ...base, results: 1, fetchImpl });
  assert.match(probes[0], /,512,'JensenShannon'\)$/);
  assert.match(probes[1], /,1024,'JensenShannon'\)$/);
  assert.equal(result.graph_candidates_considered, 3);
  assert.deepEqual(result.results.map(r => r.object_id), ["near"]);
});

test("cutoff passes through graph parameter, sorts missing positions last and retains rest", async () => {
  const fetchImpl = async (url, init) => JSON.parse(init.body).gremlin ? reply(graph([["missing", .1], ["b", .1], ["a", .2]])) : reply([
    { "r:diaObjectId": "target", "r:ra": 359.9, "r:dec": 0, "r:x": 9 },
    { "r:diaObjectId": "b", "r:ra": .1, "r:dec": 0, "r:x": { thing: 2 } },
  ]);
  const result = await objectNeighbors("target", { ...base, results: .2, restColumns: ["r:x"], fetchImpl });
  assert.deepEqual(result.results.map(r => r.object_id), ["b", "missing", "a"]);
  assert.ok(Math.abs(result.results[0].angular_distance_arcsec - 720) < 1e-6);
  assert.equal(result.results[1].angular_distance_arcsec, null);
  assert.deepEqual(result.results[0].rest, { "r:x": { thing: 2 } });
  assert.deepEqual(result.results[2].rest, {});
});

test("REST requests deduplicate IDs and batch", async () => {
  const batches = [];
  const fetchImpl = async (url, init) => {
    const body = JSON.parse(init.body);
    if (body.gremlin) return reply(graph([["a", 0], ["a", .1], ["b", .2], ["c", .3]]));
    batches.push(body.diaObjectId);
    return reply(positions(body.diaObjectId.split(",")));
  };
  const output = await objectNeighbors("target", { ...base, batchSize: 2, fetchImpl });
  assert.deepEqual(batches, ["target,a", "b,c"]);
  assert.equal(output.graph_candidates_considered, 4);
});

test("validates input before network including transport and numeric domains", async () => {
  const fetchImpl = () => { throw Error("should not fetch"); };
  for (const [id, opts, pattern] of [
    ["x');g.V()", base, /object ID/], ["target", { ...base, results: 1.2 }, /whole number/],
    ["target", { ...base, results: -1 }, /non-negative/], ["target", { ...base, results: NaN }, /finite/],
    ["target", { ...base, distance: "Other" }, /distance/], ["target", { ...base, classifier: "x'" }, /classifier/],
    ["target", { ...base, restColumns: ["r:x,evil column"] }, /REST column/],
    ["target", { ...base, graphUrl: "http://remote.test" }, /allowInsecureGraph/],
    ["target", { ...base, apiUrl: "http://remote.test" }, /plaintext/],
    ["target", { ...base, timeoutMs: 0 }, /timeoutMs/], ["target", { ...base, batchSize: 0 }, /batchSize/],
  ]) await assert.rejects(objectNeighbors(id, { ...opts, fetchImpl }), pattern);
});

test("rejects query and fragment in service URLs before network traffic", async () => {
  let calls = 0;
  const fetchImpl = () => { calls++; throw Error("should not fetch"); };
  for (const service of ["graphUrl", "apiUrl"]) {
    for (const suffix of ["?token=abc", "#section", "?", "#"]) {
      await assert.rejects(objectNeighbors("target", { ...base, [service]: `https://service.example/path${suffix}`, fetchImpl }), /query|fragment|search|hash/i);
    }
  }
  assert.equal(calls, 0);
});

test("GraphSON status and malformed numeric data fail, including REST coordinates", async () => {
  for (const [payload, pattern] of [[graph([], 500), /Gremlin status 500/], [graph([["a", "NaN"]]), /graph distance/], [graph([["a", .1, { x: 2 }]]), /classification weight/]]) {
    await assert.rejects(objectNeighbors("target", { ...base, fetchImpl: async () => reply(payload) }), pattern);
  }
  await assert.rejects(objectNeighbors("target", { ...base, fetchImpl: async (url, init) => reply(JSON.parse(init.body).gremlin ? graph([["a", 0]]) : [{ "r:diaObjectId": "target", "r:ra": "NaN", "r:dec": 0 }]) }), /RA/);
  await assert.rejects(objectNeighbors("target", { ...base, fetchImpl: async (url, init) => reply(JSON.parse(init.body).gremlin ? graph([["a", 0]]) : positions(["a"])) }), /no position/);
});

test("blocks redirects at both production call sites", async () => {
  for (const target of ["graph", "api"]) {
    const calls = [];
    const fetchImpl = async (url, init) => {
      calls.push(init.redirect);
      if (target === "graph" || calls.length === 2) return { ...reply({}), redirected: true };
      return reply(graph([["a", 0]]));
    };
    await assert.rejects(objectNeighbors("target", { ...base, fetchImpl }), /redirect/i);
    assert.deepEqual(calls, target === "graph" ? ["error"] : ["error", "error"]);
  }
});

test("times out even when fetch ignores abort", async () => {
  await assert.rejects(objectNeighbors("target", { ...base, timeoutMs: 5, fetchImpl: () => new Promise(() => {}) }), /timed out/);
});

test("rejects lossy REST IDs and empty graph neighborhoods", async () => {
  await assert.rejects(objectNeighbors("target", { ...base, fetchImpl: async () => reply(graph([])) }), /no FINK neighborhood/);
  await assert.rejects(objectNeighbors("target", { ...base, fetchImpl: async (url, init) => {
    if (JSON.parse(init.body).gremlin) return reply(graph([["a", 0]]));
    return { ok: true, status: 200, redirected: false, json: async () => [{ "r:diaObjectId": 170028486134595648, "r:ra": 0, "r:dec": 0 }] };
  } }), /unsafe numeric object ID/);
});

test("preserves unquoted large Fink REST IDs without rounding", async () => {
  const exact = "170028486134595649";
  const fetchImpl = async (url, init) => JSON.parse(init.body).gremlin
    ? reply(graph([[exact, 0]]))
    : { ok: true, status: 200, redirected: false, text: async () => '[{"r:diaObjectId":170028486134595648,"r:ra":0,"r:dec":0},{"r:diaObjectId":170028486134595649,"r:ra":1,"r:dec":0}]' };
  const result = await objectNeighbors("170028486134595648", { ...base, fetchImpl });
  assert.equal(result.results[0].object_id, exact);
  assert.equal(result.target.ra_deg, 0);
});

test("timeout covers stalled JSON body and plaintext graph opt-in works", async () => {
  let graphCalls = 0;
  const fetchImpl = async (url, init) => {
    graphCalls++;
    return { ok: true, status: 200, redirected: false, json: () => new Promise(() => {}) };
  };
  await assert.rejects(objectNeighbors("target", { ...base, graphUrl: "http://graph.example/", allowInsecureGraph: true, timeoutMs: 5, fetchImpl }), /timed out/);
  assert.equal(graphCalls, 1);
});

test("browser script exports global without CommonJS or dependencies", () => {
  const context = { globalThis: {}, URL, AbortController, setTimeout, clearTimeout };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, "../object_neighbors.js"), "utf8"), context);
  assert.equal(typeof context.globalThis.objectNeighbors, "function");
});

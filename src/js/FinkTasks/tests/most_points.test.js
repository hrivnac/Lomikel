"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");
const http = require("node:http");
const { mostPoints } = require("../most_points.js");

function fixture(routes, calls = []) {
  return async (url, init) => {
    const route = new URL(url).pathname;
    const body = JSON.parse(init.body);
    calls.push({ route, body, init });
    if (!Object.hasOwn(routes, route)) throw new Error(`unexpected request ${route}`);
    return { ok: true, status: 200, json: async () => routes[route] };
  };
}
const hit = (_id, mjd) => ({ _id, sort: [Array.isArray(mjd) ? mjd.length : 1], _source: { mjd } });
const docs = (...ids) => ({ docs: ids.map((_id) => ({ _id, found: true, _source: { location: [{}, {}] } })) });

test("ranks SS and DIA from script cardinalities and paired radec indices", async () => {
  const calls = [];
  const fetch = fixture({
    "/ss_mjd/_search": { hits: { hits: [hit("90071992547409931234", [2, 1]), hit("b", 3)] } },
    "/ss_radec/_mget": docs("90071992547409931234", "b"),
    "/dia_mjd/_search": { hits: { hits: [hit("d", [4, 5])] } },
    "/dia_radec/_mget": docs("d"),
  }, calls);
  const result = await mostPoints({ esUrl: "https://es.example", results: 2, fetch });
  assert.equal(result.object_type, "both");
  assert.equal(result.rankings.ss.objects[0].object_id, "90071992547409931234");
  assert.deepEqual(result.rankings.ss.objects[0], {
    rank: 1, object_type: "ss", object_id: "90071992547409931234", point_count: 2,
    mjd_min: 1, mjd_max: 2, radec_point_count: 2,
  });
  assert.equal(result.rankings.dia.returned, 1);
  assert.equal(calls[0].body.sort[0]._script.script.source, "doc['mjd'].size()");
  assert.deepEqual(calls[1].body.ids, ["90071992547409931234", "b"]);
  assert(calls.every(({ init }) => init.redirect === "manual"));
});

test("optional all-band SS light curve uses exact resolver match and packed designation", async () => {
  const calls = [];
  const fetch = fixture({
    "/ss_mjd/_search": { hits: { hits: [hit("123", [10, 20])] } },
    "/ss_radec/_mget": docs("123"),
    "/api/v1/resolver": [
      { "r:ssObjectId": "999", "r:unpacked_primary_provisional_designation": "wrong" },
      { "r:ssObjectId": "123", "r:packed_primary_provisional_designation": "K01A36R", "r:unpacked_primary_provisional_designation": "2001 AR36" },
    ],
    "/api/v1/sso": [
      { "r:ssObjectId": "123", "r:diaSourceId": "2", "r:midpointMjdTai": 20, "r:band": "r" },
      { "r:ssObjectId": "123", "r:diaSourceId": "1", "r:midpointMjdTai": 10, "r:band": "g" },
    ],
  }, calls);
  const result = await mostPoints({ esUrl: "https://es.example", objectType: "ss", results: 1, lightcurves: true, fetch });
  assert.equal(calls[2].body.name_or_id, "123");
  assert.equal(calls[3].body.n_or_d, "K01A36R");
  assert.equal(calls[3].body.columns.includes("r:psfFluxErr"), true);
  assert.equal(result.lightcurves["123"].designation, "2001 AR36");
  assert.deepEqual(result.lightcurves["123"].bands, { g: 1, r: 1 });
  assert.deepEqual(result.lightcurves["123"].sources.map((row) => row["r:diaSourceId"]), ["1", "2"]);
});

test("preserves unquoted large REST resolver and source IDs", async () => {
  const exact = "21163611358705234";
  const sources = "170028486134595649";
  const fetch = async (url) => {
    const route = new URL(url).pathname;
    const raw = {
      "/ss_mjd/_search": '{"hits":{"hits":[{"_id":"21163611358705234","sort":[1],"_source":{"mjd":[61000]}}]}}',
      "/ss_radec/_mget": '{"docs":[{"_id":"21163611358705234","found":true,"_source":{"location":{}}}]}',
      "/api/v1/resolver": '[{"r:ssObjectId":21163611358705234,"r:packed_primary_provisional_designation":"K01A36R","r:unpacked_primary_provisional_designation":"2001 AR36"}]',
      "/api/v1/sso": '[{"r:ssObjectId":21163611358705234,"r:diaSourceId":170028486134595649,"r:midpointMjdTai":61000,"r:band":"g"}]',
    }[route];
    assert.ok(raw, route);
    return { ok: true, status: 200, text: async () => raw };
  };
  const result = await mostPoints({ esUrl: "https://es.example", objectType: "ss", results: 1, lightcurves: true, fetch });
  assert.equal(result.lightcurves[exact].sources[0]["r:diaSourceId"], sources);
  assert.equal(result.lightcurves[exact].sources[0]["r:ssObjectId"], exact);
});

test("rejects malformed ranking, paired documents and mismatched SSO identity", async () => {
  const base = { esUrl: "https://es.example", objectType: "ss", fetch: null };
  await assert.rejects(mostPoints({ ...base, fetch: fixture({ "/ss_mjd/_search": { hits: { hits: [{ ...hit("a", [1]), sort: [2] }] } } }) }), /cardinality disagreement/);
  await assert.rejects(mostPoints({ ...base, fetch: fixture({ "/ss_mjd/_search": { hits: { hits: [hit("a", 1)] } }, "/ss_radec/_mget": { docs: [] } }) }), /omitted/);
  const routes = {
    "/ss_mjd/_search": { hits: { hits: [hit("123", 1)] } },
    "/ss_radec/_mget": docs("123"),
    "/api/v1/resolver": [{ "r:ssObjectId": "123", "r:unpacked_primary_provisional_designation": "X" }],
    "/api/v1/sso": [{ "r:ssObjectId": "other", "r:diaSourceId": "1", "r:midpointMjdTai": 1, "r:band": "g" }],
  };
  await assert.rejects(mostPoints({ ...base, lightcurves: true, fetch: fixture(routes) }), /another ssObjectId/);
});

test("validates options and URLs before network traffic", async () => {
  const fetch = () => { throw new Error("must not fetch"); };
  for (const args of [
    { results: 0 }, { results: 1.5 }, { timeoutMs: 0 }, { timeoutMs: Infinity },
    { objectType: "other" }, { objectType: "dia", lightcurves: true },
    { esUrl: "http://es.example" }, { esUrl: "https://user:pass@es.example" },
    { apiUrl: "http://api.example", lightcurves: true },
  ]) await assert.rejects(mostPoints({ ...args, fetch }), /results|timeout|objectType|lightcurves|plaintext|userinfo/);
  await assert.rejects(mostPoints({ esUrl: "http://es.example", allowInsecureEs: true, apiUrl: "http://api.example", lightcurves: true, fetch }), /plaintext/);
});

test("rejects nonempty and bare query/fragment delimiters in both service URLs", async () => {
  const fetch = () => { throw new Error("must not fetch"); };
  for (const suffix of ["?x=1", "#section", "?", "#"]) {
    await assert.rejects(mostPoints({ esUrl: `https://es.example/base${suffix}`, fetch }), /query|fragment/);
    await assert.rejects(mostPoints({ esUrl: "https://es.example", apiUrl: `https://api.example/base${suffix}`, lightcurves: true, fetch }), /query|fragment/);
  }
});

test("blocks redirects and HTTP errors from all requests", async () => {
  const base = { esUrl: "https://es.example", objectType: "ss" };
  await assert.rejects(mostPoints({ ...base, fetch: async () => ({ status: 302, ok: false, headers: { get: () => "http://evil.example" } }) }), /HTTP 302/);
  await assert.rejects(mostPoints({ ...base, fetch: async () => ({ status: 500, ok: false, text: async () => "failed" }) }), /HTTP 500/);
});

test("timeout remains active until JSON body completes", async () => {
  const fetch = async (_url, init) => ({ ok: true, status: 200, json: async () => new Promise((_resolve, reject) => {
    init.signal.addEventListener("abort", () => reject(init.signal.reason), { once: true });
  }) });
  await assert.rejects(mostPoints({ esUrl: "https://es.example", objectType: "ss", timeoutMs: 15, fetch }), /timeout|abort/i);
});

test("browser global works without module or dependencies", () => {
  const source = fs.readFileSync(path.resolve(__dirname, "../most_points.js"), "utf8");
  const context = vm.createContext({ URL, AbortController, setTimeout, clearTimeout });
  context.window = context;
  vm.runInContext(source, context);
  assert.match(fs.readFileSync(path.resolve(__dirname, "../README.md"), "utf8"), /`window\.LomikelMostPoints\.mostPoints\(options\)`/);
  assert.equal(typeof context.window.LomikelMostPoints.mostPoints, "function");
});

test("rejects lossy numeric IDs and malformed resolver/source data", async () => {
  const base = { esUrl: "https://es.example", objectType: "ss" };
  await assert.rejects(mostPoints({ ...base, fetch: fixture({
    "/ss_mjd/_search": { hits: { hits: [hit(9007199254740992, 1)] } },
  }) }), /safe integer/);
  const routes = {
    "/ss_mjd/_search": { hits: { hits: [hit("123", 1)] } },
    "/ss_radec/_mget": docs("123"),
    "/api/v1/resolver": [{ "r:ssObjectId": "123", "r:unpacked_primary_provisional_designation": "X" }],
    "/api/v1/sso": [
      { "r:ssObjectId": "123", "r:diaSourceId": "1", "r:midpointMjdTai": 1, "r:band": "g" },
      { "r:ssObjectId": "123", "r:diaSourceId": "1", "r:midpointMjdTai": 2, "r:band": "r" },
    ],
  };
  await assert.rejects(mostPoints({ ...base, lightcurves: true, fetch: fixture(routes) }), /duplicate diaSourceId/);
  routes["/api/v1/resolver"] = [routes["/api/v1/resolver"][0], routes["/api/v1/resolver"][0]];
  await assert.rejects(mostPoints({ ...base, lightcurves: true, fetch: fixture(routes) }), /2 exact matches/);
});

test("empty ranking makes no paired request and local HTTP needs no insecure opt-in", async () => {
  const calls = [];
  const result = await mostPoints({ esUrl: "http://127.0.0.1:9200", objectType: "dia", fetch: fixture({
    "/dia_mjd/_search": { hits: { hits: [] } },
  }, calls) });
  assert.equal(result.rankings.dia.returned, 0);
  assert.deepEqual(calls.map((call) => call.route), ["/dia_mjd/_search"]);
});

test("real fetch refuses redirects and times out while reading a stalled body", async () => {
  let redirects = 0;
  const server = http.createServer((request, response) => {
    if (request.url === "/redirected") {
      redirects++;
      response.end("not allowed");
    } else if (request.url === "/ss_mjd/_search") {
      response.writeHead(302, { Location: "/redirected" });
      response.end();
    } else if (request.url === "/dia_mjd/_search") {
      response.writeHead(200, { "Content-Type": "application/json" });
      response.write('{"hits":');
    }
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const esUrl = `http://127.0.0.1:${server.address().port}`;
  try {
    await assert.rejects(mostPoints({ esUrl, objectType: "ss" }), /HTTP 302/);
    assert.equal(redirects, 0);
    await assert.rejects(mostPoints({ esUrl, objectType: "dia", timeoutMs: 30 }), /timeout|abort/i);
  } finally {
    server.closeAllConnections();
    await new Promise((resolve) => server.close(resolve));
  }
});

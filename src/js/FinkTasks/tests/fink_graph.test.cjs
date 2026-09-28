"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

const {
  DEFAULT_GRAPH_URL,
  objectNeighborhood2JSON,
  overlaps2JSON,
} = require("../fink_graph.js");

function graphResponse(value, { code = 200, message = "" } = {}) {
  return {
    ok: true,
    status: 200,
    async json() {
      return {
        status: {
          code: { "@type": "g:Int32", "@value": code },
          message,
        },
        result: {
          data: {
            "@type": "g:List",
            "@value": [JSON.stringify(value)],
          },
        },
      };
    },
  };
}

function recordingFetch(response) {
  const calls = [];
  const fetchImpl = async (...args) => {
    calls.push(args);
    return response;
  };
  return { calls, fetchImpl };
}

test("can be included as a browser script exposing LomikelGraph", () => {
  const source = fs.readFileSync(path.join(__dirname, "..", "fink_graph.js"), "utf8");
  const context = {
    AbortController,
    URL,
    clearTimeout,
    setTimeout,
  };
  context.globalThis = context;

  vm.runInNewContext(source, context, { filename: "fink_graph.js" });

  assert.equal(typeof context.LomikelGraph.objectNeighborhood2JSON, "function");
  assert.equal(typeof context.LomikelGraph.overlaps2JSON, "function");
  assert.equal(Object.isFrozen(context.LomikelGraph), true);
});

test("objectNeighborhood2JSON calls the matching gr method and parses its JSON", async () => {
  const expected = {
    objectId: "170028486134595648",
    objects: {
      "170028486134595649": {
        distance: 0.125,
        classes: { SN: 0.8 },
      },
    },
    objectClassification: { SN: "0.9" },
  };
  const { calls, fetchImpl } = recordingFetch(graphResponse(expected));

  const result = await objectNeighborhood2JSON(
    "170028486134595648",
    "FINK=default",
    {
      reclassifier: "FINK=experimental",
      nmax: 12,
      metric: "Cosine",
      climit: 0.2,
      graphUrl: "https://graph.example.test/gremlin",
      fetchImpl,
      timeoutMs: 5000,
    },
  );

  assert.deepEqual(result, expected);
  assert.equal(calls.length, 1);
  assert.equal(calls[0][0], "https://graph.example.test/gremlin");
  assert.equal(calls[0][1].method, "POST");
  assert.equal(calls[0][1].redirect, "error");
  assert.equal(calls[0][1].headers["Content-Type"], "text/plain;charset=UTF-8");
  assert.deepEqual(JSON.parse(calls[0][1].body), {
    gremlin:
      "gr.objectNeighborhood2JSON('170028486134595648','FINK=default','FINK=experimental',12,'Cosine',0.2)",
  });
});

test("objectNeighborhood2JSON emits null for a missing reclassifier", async () => {
  const { calls, fetchImpl } = recordingFetch(
    graphResponse({ objectId: "target", objects: {}, objectClassification: {} }),
  );

  await objectNeighborhood2JSON("target", "FINK", {
    graphUrl: "https://graph.example.test",
    fetchImpl,
  });

  assert.deepEqual(JSON.parse(calls[0][1].body), {
    gremlin:
      "gr.objectNeighborhood2JSON('target','FINK',null,5,'JensenShannon',0)",
  });
});

test("overlaps2JSON calls gr.overlaps2JSON and parses the returned array", async () => {
  const expected = [
    {
      first: { lbl: "OCol", classifier: "FINK", flavor: "", class: "SN" },
      second: { lbl: "OCol", classifier: "FINK", flavor: "", class: "AGN" },
      overlap: 0.4,
    },
  ];
  const { calls, fetchImpl } = recordingFetch(graphResponse(expected));

  const result = await overlaps2JSON("FINK", {
    graphUrl: "https://graph.example.test",
    fetchImpl,
  });

  assert.deepEqual(result, expected);
  assert.deepEqual(JSON.parse(calls[0][1].body), {
    gremlin: "gr.overlaps2JSON('FINK')",
  });
});

test("overlaps2JSON supports null to request all classifiers", async () => {
  const { calls, fetchImpl } = recordingFetch(graphResponse([]));

  await overlaps2JSON(null, {
    graphUrl: "https://graph.example.test",
    fetchImpl,
  });

  assert.deepEqual(JSON.parse(calls[0][1].body), {
    gremlin: "gr.overlaps2JSON(null)",
  });
});

test("accepts repository-supported classifier flavors containing slashes", async () => {
  const { calls, fetchImpl } = recordingFetch(graphResponse([]));

  await overlaps2JSON("FEATURES=2025/13-50", {
    graphUrl: "https://graph.example.test",
    fetchImpl,
  });

  assert.deepEqual(JSON.parse(calls[0][1].body), {
    gremlin: "gr.overlaps2JSON('FEATURES=2025/13-50')",
  });
});

test("rejects unsafe values before issuing a request", async () => {
  let called = false;
  const fetchImpl = async () => {
    called = true;
  };

  await assert.rejects(
    objectNeighborhood2JSON("x');g.V().drop();//", "FINK", {
      graphUrl: "https://graph.example.test",
      fetchImpl,
    }),
    /object ID/,
  );
  await assert.rejects(
    overlaps2JSON("FINK');g.V().drop();//", {
      graphUrl: "https://graph.example.test",
      fetchImpl,
    }),
    /classifier/,
  );
  assert.equal(called, false);
});

test("validates neighborhood numeric and metric arguments", async () => {
  const options = {
    graphUrl: "https://graph.example.test",
    fetchImpl: async () => graphResponse({}),
  };

  await assert.rejects(
    objectNeighborhood2JSON("target", "FINK", { ...options, nmax: 1.5 }),
    /whole number/,
  );
  await assert.rejects(
    objectNeighborhood2JSON("target", "FINK", { ...options, climit: 1.1 }),
    /climit/,
  );
  await assert.rejects(
    objectNeighborhood2JSON("target", "FINK", { ...options, metric: "Random" }),
    /metric/,
  );
});

test("remote plaintext graph transport requires explicit opt-in", async () => {
  assert.equal(DEFAULT_GRAPH_URL, "http://134.158.243.144:24444");
  const fetchImpl = async () => graphResponse([]);

  await assert.rejects(
    overlaps2JSON("FINK", { fetchImpl }),
    /allowInsecureGraph/,
  );
  await assert.doesNotReject(
    overlaps2JSON("FINK", { fetchImpl, allowInsecureGraph: true }),
  );
  await assert.doesNotReject(
    overlaps2JSON("FINK", {
      graphUrl: "http://127.0.0.1:24444",
      fetchImpl,
    }),
  );
});

test("keeps timeout active while the response body is consumed", async () => {
  const slowResponse = graphResponse([]);
  slowResponse.json = async () => {
    await new Promise((resolve) => setTimeout(resolve, 60));
    return {
      status: { code: 200, message: "" },
      result: { data: { "@value": ["[]"] } },
    };
  };

  await assert.rejects(
    overlaps2JSON("FINK", {
      graphUrl: "https://graph.example.test",
      fetchImpl: async () => slowResponse,
      timeoutMs: 10,
    }),
    /timed out after 10 ms/,
  );
});

test("external AbortSignal cancels an in-flight graph request", async () => {
  const controller = new AbortController();
  const request = overlaps2JSON("FINK", {
    graphUrl: "https://graph.example.test",
    fetchImpl: async () => new Promise(() => {}),
    signal: controller.signal,
    timeoutMs: 50,
  });

  setTimeout(() => controller.abort(), 5);

  await assert.rejects(request, (error) => {
    assert.equal(error.name, "AbortError");
    return true;
  });
});

test("reports HTTP, Gremlin, and malformed payload failures", async (t) => {
  await t.test("HTTP failure", async () => {
    await assert.rejects(
      overlaps2JSON("FINK", {
        graphUrl: "https://graph.example.test",
        fetchImpl: async () => ({
          ok: false,
          status: 503,
          async text() {
            return "maintenance";
          },
        }),
      }),
      /HTTP 503.*maintenance/,
    );
  });

  await t.test("Gremlin failure", async () => {
    await assert.rejects(
      overlaps2JSON("FINK", {
        graphUrl: "https://graph.example.test",
        fetchImpl: async () => graphResponse([], { code: 500, message: "failed" }),
      }),
      /Gremlin status 500: failed/,
    );
  });

  await t.test("invalid method JSON", async () => {
    const response = graphResponse([]);
    response.json = async () => ({
      status: { code: 200, message: "" },
      result: { data: { "@value": ["not JSON"] } },
    });
    await assert.rejects(
      overlaps2JSON("FINK", {
        graphUrl: "https://graph.example.test",
        fetchImpl: async () => response,
      }),
      /invalid JSON returned by gr\.overlaps2JSON/,
    );
  });
});

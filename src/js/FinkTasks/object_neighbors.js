/* Standalone Fink object-neighbor ranking; browser and CommonJS. */
(function (root, factory) {
  "use strict";
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  else root.objectNeighbors = api.objectNeighbors;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";
  const DEFAULT_GRAPH_URL = "http://134.158.243.144:24445";
  const DEFAULT_API_URL = "https://api.lsst.fink-portal.org";
  const SUPPORTED_METRICS = ["JensenShannon", "Euclidean", "Cosine"];
  const own = (obj, key) => Object.prototype.hasOwnProperty.call(obj, key);
  const unwrap = value => {
    while (value && typeof value === "object" && !Array.isArray(value) && own(value, "@value")) value = value["@value"];
    return value;
  };
  function token(value, label, pattern = /^[A-Za-z0-9_.:=\-]+$/) {
    if (typeof value !== "string" || !pattern.test(value)) throw new TypeError(`${label} contains invalid characters`);
    return value;
  }
  function number(value, label, min, max) {
    if (value === null || typeof value === "boolean" || (typeof value !== "number" && typeof value !== "string") || (typeof value === "string" && !value.trim())) throw new TypeError(`${label} is not numeric`);
    const n = Number(value);
    if (!Number.isFinite(n)) throw new RangeError(`${label} must be finite`);
    if (n < min || n > max) throw new RangeError(`${label} must be between ${min} and ${max}`);
    return n;
  }
  function url(value, label, allowPlaintext) {
    let parsed;
    try { parsed = new URL(value); } catch (_) { throw new TypeError(`${label} must be an absolute HTTP(S) URL`); }
    if (!parsed.hostname || !["http:", "https:"].includes(parsed.protocol) || parsed.username || parsed.password) throw new TypeError(`${label} must be an absolute HTTP(S) URL without credentials`);
    // URL.search/hash normalize away a bare '?' or '#'; inspect the raw URL too.
    if (/[?#]/.test(value)) throw new TypeError(`${label} must not contain a query or fragment`);
    if (parsed.protocol === "http:" && !["localhost", "127.0.0.1", "[::1]"].includes(parsed.hostname) && !allowPlaintext) throw new Error(`${label} remote plaintext requires ${label === "graphUrl" ? "allowInsecureGraph: true" : "HTTPS"}`);
    return parsed.href;
  }
  function columns(groups) {
    if (!Array.isArray(groups) || groups.some(group => typeof group !== "string")) throw new TypeError("restColumns must be an array of strings");
    const result = [];
    for (const group of groups) for (const raw of group.split(",")) {
      const col = raw.trim();
      if (!col) continue;
      token(col, "REST column", /^[A-Za-z0-9_.:\-]+$/);
      if (!result.includes(col)) result.push(col);
    }
    return result;
  }
  function pairs(value) {
    const raw = unwrap(value);
    if (!Array.isArray(raw) || raw.length % 2) throw new TypeError("invalid GraphSON map");
    const result = [];
    for (let i = 0; i < raw.length; i += 2) result.push([raw[i], raw[i + 1]]);
    return result;
  }
  function identifier(value) {
    const raw = unwrap(value);
    // JSON numbers outside the safe range cannot faithfully represent LSST IDs.
    if (typeof raw === "number" && !Number.isSafeInteger(raw)) throw new RangeError("unsafe numeric object ID; request string IDs from the service");
    if (typeof raw !== "string" && typeof raw !== "number") throw new TypeError("invalid object ID");
    return String(raw);
  }
  function parseNeighborhood(payload) {
    if (!payload || typeof payload !== "object") throw new TypeError("invalid Gremlin response");
    const status = payload.status || {};
    const code = unwrap(status.code);
    if (code !== 200) throw new Error(`Gremlin status ${String(code)}: ${status.message || ""}`);
    const data = unwrap((payload.result || {}).data === undefined ? [] : payload.result.data);
    if (data === null) return [];
    if (!Array.isArray(data)) throw new TypeError("invalid GraphSON neighborhood list");
    return data.map(item => {
      const outer = pairs(item);
      if (outer.length !== 1) throw new TypeError("expected one neighborhood entry per GraphSON map");
      const entry = pairs(outer[0][0]);
      if (entry.length !== 1) throw new TypeError("invalid object-distance entry");
      const classes = {};
      for (const [name, weight] of pairs(outer[0][1])) {
        const key = String(unwrap(name));
        Object.defineProperty(classes, key, { value: number(unwrap(weight), "classification weight", 0, 1), enumerable: true, configurable: true, writable: true });
      }
      return { objectId: identifier(entry[0][0]), distance: number(unwrap(entry[0][1]), "graph distance", 0, 1), classes };
    });
  }
  function safeJson(value) {
    if (typeof value === "number" && !Number.isFinite(value)) return null;
    if (Array.isArray(value)) return value.map(safeJson);
    if (value && typeof value === "object") return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, safeJson(item)]));
    return value === undefined ? null : value;
  }
  function position(row) {
    if (!row || row["r:ra"] == null || row["r:dec"] == null) return null;
    return [number(row["r:ra"], "RA", 0, 360), number(row["r:dec"], "Dec", -90, 90)];
  }
  function angular(a, b) {
    const rad = Math.PI / 180;
    const ra1 = a[0] * rad, ra2 = b[0] * rad, dec1 = a[1] * rad, dec2 = b[1] * rad;
    const deltaRa = ((ra2 - ra1 + Math.PI) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI) - Math.PI;
    const h = Math.sin((dec2 - dec1) / 2) ** 2 + Math.cos(dec1) * Math.cos(dec2) * Math.sin(deltaRa / 2) ** 2;
    return 2 * Math.asin(Math.min(1, Math.sqrt(Math.max(0, h)))) / rad * 3600;
  }
  function parseLosslessIds(text) {
    // JSON.parse rounds 18-digit diaObjectId values. Quote only numeric ID
    // tokens at these exact property names before parsing; leave all other
    // numeric scientific measurements as numbers.
    const keys = new Set(["r:diaObjectId", "r:ssObjectId", "r:diaSourceId"]);
    const edits = [];
    for (let i = 0; i < text.length;) {
      if (text[i] !== '"') { i++; continue; }
      const start = i++;
      while (i < text.length) {
        if (text[i] === "\\") { i += 2; continue; }
        if (text[i++] === '"') break;
      }
      const key = JSON.parse(text.slice(start, i));
      if (!keys.has(key)) continue;
      let pos = i;
      while (/\s/.test(text[pos] || "")) pos++;
      if (text[pos++] !== ":") continue;
      while (/\s/.test(text[pos] || "")) pos++;
      const found = /^-?(?:0|[1-9]\d*)(?=\s*[,}\]])/.exec(text.slice(pos));
      if (found) edits.push([pos, pos + found[0].length, JSON.stringify(found[0])]);
    }
    for (let i = edits.length - 1; i >= 0; i--) {
      const [start, end, value] = edits[i];
      text = text.slice(0, start) + value + text.slice(end);
    }
    return JSON.parse(text);
  }
  async function post(endpoint, body, fetchImpl, timeoutMs, simpleRequest = false) {
    const controller = new AbortController();
    let timer;
    const timeoutError = new Error(`request to ${endpoint} timed out after ${timeoutMs} ms`);
    const timeout = new Promise((_, reject) => { timer = setTimeout(() => { controller.abort(); reject(timeoutError); }, timeoutMs); });
    const request = (async () => {
      // Gremlin's CORS port accepts JSON with text/plain but returns 405 for
      // OPTIONS; a browser-simple POST avoids the failing preflight.
      const contentType = simpleRequest ? "text/plain" : "application/json";
      const response = await fetchImpl(endpoint, { method: "POST", headers: { "Content-Type": contentType }, body: JSON.stringify(body), redirect: "error", signal: controller.signal });
      if (response && response.redirected) throw new Error(`redirect blocked from ${endpoint}`);
      if (!response || !response.ok) throw new Error(`HTTP ${response ? response.status : "unknown"} from ${endpoint}`);
      return typeof response.text === "function" ? parseLosslessIds(await response.text()) : response.json();
    })();
    try { return await Promise.race([request, timeout]); }
    finally { clearTimeout(timer); }
  }
  /** Rank graph neighbors by graph distance, angular separation, then exact ID.
   * Options: classifier, distance, results (integer count, fractional cutoff, or 0 for all),
   * restColumns (array of comma-separated groups), graphUrl, apiUrl, timeoutMs,
   * batchSize, allowInsecureGraph, fetchImpl. Default graph HTTP requires explicit opt-in.
   */
  async function objectNeighbors(objectId, options = {}) {
    if (!options || typeof options !== "object" || Array.isArray(options)) throw new TypeError("options must be an object");
    const oid = token(objectId, "object ID");
    const classifier = token(options.classifier === undefined ? "FINK" : options.classifier, "classifier");
    const metric = options.distance === undefined ? "JensenShannon" : options.distance;
    if (!SUPPORTED_METRICS.includes(metric)) throw new RangeError(`distance must be one of: ${SUPPORTED_METRICS.join(", ")}`);
    const results = options.results === undefined ? 10 : options.results;
    if (typeof results !== "number" || !Number.isFinite(results) || results < 0) throw new RangeError("results must be a finite, non-negative number");
    if (results >= 1 && !Number.isSafeInteger(results)) throw new RangeError("results >= 1 must be a whole number within the safe integer range");
    const restColumns = columns(options.restColumns === undefined ? [] : options.restColumns);
    const batchSize = options.batchSize === undefined ? 250 : options.batchSize;
    if (!Number.isSafeInteger(batchSize) || batchSize < 1) throw new RangeError("batchSize must be a positive safe integer");
    const timeoutMs = options.timeoutMs === undefined ? 180000 : options.timeoutMs;
    if (typeof timeoutMs !== "number" || !Number.isFinite(timeoutMs) || timeoutMs <= 0) throw new RangeError("timeoutMs must be finite and positive");
    const graphUrl = url(options.graphUrl === undefined ? DEFAULT_GRAPH_URL : options.graphUrl, "graphUrl", options.allowInsecureGraph === true);
    const apiUrl = url(options.apiUrl === undefined ? DEFAULT_API_URL : options.apiUrl, "apiUrl", false).replace(/\/+$/, "");
    const fetchImpl = options.fetchImpl === undefined ? (typeof globalThis !== "undefined" ? globalThis.fetch : undefined) : options.fetchImpl;
    if (typeof fetchImpl !== "function") throw new TypeError("fetch unavailable; pass fetchImpl");
    async function probe(size) {
      const gremlin = `gr.objectNeighborhood('${oid}','${classifier}',${size},'${metric}')`;
      return parseNeighborhood(await post(graphUrl, { gremlin }, fetchImpl, timeoutMs, true));
    }
    let neighbors;
    if (results >= 1) {
      let size = Math.max(512, results * 2);
      if (!Number.isSafeInteger(size)) throw new RangeError("probe size exceeds safe integer range");
      while (true) {
        neighbors = await probe(size);
        if (neighbors.length < size || neighbors.length < results) break;
        const distances = neighbors.map(n => n.distance).sort((a, b) => a - b);
        if (distances[distances.length - 1] > distances[results - 1]) break;
        if (size > Number.MAX_SAFE_INTEGER / 2) throw new RangeError("probe size exceeds safe integer range");
        size *= 2;
      }
    } else neighbors = await probe(results);
    if (!neighbors.length) throw new Error(`no ${classifier} neighborhood found for ${oid}`);
    const uniqueIds = [...new Set([oid, ...neighbors.map(n => n.objectId)])];
    const rows = new Map();
    const requestedColumns = [...new Set(["r:diaObjectId", "r:ra", "r:dec", ...restColumns])];
    for (let i = 0; i < uniqueIds.length; i += batchSize) {
      const response = await post(`${apiUrl}/api/v1/objects`, { diaObjectId: uniqueIds.slice(i, i + batchSize).join(","), columns: requestedColumns.join(","), "output-format": "json" }, fetchImpl, timeoutMs);
      if (!Array.isArray(response)) throw new TypeError("Fink objects API returned a non-list response");
      for (const row of response) {
        if (!row || typeof row !== "object" || Array.isArray(row)) throw new TypeError("Fink objects API row must be an object");
        if (!own(row, "r:diaObjectId")) throw new TypeError("Fink objects API row has no diaObjectId");
        const id = identifier(row["r:diaObjectId"]);
        if (row["r:ra"] != null) number(row["r:ra"], "RA", 0, 360);
        if (row["r:dec"] != null) number(row["r:dec"], "Dec", -90, 90);
        rows.set(id, row);
      }
    }
    const targetPosition = position(rows.get(oid));
    if (!targetPosition) throw new Error(`Fink REST API has no position for ${oid}`);
    const rest = row => row ? Object.fromEntries(restColumns.map(col => [col, safeJson(row[col])])) : {};
    const ranked = neighbors.map(neighbor => {
      const row = rows.get(neighbor.objectId);
      const pos = position(row);
      return { object_id: neighbor.objectId, janusgraph_distance: neighbor.distance,
        angular_distance_arcsec: pos ? angular(targetPosition, pos) : null,
        angular_distance_arcmin: pos ? angular(targetPosition, pos) / 60 : null,
        ra_deg: pos ? pos[0] : null, dec_deg: pos ? pos[1] : null,
        classification: neighbor.classes, rest: rest(row) };
    });
    ranked.sort((a, b) => a.janusgraph_distance - b.janusgraph_distance || (a.angular_distance_arcsec ?? Infinity) - (b.angular_distance_arcsec ?? Infinity) || (a.object_id < b.object_id ? -1 : a.object_id > b.object_id ? 1 : 0));
    const selected = results >= 1 ? ranked.slice(0, results) : ranked;
    return { object_id: oid, classifier, distance_measure: metric, results_parameter: results,
      target: { ra_deg: targetPosition[0], dec_deg: targetPosition[1], rest: rest(rows.get(oid)) },
      rest_columns: restColumns, graph_candidates_considered: neighbors.length,
      returned: selected.length, results: selected };
  }
  return { objectNeighbors };
});

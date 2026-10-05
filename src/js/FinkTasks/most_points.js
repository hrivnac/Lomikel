/* Standalone Fink LSST most-points service: include directly or require(). */
(function exposeMostPoints(root, factory) {
  "use strict";
  const api = Object.freeze(factory());
  if (typeof module === "object" && module.exports) module.exports = api;
  else root.LomikelMostPoints = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function createMostPoints() {
  "use strict";
  const DEFAULT_ES_URL = "http://134.158.243.139:24499";
  const DEFAULT_API_URL = "https://api.lsst.fink-portal.org";
  const LIGHTCURVE_COLUMNS = "r:diaSourceId,r:diaObjectId,r:ssObjectId,r:midpointMjdTai,r:band,r:scienceFlux,r:scienceFluxErr,r:psfFlux,r:psfFluxErr,r:ra,r:dec";
  const object = (value) => value !== null && typeof value === "object" && !Array.isArray(value);


  // JSON numbers beyond the safe integer range cannot preserve database identifiers.
  function id(value, label) {
    if (typeof value === "string" && value.length) return value;
    if (typeof value === "number" && Number.isSafeInteger(value)) return String(value);
    throw new TypeError(`${label} must be a nonempty string or safe integer (send large IDs as strings)`);
  }
  function finite(value, label) {
    if (typeof value !== "number" && (typeof value !== "string" || !value.trim())) throw new TypeError(`${label} must be finite`);
    const number = Number(value);
    if (!Number.isFinite(number)) throw new TypeError(`${label} must be finite`);
    return number;
  }
  function serviceUrl(value, label, allowRemoteHttp) {
    if (typeof value !== "string") throw new TypeError(`${label} must be an absolute HTTP(S) URL`);
    let url;
    try { url = new URL(value); } catch { throw new TypeError(`${label} must be an absolute HTTP(S) URL`); }
    if (url.username || url.password || value.match(/^https?:\/\/[^/]*@/i)) throw new TypeError(`${label} URL must not contain userinfo`);
    // URL.search/hash are empty for bare '?' and '#', which still misroute appended paths.
    if (!["http:", "https:"].includes(url.protocol) || !url.hostname || /[?#]/.test(value)) throw new TypeError(`${label} must be an absolute HTTP(S) URL without query or fragment`);
    if (url.protocol === "http:" && !["localhost", "127.0.0.1", "[::1]"].includes(url.hostname) && !allowRemoteHttp) {
      throw new Error(`remote plaintext ${label} URL requires ${label === "Elasticsearch" ? "allowInsecureEs" : "HTTPS"}`);
    }
    return url.href.replace(/\/$/, "");
  }
  function rankingQuery(results) {
    return { size: results, track_total_hits: true, _source: ["mjd"], query: { exists: { field: "mjd" } },
      sort: [{ _script: { type: "number", script: { lang: "painless", source: "doc['mjd'].size()" }, order: "desc" } }, { _id: { order: "asc" } }] };
  }
  function rankingRows(response, type) {
    if (!object(response) || !object(response.hits) || !Array.isArray(response.hits.hits)) throw new TypeError("Elasticsearch response has no hits list");
    return response.hits.hits.map((hit) => {
      if (!object(hit)) throw new TypeError("Elasticsearch hit must be an object");
      const objectId = id(hit._id, "Elasticsearch object ID");
      if (!object(hit._source)) throw new TypeError(`Elasticsearch hit has no source for ${objectId}`);
      const raw = hit._source.mjd;
      const values = Array.isArray(raw) ? raw : [raw];
      if (!values.length || values.some((v) => v === null || v === undefined)) throw new TypeError(`missing MJD values for ${objectId}`);
      const mjds = values.map((v) => finite(v, `MJD values for ${objectId}`));
      if (!Array.isArray(hit.sort) || !hit.sort.length) throw new TypeError(`missing script cardinality for ${objectId}`);
      const count = finite(hit.sort[0], `script cardinality for ${objectId}`);
      if (!Number.isSafeInteger(count) || count < 1) throw new TypeError(`invalid script cardinality for ${objectId}`);
      if (count !== mjds.length) throw new TypeError(`MJD cardinality disagreement for ${objectId}: sort=${count}, source=${mjds.length}`);
      let min = Infinity;
      let max = -Infinity;
      for (const mjd of mjds) { min = Math.min(min, mjd); max = Math.max(max, mjd); }
      return { object_type: type, object_id: objectId, point_count: count,
        mjd_min: min, mjd_max: max, radec_point_count: null };
    });
  }
  function radecCounts(response, ids) {
    if (!object(response) || !Array.isArray(response.docs)) throw new TypeError("Elasticsearch radec response has no docs list");
    const counts = new Map();
    for (const doc of response.docs) {
      if (!object(doc)) throw new TypeError("Elasticsearch radec row must be an object");
      const objectId = id(doc._id, "Elasticsearch radec object ID");
      if (!ids.includes(objectId) || counts.has(objectId)) throw new TypeError(`unexpected or duplicate paired radec document for ${objectId}`);
      if (!doc.found) throw new TypeError(`paired radec document not found for ${objectId}`);
      if (!object(doc._source) || doc._source.location === null || doc._source.location === undefined) throw new TypeError(`paired radec document has no location for ${objectId}`);
      counts.set(objectId, Array.isArray(doc._source.location) ? doc._source.location.length : 1);
    }
    for (const objectId of ids) if (!counts.has(objectId)) throw new TypeError(`paired radec response omitted: ${objectId}`);
    return counts;
  }
  function resolverMatch(rows, ssId) {
    if (!Array.isArray(rows)) throw new TypeError("resolver response must be a list");
    const matches = rows.filter((row) => {
      if (!object(row)) throw new TypeError("resolver response row must be an object");
      return id(row["r:ssObjectId"], "resolver ssObjectId") === ssId;
    });
    if (matches.length !== 1) throw new TypeError(`resolver returned ${matches.length} exact matches for ${ssId}`);
    const match = matches[0];
    if (typeof match["r:unpacked_primary_provisional_designation"] !== "string" || !match["r:unpacked_primary_provisional_designation"].trim()) throw new TypeError(`resolver match for ${ssId} has no designation`);
    return { packed: match["r:packed_primary_provisional_designation"] || "", unpacked: match["r:unpacked_primary_provisional_designation"] };
  }
  function normalizeSources(rows, ssId) {
    if (!Array.isArray(rows)) throw new TypeError("SSO response must be a list");
    const seen = new Set();
    const sources = rows.map((row) => {
      if (!object(row)) throw new TypeError("SSO response row must be an object");
      if (id(row["r:ssObjectId"], "SSO ssObjectId") !== ssId) throw new TypeError("SSO response contains another ssObjectId");
      const sourceId = id(row["r:diaSourceId"], "SSO diaSourceId");
      if (!sourceId.trim()) throw new TypeError("SSO diaSourceId must not be blank");
      if (seen.has(sourceId)) throw new TypeError(`duplicate diaSourceId ${sourceId}`);
      seen.add(sourceId);
      finite(row["r:midpointMjdTai"], "SSO response row finite MJD");
      if (row["r:band"] === null || row["r:band"] === undefined) throw new TypeError("SSO response row is missing band");
      return row;
    });
    sources.sort((a, b) => finite(a["r:midpointMjdTai"], "MJD") - finite(b["r:midpointMjdTai"], "MJD") ||
      (id(a["r:diaSourceId"], "diaSourceId") < id(b["r:diaSourceId"], "diaSourceId") ? -1 : 1));
    return sources;
  }
  function parseLosslessIds(text) {
    // Fink REST emits 18-digit IDs as JSON numbers; quote their exact lexical
    // values before JSON.parse so JavaScript never rounds identifier digits.
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
  async function postJson(url, payload, fetcher, timeoutMs) {
    const controller = new AbortController();
    let timer;
    const deadline = new Promise((_, reject) => {
      timer = setTimeout(() => {
        controller.abort();
        reject(new Error(`request timeout after ${timeoutMs} ms: ${url}`));
      }, timeoutMs);
    });
    try {
      return await Promise.race([deadline, (async () => {
        const response = await fetcher(url, { method: "POST", headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload), redirect: "manual", signal: controller.signal });
        // A redirect is never followed, even to another HTTPS endpoint.
        if (!response || !response.ok || response.status >= 300 && response.status < 400) {
          throw new Error(`HTTP ${response && response.status} from ${url}`);
        }
        return typeof response.text === "function" ? parseLosslessIds(await response.text()) : response.json();
      })()]);
    } finally { clearTimeout(timer); }
  }
  /**
   * Rank Fink SS/DIA objects. Options: objectType ('both'|'ss'|'dia'), results,
   * esUrl, apiUrl, lightcurves, allowInsecureEs, timeoutMs, fetch.
   * Returns {generated_at, object_type, results_per_type, ss_lightcurves,
   * rankings: {ss?, dia?}, lightcurves: { [exactSsId]: JSON document }}.
   * The default ES endpoint is remote HTTP and requires allowInsecureEs: true;
   * alternatively supply an HTTPS endpoint or local tunnel. No files/plots are made.
   */
  async function mostPoints(options = {}) {
    if (!object(options)) throw new TypeError("options must be an object");
    const { objectType = "both", results = 10, esUrl = DEFAULT_ES_URL, apiUrl = DEFAULT_API_URL,
      lightcurves = false, allowInsecureEs = false, timeoutMs = 180000,
      fetch: injectedFetch } = options;
    if (!["both", "ss", "dia"].includes(objectType)) throw new TypeError("objectType must be both, ss or dia");
    if (!Number.isSafeInteger(results) || results < 1) throw new RangeError("results must be a positive safe integer");
    if (typeof timeoutMs !== "number" || !Number.isFinite(timeoutMs) || timeoutMs <= 0) throw new RangeError("timeoutMs must be a finite positive number");
    if (typeof lightcurves !== "boolean" || typeof allowInsecureEs !== "boolean") throw new TypeError("lightcurves and allowInsecureEs must be booleans");
    if (lightcurves && objectType === "dia") throw new TypeError("lightcurves requires SS objects");
    const es = serviceUrl(esUrl, "Elasticsearch", allowInsecureEs);
    const api = lightcurves ? serviceUrl(apiUrl, "Fink REST", false) : null;
    const fetcher = injectedFetch === undefined ? globalThis.fetch : injectedFetch;
    if (typeof fetcher !== "function") throw new TypeError("fetch must be a function");
    const generatedAt = new Date().toISOString();
    const rankings = {};
    const lightcurveDocuments = Object.create(null);
    const types = objectType === "both" ? ["ss", "dia"] : [objectType];
    for (const type of types) {
      const response = await postJson(`${es}/${type}_mjd/_search`, rankingQuery(results), fetcher, timeoutMs);
      const rows = rankingRows(response, type);
      if (rows.length) {
        const ids = rows.map((row) => row.object_id);
        const paired = await postJson(`${es}/${type}_radec/_mget`, { ids }, fetcher, timeoutMs);
        const counts = radecCounts(paired, ids);
        for (const row of rows) row.radec_point_count = counts.get(row.object_id);
      }
      rankings[type] = { generated_at: generatedAt, source: `Elasticsearch ${type}_mjd`, object_type: type,
        returned: rows.length, objects: rows.map((row, index) => ({ rank: index + 1, ...row })) };
    }
    if (lightcurves) for (const row of rankings.ss.objects) {
      const ssId = row.object_id;
      const resolver = await postJson(`${api}/api/v1/resolver`, { resolver: "ssodnet", name_or_id: ssId,
        reverse: true, nmax: 10, "output-format": "json" }, fetcher, timeoutMs);
      const match = resolverMatch(resolver, ssId);
      const sources = normalizeSources(await postJson(`${api}/api/v1/sso`, { n_or_d: match.packed || match.unpacked,
        columns: LIGHTCURVE_COLUMNS, "output-format": "json" }, fetcher, timeoutMs), ssId);
      const bands = {};
      for (const source of sources) {
        const band = String(source["r:band"]);
        bands[band] = (bands[band] || 0) + 1;
      }
      lightcurveDocuments[ssId] = { object_type: "ss", ss_object_id: ssId, designation: match.unpacked,
        packed_designation: match.packed, elasticsearch_point_count: row.point_count,
        rest_source_count: sources.length, bands: Object.fromEntries(Object.entries(bands).sort(([a], [b]) => a.localeCompare(b))),
        sources, generated_at: generatedAt };
    }
    return { generated_at: generatedAt, object_type: objectType, results_per_type: results,
      ss_lightcurves: lightcurves, rankings, lightcurves: lightcurveDocuments };
  }
  return { mostPoints };
});

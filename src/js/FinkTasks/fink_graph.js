(function exposeFinkGraph(root, factory) {
  "use strict";

  const api = factory();
  if (typeof module === "object" && module.exports) {
    module.exports = api;
  } else {
    root.LomikelGraph = Object.freeze(api);
  }
})(typeof globalThis !== "undefined" ? globalThis : this, function createFinkGraph() {
  "use strict";

  const DEFAULT_GRAPH_URL = "http://134.158.243.144:24444";
  const DEFAULT_TIMEOUT_MS = 180000;
  const SUPPORTED_METRICS = Object.freeze([
    "JensenShannon",
    "Euclidean",
    "Cosine",
  ]);
  const SAFE_TOKEN = /^[A-Za-z0-9_.:=/\-]+$/;

  function validateSafeToken(value, label, nullable) {
    if (nullable && value === null) return null;
    if (typeof value !== "string" || !SAFE_TOKEN.test(value)) {
      throw new TypeError(
        `${label} must contain only letters, digits, dot, underscore, colon, equals, slash, or hyphen`,
      );
    }
    return value;
  }

  function validateNmax(value) {
    if (typeof value !== "number" || !Number.isFinite(value) || value < 0) {
      throw new RangeError("nmax must be a finite, non-negative number");
    }
    if (value >= 1 && !Number.isInteger(value)) {
      throw new RangeError("nmax values >= 1 must be a whole number");
    }
    return value;
  }

  function validateClimit(value) {
    if (
      typeof value !== "number" ||
      !Number.isFinite(value) ||
      value < 0 ||
      value > 1
    ) {
      throw new RangeError("climit must be a finite number between 0 and 1");
    }
    return value;
  }

  function validateMetric(value) {
    if (!SUPPORTED_METRICS.includes(value)) {
      throw new RangeError(
        `metric must be one of: ${SUPPORTED_METRICS.join(", ")}`,
      );
    }
    return value;
  }

  function validateTransport(graphUrl, allowInsecureGraph) {
    let parsed;
    try {
      parsed = new URL(graphUrl);
    } catch (error) {
      throw new TypeError("graphUrl must be an absolute HTTP(S) URL", {
        cause: error,
      });
    }
    if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
      throw new TypeError("graphUrl must be an absolute HTTP(S) URL");
    }
    const loopback = ["localhost", "127.0.0.1", "[::1]"].includes(
      parsed.hostname,
    );
    if (parsed.protocol === "http:" && !loopback && !allowInsecureGraph) {
      throw new Error(
        "remote plaintext graphUrl requires allowInsecureGraph: true; " +
          "responses can otherwise be altered in transit",
      );
    }
    return parsed.href;
  }

  function unwrapGraphSON(value) {
    let result = value;
    while (
      result !== null &&
      typeof result === "object" &&
      !Array.isArray(result) &&
      Object.prototype.hasOwnProperty.call(result, "@value")
    ) {
      result = result["@value"];
    }
    return result;
  }

  function createAbortError(reason) {
    if (reason instanceof Error && reason.name === "AbortError") return reason;
    const error = new Error("graph request was cancelled");
    error.name = "AbortError";
    if (reason !== undefined) error.cause = reason;
    return error;
  }

  function parseMethodResult(payload, methodName, expectedType) {
    if (payload === null || typeof payload !== "object") {
      throw new TypeError("invalid Gremlin response payload");
    }

    const status = payload.status || {};
    const statusCode = unwrapGraphSON(status.code);
    if (statusCode !== 200) {
      throw new Error(
        `Gremlin status ${String(statusCode)}: ${status.message || ""}`,
      );
    }

    let data = unwrapGraphSON((payload.result || {}).data);
    if (!Array.isArray(data) || data.length !== 1) {
      throw new TypeError(
        `invalid Gremlin result for gr.${methodName}: expected one value`,
      );
    }
    data = unwrapGraphSON(data[0]);

    let parsed;
    if (typeof data === "string") {
      try {
        parsed = JSON.parse(data);
      } catch (error) {
        throw new SyntaxError(`invalid JSON returned by gr.${methodName}`, {
          cause: error,
        });
      }
    } else {
      parsed = data;
    }

    const validObject =
      expectedType === "object" &&
      parsed !== null &&
      typeof parsed === "object" &&
      !Array.isArray(parsed);
    const validArray = expectedType === "array" && Array.isArray(parsed);
    if (!validObject && !validArray) {
      throw new TypeError(
        `gr.${methodName} returned JSON of the wrong type; expected ${expectedType}`,
      );
    }
    return parsed;
  }

  async function postGremlin(gremlin, methodName, expectedType, options) {
    const graphUrl = validateTransport(
      options.graphUrl === undefined ? DEFAULT_GRAPH_URL : options.graphUrl,
      options.allowInsecureGraph === true,
    );
    const timeoutMs =
      options.timeoutMs === undefined ? DEFAULT_TIMEOUT_MS : options.timeoutMs;
    if (
      typeof timeoutMs !== "number" ||
      !Number.isFinite(timeoutMs) ||
      timeoutMs <= 0
    ) {
      throw new RangeError("timeoutMs must be a finite positive number");
    }

    const fetchImpl =
      options.fetchImpl ||
      (typeof globalThis !== "undefined" ? globalThis.fetch : undefined);
    if (typeof fetchImpl !== "function") {
      throw new Error("fetch is unavailable; pass options.fetchImpl");
    }

    const controller =
      typeof AbortController === "function" ? new AbortController() : null;
    const externalSignal = options.signal;
    if (
      externalSignal !== undefined &&
      (externalSignal === null ||
        typeof externalSignal.aborted !== "boolean" ||
        typeof externalSignal.addEventListener !== "function")
    ) {
      throw new TypeError("signal must be an AbortSignal");
    }
    if (externalSignal?.aborted) {
      throw createAbortError(externalSignal.reason);
    }
    const timeoutError = new Error(
      `request to ${graphUrl} timed out after ${timeoutMs} ms`,
    );
    let timedOut = false;
    let externallyAborted = false;
    let timer;
    let rejectExternalAbort;
    const externalAbortPromise = new Promise((_, reject) => {
      rejectExternalAbort = reject;
    });
    const handleExternalAbort = () => {
      externallyAborted = true;
      const abortError = createAbortError(externalSignal.reason);
      rejectExternalAbort(abortError);
      if (controller) controller.abort(abortError);
    };
    if (externalSignal) {
      externalSignal.addEventListener("abort", handleExternalAbort, { once: true });
    }
    const timeoutPromise = new Promise((_, reject) => {
      timer = setTimeout(() => {
        timedOut = true;
        reject(timeoutError);
        if (controller) controller.abort();
      }, timeoutMs);
    });

    const requestPromise = (async () => {
      let response;
      try {
        response = await fetchImpl(graphUrl, {
          method: "POST",
          headers: { "Content-Type": "text/plain;charset=UTF-8" },
          body: JSON.stringify({ gremlin }),
          redirect: "error",
          ...(controller ? { signal: controller.signal } : {}),
        });
      } catch (error) {
        if (timedOut) throw timeoutError;
        if (externallyAborted) throw createAbortError(externalSignal.reason);
        throw new Error(`request to ${graphUrl} failed: ${error.message}`, {
          cause: error,
        });
      }

      if (!response || !response.ok) {
        const status = response && response.status !== undefined
          ? response.status
          : "unknown";
        let detail = "";
        if (response && typeof response.text === "function") {
          try {
            detail = (await response.text()).slice(0, 1000);
          } catch (_) {
            if (timedOut) throw timeoutError;
            if (externallyAborted) throw createAbortError(externalSignal.reason);
            detail = "";
          }
        }
        throw new Error(
          `HTTP ${status} from ${graphUrl}${detail ? `: ${detail}` : ""}`,
        );
      }

      let payload;
      try {
        payload = await response.json();
      } catch (error) {
        if (timedOut) throw timeoutError;
        if (externallyAborted) throw createAbortError(externalSignal.reason);
        throw new SyntaxError(`invalid JSON response from ${graphUrl}`, {
          cause: error,
        });
      }
      return parseMethodResult(payload, methodName, expectedType);
    })();

    try {
      return await Promise.race([
        requestPromise,
        timeoutPromise,
        externalAbortPromise,
      ]);
    } finally {
      clearTimeout(timer);
      if (externalSignal) {
        externalSignal.removeEventListener("abort", handleExternalAbort);
      }
    }
  }

  /**
   * Call gr.objectNeighborhood2JSON(...) and return its parsed JSON object.
   *
   * @param {string} objectId LSST/ZTF object identifier.
   * @param {string} classifier Classifier, optionally including =flavor.
   * @param {object} [options]
   * @param {string|null} [options.reclassifier=null]
   * @param {number} [options.nmax=5] Count (>=1), relative cutoff (0<n<1), or 0.
   * @param {"JensenShannon"|"Euclidean"|"Cosine"} [options.metric="JensenShannon"]
   * @param {number} [options.climit=0] Classification-weight lower limit.
   * @param {AbortSignal} [options.signal] Optional caller cancellation signal.
   * @returns {Promise<object>}
   */
  async function objectNeighborhood2JSON(objectId, classifier, options = {}) {
    if (options === null || typeof options !== "object" || Array.isArray(options)) {
      throw new TypeError("options must be an object");
    }
    const oid = validateSafeToken(objectId, "object ID", false);
    const cls = validateSafeToken(classifier, "classifier", false);
    const reclassifier = validateSafeToken(
      options.reclassifier === undefined ? null : options.reclassifier,
      "reclassifier",
      true,
    );
    const nmax = validateNmax(options.nmax === undefined ? 5 : options.nmax);
    const metric = validateMetric(
      options.metric === undefined ? "JensenShannon" : options.metric,
    );
    const climit = validateClimit(
      options.climit === undefined ? 0 : options.climit,
    );
    const reclassifierLiteral =
      reclassifier === null ? "null" : `'${reclassifier}'`;
    const gremlin =
      `gr.objectNeighborhood2JSON('${oid}','${cls}',` +
      `${reclassifierLiteral},${nmax},'${metric}',${climit})`;
    return postGremlin(gremlin, "objectNeighborhood2JSON", "object", options);
  }

  /**
   * Call gr.overlaps2JSON(classifier) and return its parsed JSON array.
   * Pass null to request overlaps for all classifiers.
   *
   * @param {string|null} classifier Classifier, optionally including =flavor.
   * @param {object} [options]
   * @returns {Promise<Array<object>>}
   */
  async function overlaps2JSON(classifier = null, options = {}) {
    if (options === null || typeof options !== "object" || Array.isArray(options)) {
      throw new TypeError("options must be an object");
    }
    const cls = validateSafeToken(classifier, "classifier", true);
    const classifierLiteral = cls === null ? "null" : `'${cls}'`;
    return postGremlin(
      `gr.overlaps2JSON(${classifierLiteral})`,
      "overlaps2JSON",
      "array",
      options,
    );
  }

  return {
    DEFAULT_GRAPH_URL,
    DEFAULT_TIMEOUT_MS,
    SUPPORTED_METRICS,
    objectNeighborhood2JSON,
    overlaps2JSON,
  };
});

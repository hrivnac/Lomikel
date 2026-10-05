# Reusable Lomikel graph functions for JavaScript

## FinkTasks: Python-equivalent standalone services

The dependency-free `object_neighbors.js` and `most_points.js` expose asynchronous
functions in both Node.js (CommonJS) and browsers (plain `<script>` tags). Each
file stands alone; neither imports `fink_graph.js` or the other module. They
return data rather than running a server or writing files.

```js
const { objectNeighbors } = require("./object_neighbors.js");
const { mostPoints } = require("./most_points.js");

const neighbors = await objectNeighbors("170028486134595648", {
  results: 5, allowInsecureGraph: true,
});
const ranking = await mostPoints({ results: 5, allowInsecureEs: true });
// For all-band Solar System source data suitable for plotting:
const ss = await mostPoints({ objectType: "ss", results: 1,
  lightcurves: true, allowInsecureEs: true });
```

In a browser, include `object_neighbors.js` and/or `most_points.js` and call
`window.objectNeighbors(id, options)` / `window.LomikelMostPoints.mostPoints(options)`. Node.js
18+ or a browser with Fetch is required; use `fetchImpl` (neighbors) or `fetch`
(ranking) to inject a compatible Fetch function in tests/older runtimes.

- `objectNeighbors(id, options)`: `classifier` (default `FINK`), `distance`
  (`JensenShannon`, `Euclidean`, `Cosine`), `results` (integer count, fractional
  graph-distance cutoff, or `0` for all), `restColumns`, `batchSize`,
  `graphUrl` (default `http://134.158.243.144:24445`), `apiUrl` (default
  `https://api.lsst.fink-portal.org`), `timeoutMs`, `allowInsecureGraph`,
  `fetchImpl`. Returns target position and REST fields, graph candidate count,
  and neighbors ranked by graph distance then angular separation.
- `mostPoints(options)`: `objectType` (`both`/`ss`/`dia`, default `both`),
  `results`, `lightcurves` (SS only), `esUrl` (default
  `http://134.158.243.139:24499`), `apiUrl` (same Fink REST default),
  `timeoutMs`, `allowInsecureEs`, `fetch`. Returns per-type Elasticsearch
  rankings (MJD points and paired radec counts), and optionally SS light-curve
  documents with **all** REST bands and source records. It does not generate
  Python's PNG plots; render the returned JSON in the host application.

Pass object IDs as **strings** to preserve their exact digits. The modules
also preserve unquoted large integer IDs in Fink REST JSON responses. Explicit
`allowInsecureGraph`/`allowInsecureEs` is needed for remote plaintext HTTP;
these flags are not encryption or CORS bypasses. The LSST Gremlin port 24445
responds to JSON POSTs with `Access-Control-Allow-Origin: *`, but its OPTIONS
preflight returns 405. `objectNeighbors` therefore sends Gremlin JSON as
`text/plain` to avoid that preflight; the Fink REST call still needs its own
CORS support. The Elasticsearch service also needs appropriate CORS headers.
An **HTTPS** browser page cannot fetch any of these plaintext HTTP endpoints
due to mixed-content blocking: use HTTPS CORS-enabled endpoints or a same-origin
HTTPS proxy. Never expose Elasticsearch or Gremlin write permissions through a
public proxy just to run these read-only queries.

Run focused tests from this directory: `node --test tests/*.test.js`.

### Minimal browser pages

For **the same arguments, defaults, and output as the original Python CLIs**,
run the loopback-only example server from this directory:

```bash
python3 examples/serve.py
# Open http://127.0.0.1:8766/examples/object_neighbors.html
# Or   http://127.0.0.1:8766/examples/most_points.html
```

The two pages submit the form arguments to the local server, which runs the
original `src/python/FinkTasks/src/fink_tasks/{object_neighbors,most_points}.py`
scripts with an allowlisted argument vector (never a shell command). This is
intentional: a static HTML page alone cannot invoke a Python process, write
server-side output files, or bypass CORS/mixed-content restrictions on the
default remote HTTP endpoints. The pages are **Python-backed CLI examples**,
not replacements for the separate JavaScript library modules above. For
browser-only deployment of those libraries, use your own protected HTTPS,
CORS-enabled service endpoints.

`object_neighbors.html` exposes object ID, classifier, distance metric,
result count/cutoff, repeatable REST columns, table/JSON output, service URLs,
timeout, and the explicit insecure-graph opt-in. The default Gremlin endpoint
is the Python CLI's `:24444` (the independent JavaScript library uses `:24445`).
`most_points.html` exposes SS/DIA/both, result count, SS light curves, output
directory name, service URLs, timeout, and insecure-ES opt-in. The local server
runs each request in an isolated temporary directory and exposes generated
ranking/manifest JSON and optional SS JSON/PNG files as downloads while it is
running; stopping it removes those files. `--output-dir` is confined to a safe
single directory name inside that temporary run. PNG generation needs
Matplotlib available to the Python executable running the server (for example,
a virtual environment with `matplotlib>=3.7,<4`).

The server binds only to `127.0.0.1`, rejects foreign Host/Origin values and
unknown input fields, and does not expose a general-purpose HTTP proxy. **The
trust boundary is the local machine:** any local process able to send loopback
HTTP requests can invoke the Python scripts, including with custom API, Gremlin,
and Elasticsearch service URLs. Host/Origin checks mitigate browser cross-site
requests, not malicious local clients; do not expose or reverse-proxy this
unauthenticated example server.

One task runs at a time (additional jobs receive 429); result counts are capped
at 100, while neighbors `0` (all) and fractional distance cutoffs remain valid.
Each task has a 600-second wall limit, 256 KiB each for stdout/stderr, and a
64 MiB run-directory allowance (up to 256 entries and 16 MiB per file).
Downloads are capped at 16 MiB; only the latest 8 run directories remain until
shutdown, when the temporary storage is removed. On POSIX the child interpreter
also has a 4 GiB address-space and 16 MiB per-file limit. Windows has no native
child memory/file-size limit in this example; directory use is polled and the
child terminated on overage, so brief bursts can exceed the storage allowance.
The Python scripts are trusted local code, not a sandbox for arbitrary programs.

Run server tests from the repository root with
`python3 -m unittest discover -s src/js/FinkTasks/examples -p 'test_serve.py'`.

## Other graph helpers

`fink_graph.js` provides two dependency-free asynchronous functions:

- `objectNeighborhood2JSON(objectId, classifier, options)` calls
  `gr.objectNeighborhood2JSON(...)`;
- `overlaps2JSON(classifier, options)` calls `gr.overlaps2JSON(classifier)`.

Both functions return the JSON produced by Lomikel as parsed JavaScript values.
They validate all values before embedding them in a Gremlin expression, reject
redirects, check both HTTP and Gremlin status codes, and enforce a request
timeout.

## Include from another Node.js script

```javascript
const {
  objectNeighborhood2JSON,
  overlaps2JSON,
} = require("./fink_graph.js");

async function main() {
  const neighborhood = await objectNeighborhood2JSON(
    "170028486134595648",
    "FINK",
    {
      nmax: 10,
      metric: "JensenShannon",
      climit: 0,
      // The default public endpoint is remote plaintext HTTP. This explicit
      // opt-in is required unless graphUrl uses HTTPS or loopback.
      allowInsecureGraph: true,
    },
  );

  const overlaps = await overlaps2JSON("FINK", {
    allowInsecureGraph: true,
  });

  console.log(neighborhood);
  console.log(overlaps);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
```

Node.js 18 or newer supplies the required global `fetch`. On older runtimes,
pass a compatible function as `fetchImpl`.

## Include in a browser page

```html
<script src="fink_graph.js"></script>
<script>
  async function loadData() {
    const neighborhood = await LomikelGraph.objectNeighborhood2JSON(
      "170028486134595648",
      "FINK",
      {
        graphUrl: "https://your-gremlin-endpoint.example/",
      },
    );

    const overlaps = await LomikelGraph.overlaps2JSON("FINK", {
      graphUrl: "https://your-gremlin-endpoint.example/",
    });

    return { neighborhood, overlaps };
  }
</script>
```

The Gremlin server must permit the page's origin through CORS. An HTTPS page
cannot call the default plaintext HTTP endpoint because browsers block mixed
content; use an HTTPS endpoint or a same-origin HTTPS proxy.

## API

### `objectNeighborhood2JSON(objectId, classifier, options = {})`

Options:

- `reclassifier`: alternate classifier or `null` (default: `null`);
- `nmax`: integer count when `>= 1`, Lomikel relative cutoff when between 0 and
  1, or `0` for all (default: `5`);
- `metric`: `JensenShannon`, `Euclidean`, or `Cosine` (default:
  `JensenShannon`);
- `climit`: classification-weight lower limit from 0 through 1 (default: `0`);
- `graphUrl`: absolute Gremlin HTTP(S) endpoint;
- `timeoutMs`: positive request timeout in milliseconds (default: `180000`);
- `signal`: optional `AbortSignal` for caller-controlled cancellation;
- `allowInsecureGraph`: explicit opt-in for a remote HTTP endpoint;
- `fetchImpl`: optional Fetch-compatible implementation.

### `overlaps2JSON(classifier = null, options = {})`

The classifier may include a flavor after `=`, including slash-containing
values such as `FEATURES=2025/13-50`. Pass
`null` to request all classifiers. Transport options are the same as above.

## Test

```bash
node --test tests/fink_graph.test.cjs
```

# Reusable Lomikel graph functions for JavaScript

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
- `allowInsecureGraph`: explicit opt-in for a remote HTTP endpoint;
- `fetchImpl`: optional Fetch-compatible implementation.

### `overlaps2JSON(classifier = null, options = {})`

The classifier may include a flavor after `=`, such as `FINK=default`. Pass
`null` to request all classifiers. Transport options are the same as above.

## Test

```bash
node --test tests/fink_graph.test.cjs
```

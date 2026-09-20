# ClassificationView

ClassificationView is a standalone browser application for exploring one Fink alert and its nearest JanusGraph neighbors. It calls the reusable `LomikelGraph` JavaScript API directly; it does not call `Neighborhood.jsp` or `Overlaps.jsp`.

## Run locally

Serve the repository root over HTTP so the symlinked D3 and graph-helper assets remain available:

```sh
python3 -m http.server 8768 --bind 127.0.0.1
```

Then open:

```text
http://127.0.0.1:8768/src/ws/FinkBrowser/ClassificationView/index.html
```

The configured LSST and ZTF endpoints currently use plaintext HTTP. A page served over HTTPS cannot call them because browsers block mixed content. Plaintext graph use is explicitly opted into in `data.js`. The ZTF service may additionally require firewall or SSH-tunnel access from the client network.

Neighbor limits accept an integer count from 1 through 20, a relative cutoff strictly between 0 and 1, or 0 for all neighbors. The last mode can be slow. Before rendering, responses are checked for exact object IDs, finite non-negative graph distances and classification weights, matching focal IDs, and bounded object/class counts. Classification weights emitted as canonical decimal or scientific-notation strings by the current backend are normalized to numbers; other JSON types and malformed strings are rejected.

## Projection semantics

The map is an approximate 2D projection, not an exact embedding:

- class labels lie on the outer ring; a force layout uses raw common-alert intersection counts to determine their approximate angular arrangement. It is not a normalized or exact distance scale;
- the selected alert's classification-weighted class affinity determines its anchor inside the ring;
- each neighbor's direction is chosen toward its classification-weighted class anchor;
- the radial target-to-neighbor separation is a square-root scaling of its JanusGraph distance;
- exact zero-distance neighbors receive a small deterministic visual offset so distinct alerts remain selectable. Their labels continue to report distance `0`, and their links are dashed.
- coincident equal-distance symbols are fanned apart by a small angle while retaining their radial graph distance. If target and neighbor have identical classification affinity, no classification direction exists; deterministic direction is used only to keep the alert selectable.

Only distances from the selected alert are supplied by the neighborhood API. The view therefore does not claim to preserve pairwise distances between every pair of neighbors.

## Interaction

- Submit the parameter form with the button or Enter.
- Cancel a slow live query from the loading dialog.
- Hover, focus, or tap a star to inspect classifications.
- Press Enter/Space on a focused star, double-click a star, or use **Center** in the alert list to recenter.
- Pan and zoom the SVG; **Reset view** returns to the initial transform.
- The controls and list panels are draggable on larger screens and flow vertically on mobile.

## Tests

```sh
node --test src/js/FinkTasks/tests/fink_graph.test.cjs
node --test src/ws/FinkBrowser/ClassificationView/tests/*.test.cjs
```

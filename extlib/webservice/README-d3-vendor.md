# Vendored d3 browser bundle

FinkBrowser plotting pages (`Venn.jsp`, `d3/venn.jsp`, `d3/lightcurve.jsp`,
`d3/correlogram.jsp`, `d3/scatterplot.jsp`) load the minified UMD bundle
`d3-v7.9.2/d3.min.js` from the official npm tarball `d3@7.9.2`
(integrity `sha512-ZKb/mONPVKzaO5UlcWcwN8bS4ezvQaYu+bsou+XgNABdGC800kwH651ADTbbTxMPHW13UlvbQafdmJQDk1w7Mg==`).
Only that bundle, the upstream `LICENSE` and `package.json` (version/provenance)
are kept. The standalone `src/webservice/ClassificationView/d3.min.js` is a
regular-file copy of the same bundle so that directory can be served on its own.
`venn-v.0.2.14/venn.js` uses the global d3 selection/transition API from this bundle.

`d3-celestial-0.7.32` is deliberately **not** switched to this bundle: d3-celestial
0.7.x is written against the d3 v3 API (`d3.geo.*`) and ships its own
`lib/d3.min.js` 3.5.17 (the final v3 release), which `d3/skyview.jsp` loads.

To update: check the current npm release, `npm pack d3@VERSION`, copy
`dist/d3.min.js`, `LICENSE` and `package.json` into `d3-vVERSION/`, copy the same
`d3.min.js` to `src/webservice/ClassificationView/`, update the five JSP references
and the `?v=` in `ClassificationView/index.html`, then run
`node src/test/js/testD3VendorAssets.js`, the other JS tests and `ant war`, and
smoke-test the plots in a browser.

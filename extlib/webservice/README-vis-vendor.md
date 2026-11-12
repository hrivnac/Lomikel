# Vendored vis browser bundles

The FinkBrowser WAR uses the standalone UMD JavaScript and minified CSS from the
npm release tarballs for `vis-network@10.1.5` and `vis-timeline@8.5.6`.
Only the browser assets referenced by `src/webservice/index.jsp`, the upstream
license files and `package.json` (version/provenance) are kept. These UMD bundles
include their browser dependencies; the WAR does not need npm or a CDN for vis.

To update: verify the current npm releases, obtain the official npm tarballs
(`npm pack vis-network@VERSION` and `npm pack vis-timeline@VERSION`), extract
`standalone/umd/*.min.js` and `styles/*.min.css` with their licenses and
`package.json` into versioned directories here, change the four references in
`src/webservice/index.jsp`, and run `node src/test/js/testVisVendorAssets.js`
plus `ant war`. Confirm both assets are in the WAR and smoke-test a graph and
timeline in a browser before opening a PR. The old full source trees are not
required by Lomikel and are replaced rather than shipped alongside the new bundles.

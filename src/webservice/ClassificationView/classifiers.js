// Edit this local catalog when graph classifier schemas change.
// Keep each classifier/flavor as the exact value expected by the graph API.
const CLASSIFIERS = Object.freeze({
  LSST: Object.freeze(["FINK", "TAG"]),
  ZTF: Object.freeze([
    "FINK", "XMATCH", "FEATURES=2025/13-50", "FEATURES=2024/13-60",
    "LIGHTCURVES=Latent", "TAG",
  ]),
});

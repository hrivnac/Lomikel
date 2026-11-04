/* Preview-only Fink object action; the root Stylesheet.js remains unchanged. */
function finkSurveyStarImage(objectId) {
  if (objectId == null) return 'Star.png';
  var id = String(objectId).trim();
  if (/^ZTF[A-Za-z0-9_-]{1,80}$/.test(id)) return 'Star-ZTF.png';
  if (/^[0-9]{1,24}$/.test(id)) return 'Star-LSST.png';
  return 'Star.png';
}
// GraphView.js evaluates graphics.image for each vertex, then prefixes images/.
if (stylesheet.nodes.object && stylesheet.nodes.object.graphics) {
  stylesheet.nodes.object.graphics.image = {js:"finkSurveyStarImage(eMap.get('objectId'))"};
}
if (stylesheet.nodes.object && !stylesheet.nodes.object.actions.some(function (action) { return action.name === 'SkyView'; })) {
  var skyActions = stylesheet.nodes.object.actions;
  var skyTableIndex = skyActions.findIndex(function (action) { return action.name === 'Table'; });
  skyActions.splice(skyTableIndex + 1, 0, {
    name: 'SkyView', url: 'd3/skyview.jsp', target: 'skyview'
  });
}
if (stylesheet.nodes.object && !stylesheet.nodes.object.actions.some(function (action) { return action.name === 'Cutouts'; })) {
  var cutoutActions = stylesheet.nodes.object.actions;
  var cutoutSkyIndex = cutoutActions.findIndex(function (action) { return action.name === 'SkyView'; });
  cutoutActions.splice(cutoutSkyIndex + 1, 0, {
    name: 'Cutouts', url: 'cutouts', target: 'image'
  });
}
if (stylesheet.nodes.object && !stylesheet.nodes.object.actions.some(function (action) { return action.name === 'LightCurve'; })) {
  var lightCurveActions = stylesheet.nodes.object.actions;
  var lightCurveShowIndex = lightCurveActions.findIndex(function (action) { return action.name === 'Show'; });
  // Use a static sentinel; never embed object IDs in the legacy inline action URL.
  lightCurveActions.splice(lightCurveShowIndex + 1, 0, {
    name: 'LightCurve', url: 'lightcurve', target: 'plot'
  });
}

// External-service links open another Fink Browser app (or the portal) in a new tab.
// GraphView.js only accepts absolute http(s) URLs for target "external", so build them here.
function finkSurveyOfObject(objectId) {
  if (objectId == null) return '';
  var id = String(objectId).trim();
  if (/^ZTF[A-Za-z0-9_-]{1,80}$/.test(id)) return 'ZTF';
  if (/^[0-9]{1,24}$/.test(id)) return 'LSST';
  return '';
}
function finkAppUrl(page, params) {
  if (typeof window === 'undefined' || !window.location) return '';
  try {
    var url = new URL(page, window.location.href);
    Object.keys(params).forEach(function (key) { url.searchParams.set(key, params[key]); });
    return /^https?:$/.test(url.protocol) ? url.href : '';
  } catch (error) { return ''; }
}
// Classification neighborhood: fills the survey and object ID of its menu.
function finkNeighborhoodUrl(objectId) {
  var survey = finkSurveyOfObject(objectId);
  return survey ? finkAppUrl('ClassificationView/index.html', {survey: survey, objectId: String(objectId).trim()}) : '';
}
// LSST Light Curve Visualizer: LSST objects only (an empty URL omits the action for ZTF).
function finkLightCurveVisualizerUrl(objectId) {
  return finkSurveyOfObject(objectId) === 'LSST' ?
    finkAppUrl('LightCurveView/index.html', {objectId: String(objectId).trim()}) : '';
}
// "Tag" registers the selected object in the TAG OCol "GraphBrowser" (see tag-graph.js). The static
// sentinel keeps object IDs out of the action URL; the ID is taken from the live vertex when clicked.
if (stylesheet.nodes.object && !stylesheet.nodes.object.actions.some(function (action) { return action.name === 'Tag'; })) {
  stylesheet.nodes.object.actions.push({name: 'Tag', url: 'tag', target: 'tag'});
}
if (stylesheet.nodes.object) {
  var externalActions = stylesheet.nodes.object.actions;
  if (!externalActions.some(function (action) { return action.name === 'Classification neighborhood'; })) {
    externalActions.push({name: 'Classification neighborhood',
      url: {js: "finkNeighborhoodUrl(eMap.get('objectId'))"}, target: 'external'});
  }
  if (!externalActions.some(function (action) { return action.name === 'LSST Light Curve Visualizer'; })) {
    externalActions.push({name: 'LSST Light Curve Visualizer',
      url: {js: "finkLightCurveVisualizerUrl(eMap.get('objectId'))"}, target: 'external'});
  }
}

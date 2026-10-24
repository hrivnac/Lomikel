/* Preview-only Fink object action; the root Stylesheet.js remains unchanged. */
if (stylesheet.nodes.object && !stylesheet.nodes.object.actions.some(function (action) { return action.name === 'SkyView'; })) {
  var skyActions = stylesheet.nodes.object.actions;
  var skyTableIndex = skyActions.findIndex(function (action) { return action.name === 'Table'; });
  skyActions.splice(skyTableIndex + 1, 0, {
    name: 'SkyView', url: 'webservice/d3/skyview.jsp', target: 'skyview'
  });
}
if (stylesheet.nodes.object && !stylesheet.nodes.object.actions.some(function (action) { return action.name === 'Cutouts'; })) {
  var cutoutActions = stylesheet.nodes.object.actions;
  var cutoutSkyIndex = cutoutActions.findIndex(function (action) { return action.name === 'SkyView'; });
  cutoutActions.splice(cutoutSkyIndex + 1, 0, {
    name: 'Cutouts', url: 'webservice/cutouts', target: 'image'
  });
}

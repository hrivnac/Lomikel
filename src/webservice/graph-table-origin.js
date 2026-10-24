/* Preview-only provenance for graph vertices loaded from different Gremlin servers. */
(function () {
  'use strict';
  var origins = new Map();
  var edgeOrigins = new Map();
  var originalShow = window.show;
  var originalValues = window.callGremlinValues;
  var originalDispose = window.disposeGraphView;

  function prune() {
    var live = new Set(window.nodes.map(function (node) { return String(node.id); }));
    var liveEdges = new Set(window.edges.map(function (edge) { return String(edge.id); }));
    origins.forEach(function (_, id) { if (!live.has(id)) origins.delete(id); });
    edgeOrigins.forEach(function (_, id) { if (!liveEdges.has(id)) edgeOrigins.delete(id); });
  }
  window.show = function (graph) {
    // Reject before legacy show can silently deduplicate an unrelated vertex
    // or connect an edge to a vertex with the same ID on another server.
    prune();
    var activeServer = window.server;
    // Legacy show retains edges whose endpoints have not been drawn yet.
    // Those endpoints still reserve their IDs for the edge's server.
    var reservedEndpoints = new Map();
    window.edges.forEach(function (edge) {
      var owner = edgeOrigins.get(String(edge.id));
      if (!owner) return;
      reservedEndpoints.set(String(edge.from), owner);
      reservedEndpoints.set(String(edge.to), owner);
    });
    if (Array.isArray(graph)) graph.forEach(function (item) {
      var id = String(item.id);
      if (item.type === 'vertex' &&
          ((origins.has(id) && origins.get(id) !== activeServer) ||
           (reservedEndpoints.has(id) && reservedEndpoints.get(id) !== activeServer))) {
        throw new Error('Conflicting vertex ID ' + id + ' across graph servers; graph unchanged.');
      }
      if (item.type !== 'edge') return;
      if (edgeOrigins.has(id) && edgeOrigins.get(id) !== activeServer) {
        throw new Error('Conflicting edge ID ' + id + ' across graph servers; graph unchanged.');
      }
      [item.inVid, item.outVid].forEach(function (endpoint) {
        var key = String(endpoint);
        if ((origins.has(key) && origins.get(key) !== activeServer) ||
            (reservedEndpoints.has(key) && reservedEndpoints.get(key) !== activeServer)) {
          throw new Error('Conflicting edge endpoint ' + key + ' across graph servers; graph unchanged.');
        }
      });
    });
    var previous = new Set(window.nodes.map(function (node) { return String(node.id); }));
    var previousEdges = new Set(window.edges.map(function (edge) { return String(edge.id); }));
    var result = originalShow.apply(this, arguments);
    if (Array.isArray(graph)) graph.forEach(function (item) {
      if (item.type === 'vertex' && !previous.has(String(item.id)) &&
          window.nodes.some(function (node) { return String(node.id) === String(item.id); })) {
        origins.set(String(item.id), activeServer);
      }
      if (item.type === 'edge' && !previousEdges.has(String(item.id)) &&
          window.edges.some(function (edge) { return String(edge.id) === String(item.id); })) {
        edgeOrigins.set(String(item.id), activeServer);
      }
    });
    prune();
    return result;
  };
  window.graphTableServerForVertex = function (id) { return origins.get(String(id)) || null; };

  window.callGremlinValues = function (request, explicitServer) {
    // Legacy Nodes.jsp queries each visible vertex independently. For a graph
    // assembled with Add to graph across servers, its implicit global server
    // belongs only to the most recent load. Select the vertex's own server.
    var match = typeof request === 'string' &&
      request.match(/^[A-Za-z_][\w]*\.V\('([0-9]+)'\)\.valueMap\(\)\.next\(\)\.toString\(\)$/);
    var origin = !explicitServer && match && origins.get(match[1]);
    if (!origin) return originalValues.apply(this, arguments);
    var activeServer = window.server;
    try { return originalValues.call(this, request, origin); }
    finally { window.server = activeServer; }
  };
  window.disposeGraphView = function () {
    origins.clear();
    edgeOrigins.clear();
    return originalDispose.apply(this, arguments);
  };
})();

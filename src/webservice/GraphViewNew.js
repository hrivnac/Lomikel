// Global variables
var network;
var nodes  = [];
var edges  = [];
var groups = [];
var selectedNode;
var selectedEdge;
var clusterIndex = 0;
var clusters = [];
var lastClusterZoomLevel = 0;
var clusterFactor = 0.9;
var data = {
    nodes:nodes,
    edges:edges,
    };
var container = document.getElementById('vis');
var network;
var gr = 'g';
var graphViewToken = {cancelled:false};
// Support the known stylesheet expressions explicitly: neither graph values
// nor stylesheet expressions become executable source in this controller.
function graphText(value) { return value == null ? '' : String(value); }
// GraphSON IDs may be numeric or restricted string IDs. Never interpolate
// arbitrary graph data into Gremlin source.
function validGraphId(id, edge) {
  if (edge) return typeof id === 'string' && /^[A-Za-z0-9_-]+$/.test(id);
  return (typeof id === 'number' && Number.isSafeInteger(id)) ||
    (typeof id === 'string' && /^[A-Za-z0-9_-]+$/.test(id));
}
function graphVertexLiteral(id) {
  if (!validGraphId(id, false)) return null;
  return typeof id === 'number' ? String(id) : "'" + id + "'";
}
function graphStyleExpression(expression, title, eMap) {
  switch (expression) {
    case "title.replace('site:', '') + '.png'":
      return graphText(title).replace('site:', '') + '.png';
    case 'title.replace("site:", "")':
      return graphText(title).replace('site:', '');
    case "finkSurveyStarImage(eMap.get('objectId'))":
      return typeof finkSurveyStarImage === 'function' ? finkSurveyStarImage(eMap.get('objectId')) : '';
    case "finkPortalUrl(eMap.get('objectId'))":
      return typeof finkPortalUrl === 'function' ? finkPortalUrl(eMap.get('objectId')) : '';
    default:
      return '';
  }
}
function escapeGraphHtml(value) {
  return graphText(value).replace(/&/g, '&amp;').replace(/</g, '&lt;')
    .replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}
function safeGraphAction(action, url) {
  url = graphText(url).trim();
  if (!url || /[\u0000-\u001f\u007f]/.test(url)) return null;
  var external = action.target === 'external';
  if (external) {
    if (!/^https?:\/\//i.test(url)) return null;
  } else {
    if (!['result','table','plot','image','skyview','graph'].includes(action.target) ||
        !((action.target === 'plot' && url === 'lightcurve') ||
          (action.target === 'image' && url === 'cutouts') ||
          /^(?!\/|\\|#|\?)[A-Za-z][\w./-]*\.jsp(?:\?[^#]*)?$/.test(url)) ||
        url.split('?')[0].split('/').includes('..')) return null;
  }
  return {name:graphText(action.name), url:url, target:action.target};
}
function graphActions(styleActions, id, eMap, pMap, isEdge, title) {
  if (!Array.isArray(styleActions)) return [];
  return styleActions.map(function (action) {
    if (!action || !action.url) return null;
    return safeGraphAction(action, stylesheetValue(action.url, id, eMap, pMap, isEdge, title));
  }).filter(Boolean);
}
function showGraphCommands(item, edge) {
  var pane = document.getElementById('commands');
  if (!pane || !item) return;
  pane.replaceChildren();
  var heading = document.createElement('b');
  heading.textContent = graphText(item.heading);
  pane.appendChild(heading);
  var help = document.createElement('button');
  help.textContent = '?';
  help.addEventListener('click', function () { w2popup.load({url:'Help-Top.html',showMax:true}); });
  pane.appendChild(help);
  ['describe','remove'].forEach(function (verb) {
    var button = document.createElement('button');
    button.type = 'button';
    button.title = verb;
    button.className = 'button-' + verb;
    button.addEventListener('click', function () {
      (edge ? (verb === 'describe' ? describeEdge : removeEdge) :
        (verb === 'describe' ? describeNode : removeNode))(item.id);
    });
    pane.appendChild(button);
  });
  pane.appendChild(document.createElement('hr'));
  (item.actions || []).forEach(function (action, index) {
    if (index) pane.appendChild(document.createTextNode(', '));
    var link = document.createElement('a');
    link.textContent = action.name;
    if (action.target === 'external') {
      link.href = action.url;
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
    } else {
      link.href = '#';
      link.addEventListener('click', function (event) {
        event.preventDefault();
        loadPane(action.target, action.url);
      });
    }
    pane.appendChild(link);
  });
}

// Release vis physics/listeners before the workspace replaces its graph pane.
function disposeGraphView() {
  graphViewToken.cancelled = true;
  if (network) network.destroy();
  network = null;
  clusters.length = 0;
  lastClusterZoomLevel = 0;
  }

var helpButton  = "<button onClick=\"w2popup.load({url:'Help-Top.html', showMax: true})\" style=\"position:absolute; top:0; right:0\">";
    helpButton += "<img src=\"images/Help.png\" width=\"10\"/>";
    helpButton += "</button>";

// Initial Gremlin call
async function bootstrap(button, level = 0) {
  // Capture the pane identity: a reset may replace these IDs while we wait.
  var token = graphViewToken;
  while (!(container = document.getElementById('vis')) ||
         !document.getElementById('gremlin_server') ||
         !document.getElementById('bootstrap_command') ||
         !document.getElementById('bootstrap_graph') ||
         !document.getElementById('add2graph')) {
    if (token.cancelled) return;
    await sleep(1000);
    }
  if (token.cancelled) return;
  var server  =  document.getElementById('gremlin_server').value;
  var command =  document.getElementById('bootstrap_command').value;
  gr = command.split('.')[0];
  if (button == 'selection') {
    command = document.getElementById('bootstrap_graph').value;
    }
  if (document.getElementById('add2graph').checked === false) {
    nodes.length = 0;
    edges.length = 0;
    }
  callGremlinGraph(command,
                   server,
                   level,
                   token);
  showTab('graph');
  }

// Gremlin call
function gcall(command, level = 0) {
  document.getElementById('bootstrap_command').value = command;
  bootstrap('text', level);
  }

// Append graph activity as text, not executable markup from commands or results.
function feedbackLine(message) {
  if (typeof window.activityLogMessage === 'function') {
    window.activityLogMessage(message);
    return;
    }
  var pane = document.getElementById('feedback');
  if (!pane) return;
  pane.appendChild(document.createTextNode(message));
  pane.appendChild(document.createElement('br'));
  var scrollPane = pane.parentElement || pane;
  scrollPane.scrollTop = scrollPane.scrollHeight;
  }

function graphQuery(request, server) {
  if (typeof window.activityLogQuery === 'function') window.activityLogQuery('JG', request);
  else feedbackLine('Sending Gremlin request to ' + server + ': ' + request);
  }

// Send request to Gremlin server giving JSON graph
// TBD: use POST
// TBD: check rc
async function callGremlinGraph(request, newServer, level = 0, token = graphViewToken) {
  if (token.cancelled) return;
  if (newServer == null) {
    newServer = server;
    }
  server = newServer;
  var select;
  while (!document.getElementById('select')) { // TBD: do better
    if (token.cancelled) return;
    await new Promise(r => setTimeout(r, 1000));
    }
  if (token.cancelled) return;
  select = document.getElementById('select').value.trim();
  if (select !== "") {
    request += "." + select;
    }
  graphQuery(request, server);
  var http = new XMLHttpRequest();
  http.onload = function() {
    if (http.readyState !== 4) return;
    if (http.status !== 200) {
      feedbackLine('Graph request failed (HTTP ' + http.status + ')');
      return;
      }
    try {
      show(parseGraph(http.responseText));
      expandNodes(level);
      }
    catch (error) {
      feedbackLine('Graph response could not be displayed: ' + error.message);
      }
    };
  http.onerror = function () { feedbackLine('Graph request failed (network error)'); };
  http.open("GET", server + (server.includes("?") ? '&' : '?') + 'gremlin=' + encodeURIComponent(request), false);
  try { http.send(); }
  catch (error) { feedbackLine('Graph request failed: ' + error.message); }
  }

// Send request to Gremlin server giving JSON values
// TBD: use POST
// TBD: check rc
function callGremlinValues(request, newServer) {
  if (newServer == null) {
    newServer = server;
    }
  server = newServer;
  graphQuery(request, server);
  var http = new XMLHttpRequest();
  http.open("GET", server + (server.includes("?") ? '&' : '?') + 'gremlin=' + encodeURIComponent(request), false);
  try {
    http.send();
    if (http.status !== 200) {
      feedbackLine('Value request failed (HTTP ' + http.status + ')');
      return '';
      }
    return parseValues(http.responseText);
    }
  catch (error) {
    feedbackLine('Value response could not be read: ' + error.message);
    return '';
    }
  }

// Parse Graphson graph
function parseGraph(graphson) {
  var g = JSON.parse(graphson).result.data['@value'];
  feedbackLine('Showing ' + g.length + ' new elements');
  var label;
  var id;
  var properties;
  var graph = [];
  var element;
  var value;
  for (var i = 0; i < g.length; i++) {
    if (g[i]['@type'] === 'g:Vertex') {
      id = g[i]['@value'].id['@value'];
      label = g[i]['@value'].label;
      if (typeof label === 'object') {
        label = g[i]['@value'].label['@value'];
        }
      properties = g[i]['@value'].properties;
      element = [];
      if (properties) {
        Object.keys(properties).forEach(function(key) {
          value = properties[key][0]['@value'].value;
          if (typeof value === 'object') {
            value = properties[key][0]['@value'].value['@value'];
            }
          element.push({key:key, value:value});
          })
        }
      graph.push({type:'vertex', label:label, id:id, element:element});
      }
    else if (g[i]['@type'] === 'g:Edge') {
      id = g[i]['@value'].id['@value'].relationId;
      label = g[i]['@value'].label;
      if (typeof label === 'object') {
        label = g[i]['@value'].label['@value'];
        }
      properties = g[i]['@value'].properties;
      element = [];
      if (properties) {
        Object.keys(properties).forEach(function(key) {
          value = properties[key]['@value'].value;
          if (typeof value === 'object') {
            value = properties[key]['@value'].value['@value'];
            }
          element.push({key:key, value:value});
          })
        }
      inVid = g[i]['@value'].inV['@value'];
      outVid = g[i]['@value'].outV['@value'];
      graph.push({type:'edge', label:label, id:id, element:element, inVid:inVid, outVid:outVid});
      }
    }
  return graph;
  }

// Parse Graphson values
function parseValues(graphson) {
  return JSON.parse(graphson).result.data['@value'];
  }

// Construct VIS graph
function show(graph) {
  var target;
  var l;
  var e;
  var id;
  var value;
  var shape;
  var image;
  var borderRadius;
  var borderWidth;
  var title;
  var subtitle;
  var subtitleArray;
  var label;
  var color;
  var inVid;
  var outVid;
  var arrows;
  var group;
  var actions;
  var actionsArray;
  var filter = document.getElementById('filter').value.trim();
  var eMap;
  var b;
  var c;
  var o;
  var u;
  // With a label filter, resolve all visible vertices before deciding whether
  // each edge has two displayed endpoints; preserve source order otherwise.
  if (graph != null && filter) graph = graph.filter(function (item) { return item.type === 'vertex'; })
    .concat(graph.filter(function (item) { return item.type !== 'vertex'; }));
  // loop over all elements (just redraw if no elements)
  if (graph != null) {
    for (var i = 0; i < graph.length; i++) {
      color = {background:'white', border:'black', highlight:'#eeeeee', inherit:false};
      l = graph[i].label;
      id = graph[i].id;
      e = graph[i].element;
      eMap = new Map();
      pMap = new Map();
      if (e.length) {
        for (var j = 0; j < e.length; j++) { // TBD: make better loop
          eMap.set(e[j].key, e[j].value);
          }
        }
      // vertex
      if (graph[i].type == 'vertex') {
        stylesheetNode = stylesheet.nodes[l];
        if (!stylesheetNode) {
          stylesheetNode = stylesheet.nodes["default"];
          }
        stylesheetValue(stylesheetNode.properties, id, eMap, pMap, false);
        title        = l + ":" + stylesheetValue(stylesheetNode.graphics.title,        id, eMap, pMap, false);
        subtitle     = stylesheetValue(stylesheetNode.graphics.subtitle,     id, eMap, pMap, false, title);
        label        = stylesheetValue(stylesheetNode.graphics.label,        id, eMap, pMap, false, title);
        group        = stylesheetValue(stylesheetNode.graphics.group,        id, eMap, pMap, false, title);
        if (Array.isArray(group)) { // TBD: ugly
          group = group[0];
          }

        value        = stylesheetValue(stylesheetNode.graphics.value,        id, eMap, pMap, false, title);
        shape        = stylesheetValue(stylesheetNode.graphics.shape,        id, eMap, pMap, false, title);
        borderDashes = stylesheetValue(stylesheetNode.graphics.borderDashes, id, eMap, pMap, false, title);
        borderWidth  = stylesheetValue(stylesheetNode.graphics.borderWidth,  id, eMap, pMap, false, title);
        borderWidth  = parseInt(borderWidth);
        if (shape === 'image') {
          image = stylesheetValue(stylesheetNode.graphics.image, id, eMap, pMap, false, title);
          // Accept a plain PNG basename only; graph-supplied site names must not
          // turn an image request into a traversal or authenticated JSP GET.
          image = typeof image === 'string' && /^[A-Za-z0-9][A-Za-z0-9_-]*\.png$/.test(image) ?
            'images/' + image : '';
          }
        else if (shape === 'box') {
          borderRadius = stylesheetValue(stylesheetNode.graphics.borderRadius, id, eMap, pMap, false, title);
          borderRadius = parseInt(borderRadius);
          }
        actions = graphActions(stylesheetNode.actions, id, eMap, pMap, false, title);
        if ((filter === '' || label.includes(filter)) && !findObjectByKey(nodes, 'id', id)) {
          nodes.push({id:id, value:value, label:graphText(label), heading:graphText(title), title:(escapeGraphHtml(title) + "<br/>" + escapeGraphHtml(subtitle)), group:graphText(group), actions:actions, shape:shape, image:image, shapeProperties:{borderRadius:borderRadius, borderDashes:borderDashes}, borderWidth:borderWidth, color:color});
          }
        //groups.push(group);
        }
      // edge
      else if (graph[i].type === 'edge') {
        stylesheetEdge = stylesheet.edges[l];
        if (!stylesheetEdge) {
          stylesheetEdge = stylesheet.edges["default"];
          }
        inVid = graph[i].inVid;
        outVid = graph[i].outVid;
        stylesheetValue(stylesheetEdge.properties, id, eMap, pMap, true);
        title        = l + ":" + stylesheetValue(stylesheetEdge.graphics.title,        id, eMap, pMap, true);
        subtitle     = stylesheetValue(stylesheetEdge.graphics.subtitle,     id, eMap, pMap, true, title);
        label        = stylesheetValue(stylesheetEdge.graphics.label,        id, eMap, pMap, true, title);
        group        = stylesheetValue(stylesheetEdge.graphics.group,        id, eMap, pMap, true, title);
        arrows       = stylesheetValue(stylesheetEdge.graphics.arrows,       id, eMap, pMap, true, title);
        value        = stylesheetValue(stylesheetEdge.graphics.value,        id, eMap, pMap, true, title);
        actions = graphActions(stylesheetEdge.actions, id, eMap, pMap, true, title);
        // A label filter can hide a neighboring vertex. Do not retain an edge
        // whose endpoint is absent from the displayed graph.
        if (filter && (!findObjectByKey(nodes, 'id', inVid) ||
                       !findObjectByKey(nodes, 'id', outVid))) continue;
        // Parallel relations may share endpoints; only a repeated relation ID is a duplicate.
        if (!findObjectByKey(edges, 'id', id)) {
          if (findObjectByKey(nodes, 'id', inVid) && findObjectByKey(nodes, 'id', outVid) && findObjectByKey(nodes, 'id', inVid).group != findObjectByKey(nodes, 'id', outVid).group) {
            color = 'grey';
            edges.push({id:id, from:outVid, to:inVid, value:value, color:{color:color, highlight:color, hover:color, inherit:false}, arrows:arrows, label:graphText(label), heading:graphText(title), title:(escapeGraphHtml(title) + "<br/>" + escapeGraphHtml(subtitle)), actions:actions});
            }
          else {
            edges.push({id:id, from:outVid, to:inVid, value:value, arrows:arrows, label:graphText(label), heading:graphText(title), title:(escapeGraphHtml(title) + "<br/>" + escapeGraphHtml(subtitle)), actions:actions});
            }
          }
        }
      }
    }
  var previousView = null;
  if (network) {
    if ((graph == null || document.getElementById('add2graph').checked) &&
        typeof network.getScale === 'function' && typeof network.getViewPosition === 'function') {
      previousView = {scale:network.getScale(), position:network.getViewPosition()};
      }
    network.destroy();
    }
  container = document.getElementById('vis');
  clusters.length = 0;
  lastClusterZoomLevel = previousView ? previousView.scale : 0;
  network = new vis.Network(container, data, options4Graph);
  if (graph != null && document.getElementById('clusterize').checked === true) {
    clusterByGroups(false);
    }
  if (previousView) network.moveTo(previousView);
  network.once('initRedraw', function() {
    if (lastClusterZoomLevel === 0) {
      lastClusterZoomLevel = network.getScale();
      }
    });
  network.on('zoom', function (params) {
    if (!document.getElementById('zoom').checked) return;
    if (params.direction == '-') {
       if (params.scale < lastClusterZoomLevel * clusterFactor) {
         makeClusters(params.scale);
         lastClusterZoomLevel = params.scale;
         }
      }
    else {
      openClusters(params.scale);
      }
    });
  network.on("click", function(params) {
    if (params.nodes.length == 1) {
      if (network.isCluster(params.nodes[0]) == true) {
        }
      else {
        selectedNode = findObjectByKey(nodes, 'id', params.nodes[0]);
        showGraphCommands(selectedNode, false);
        }
      }
    else if (params.edges.length == 1) {
      selectedEdge = findObjectByKey(edges, 'id', params.edges[0]);
      if (selectedEdge) { // TBD: should test on cluster
        showGraphCommands(selectedEdge, true);
        }
      }
    });
  network.on("doubleClick", function(params) {
    if (params.nodes.length == 1) {
      if (network.isCluster(params.nodes[0]) == true) {
        network.openCluster(params.nodes[0]);
        }
      else {
        selectedNode = findObjectByKey(nodes, 'id', params.nodes[0]);
        expand(selectedNode.id);
        }
      }
    else if (params.edges.length == 1) {
      expandEdge(params.edges[0]);
      }
    });
  }

// Expand each discovered vertex once per request, without chasing a growing array.
function expandNodes(level = 1) {
  var visited = new Set();
  var frontier = nodes.map(function (node) { return node.id; });
  for (var depth = 0; depth < level && frontier.length; depth++) {
    for (var i = 0; i < frontier.length; i++) {
      visited.add(frontier[i]);
      expand(frontier[i]);
      }
    frontier = nodes.map(function (node) { return node.id; }).filter(function (id) {
      return !visited.has(id);
      });
    }
  }

// Expand both endpoints of a selected edge using the same direction menu.
function expandEdge(id) {
  var edge = findObjectByKey(edges, 'id', id);
  if (!edge) return;
  if (!document.getElementById('expandTo').checked && !document.getElementById('expandFrom').checked) {
    feedbackLine('Select Children or Parents in Graph tools before expanding.');
    return;
    }
  selectedEdge = edge;
  var edgeSource = typeof window.graphTableServerForEdge === 'function' ? window.graphTableServerForEdge(id) : server;
  if (!edgeSource) {
    feedbackLine('Cannot expand an edge with unknown graph origin.');
    return;
  }
  if (document.getElementById('removeOld').checked) {
    var endpoints = new Set([edge.from, edge.to]);
    for (var i = nodes.length - 1; i >= 0; i--) {
      if (!endpoints.has(nodes[i].id)) nodes.splice(i, 1);
      }
    for (var j = edges.length - 1; j >= 0; j--) {
      if (edges[j].id !== id) edges.splice(j, 1);
      }
    }
  expand(edge.from, true, edgeSource);
  if (edge.to !== edge.from) expand(edge.to, true, edgeSource);
  }

// Expand one more level
function expand(id, preserveExisting = false, edgeSource = null) {
  var literal = graphVertexLiteral(id);
  if (literal === null) {
    feedbackLine('Cannot expand a vertex with an unsupported ID.');
    return;
  }
  var owner = typeof window.graphTableServerForVertex === 'function' ? window.graphTableServerForVertex(id) : server;
  if (owner && edgeSource && owner !== edgeSource) {
    feedbackLine('Cannot expand an edge with endpoints from different graph servers.');
    return;
  }
  var source = owner || edgeSource;
  if (!source) {
    feedbackLine('Cannot expand a vertex with unknown graph origin.');
    return;
  }
  if (!document.getElementById('expandTo').checked && !document.getElementById('expandFrom').checked) {
    feedbackLine('Select Children or Parents in Graph tools before expanding.');
    return;
    }
  if (document.getElementById('removeOld').checked && !preserveExisting) {
    var vertex = findObjectByKey(nodes, 'id', id);
    if (!vertex) return;
    nodes.length = 0;
    edges.length = 0;
    nodes.push(vertex);
    }
  var selectedServer = server;
  try {
    if (document.getElementById('expandTo').checked) {
      callGremlinGraph(gr + ".V(" + literal + ").out()", source);
      }
    if (document.getElementById('expandFrom').checked) {
      callGremlinGraph(gr + ".V(" + literal + ").in()", source);
      }
    if (document.getElementById('expandTo').checked) {
      callGremlinGraph(gr + ".V(" + literal + ").outE()", source);
      }
    if (document.getElementById('expandFrom').checked) {
      callGremlinGraph(gr + ".V(" + literal + ").inE()", source);
      }
    }
  finally { server = selectedServer; }
  }

// Only GraphSON map structure creates description rows; graph strings remain text.
function graphDescriptionRows(values) {
  if (!Array.isArray(values) || !values.length || !values[0] ||
      values[0]['@type'] !== 'g:Map' || !Array.isArray(values[0]['@value'])) return null;
  function plain(value) {
    if (Array.isArray(value)) return value.map(plain).join(', ');
    if (value && typeof value === 'object' && '@value' in value) return plain(value['@value']);
    return graphText(value);
  }
  var entries = values[0]['@value'];
  var rows = [];
  for (var i = 0; i + 1 < entries.length; i += 2) {
    rows.push(plain(entries[i]) + ': ' + plain(entries[i + 1]));
  }
  return rows;
}

// Describe selected node
function describeNode(id) {
  var literal = graphVertexLiteral(id);
  if (literal === null) return;
  var node = findObjectByKey(nodes, 'id', id);
  if (!node) return;
  popup(node.title, callGremlinValues(gr + '.V(' + literal + ').valueMap()'), JSON.stringify(node));
  }

// Describe selected edge
function describeEdge(id) {
  if (!validGraphId(id, true)) return;
  var edge = findObjectByKey(edges, 'id', id);
  if (!edge) return;
  popup(edge.title, callGremlinValues(gr + ".E('" + id + "').valueMap()"), JSON.stringify(edge));
  }

// Remove selected node
function removeNode(id, type) {
  removeObjectByKey(nodes, 'id', id);
  for (var i = edges.length - 1; i >= 0; i--) {
    if (edges[i].from === id || edges[i].to === id) edges.splice(i, 1);
    }
  show(null, null);
  }

// Remove selected edge
function removeEdge(id) {
  removeObjectByKey(edges, 'id', id);
  show(null, null);
  }

// Apply Filter
function applyFilter() {
  if (!network || !document.getElementById('filter')) return;
  var filter = document.getElementById('filter').value.trim();
  if (filter) {
    filterObjectByKey(nodes, 'label', filter);
    var visible = new Set(nodes.map(function (node) { return node.id; }));
    for (var i = edges.length - 1; i >= 0; i--) {
      if (!visible.has(edges[i].from) || !visible.has(edges[i].to)) edges.splice(i, 1);
      }
    show(null, null);
    }
  }

// Cluser by Groups
function clusterByGroups(resetData = true) {
  if (!network) return;
  if (resetData) network.setData(data);
  var uniqueGroups = new Set(nodes.map(function (node) { return node.group; }));
  uniqueGroups.forEach(function (group) { clusterGroup(group); });
  }

// Cluser Group
function clusterGroup(group) {
  if (!network) return;
  if (typeof group === 'string' && group.trim() != '') {
    clusterOptionsByData = {
      joinCondition: function(childOptions) {
        return childOptions.group == group;
        },
      processProperties: function(clusterOptions, childNodes, childEdges) {
        var totalMass = 0;
        for (var i = 0; i < childNodes.length; i++) {
          totalMass += childNodes[i].mass;
          }
        clusterOptions.mass = totalMass;
        clusterOptions.value = totalMass;
        clusterOptions.color = childNodes[0].color;
        clusterOptions.title = 'contains ' + childNodes.length;
        return clusterOptions;
        },
      clusterNodeProperties: {id:('cluster:' + group), type:'cluster', borderWidth:3, shape:'star', label:('cluster:' + group), title:escapeGraphHtml('cluster:' + group)}
      };
    network.cluster(clusterOptionsByData);
    }
  }

// Cluste by Hubs
function clusterByHubsize() {
  if (!network) return;
  network.setData(data);
  var clusterOptionsByData = {
    processProperties: function(clusterOptions, childNodes) {
      clusterOptions.label = "[" + childNodes.length + "]";
      return clusterOptions;
      },
    clusterNodeProperties: {borderWidth:3, shape:'box', color:'grey', font:{size:30}}
    };
  network.clusterByHubsize(undefined, clusterOptionsByData);
  }

// Expand clusters
function clusterExpand() {
  if (!network) return;
  network.setData(data);
  var clusterOptionsByData = {
    joinCondition:function(childOptions) {
      return false;
      },
    };
  network.cluster(clusterOptionsByData);
  }

// Fill Edges
function fillEdges() {
  if (!network) return;
  var selectedServer = server;
  var originOf = typeof window.graphTableServerForVertex === 'function' ?
    window.graphTableServerForVertex : null;
  var snapshot = nodes.slice();
  var visibleIds = new Map();
  var entries = [];
  // All endpoints of one relation must belong to the same source graph.
  // Resolve every origin before issuing any request, so an unknown ID cannot
  // silently be looked up on the last selected server in a mixed graph.
  for (var i = 0; i < snapshot.length; i++) {
    var literal = graphVertexLiteral(snapshot[i].id);
    var source = originOf ? originOf(snapshot[i].id) : selectedServer;
    if (literal === null || !source) {
      feedbackLine('Cannot request edges for vertices with unknown origin or unsupported IDs.');
      return;
      }
    if (!visibleIds.has(source)) visibleIds.set(source, []);
    visibleIds.get(source).push(literal);
    entries.push({node:snapshot[i], literal:literal, source:source});
    }
  try {
    entries.forEach(function (entry) {
      var sameGraphIds = visibleIds.get(entry.source).join(',');
      feedbackLine('Expanding ' + entry.node.label);
      if (document.getElementById('expandTo').checked) {
        callGremlinGraph(gr + ".V(" + entry.literal + ").outE().filter(inV().hasId(within(" + sameGraphIds + ")))", entry.source);
        }
      if (document.getElementById('expandFrom').checked) {
        callGremlinGraph(gr + ".V(" + entry.literal + ").inE().filter(outV().hasId(within(" + sameGraphIds + ")))", entry.source);
        }
      });
    }
  finally { server = selectedServer; }
  }

// Cluster by Zoom
function makeClusters(scale) {
  var clusterOptionsByData = {
    processProperties: function (clusterOptions, childNodes) {
      clusterIndex = clusterIndex + 1;
      var childrenCount = 0;
      for (var i = 0; i < childNodes.length; i++) {
        childrenCount += childNodes[i].childrenCount || 1;
        }
      clusterOptions.childrenCount = childrenCount;
      clusterOptions.label = "# " + childrenCount + "";
      clusterOptions.color = "white";
      clusterOptions.font = {size: childrenCount * 5 + 30}
      clusterOptions.id = 'cluster:' + clusterIndex;
      clusters.push({id:'cluster:' + clusterIndex, scale:scale});
      return clusterOptions;
      },
    clusterNodeProperties:{borderWidth:3, shape:'database', font:{size:30}}
    }
  network.clusterOutliers(clusterOptionsByData);
  if (document.getElementById('stabilize').checked === true) {
    // since we use the scale as a unique identifier, we do NOT want to fit after the stabilization
    network.setOptions({physics:{stabilization:{fit:false}}});
    network.stabilize();
    }
  }

// Open clusterd by Zoom
function openClusters(scale) {
  var newClusters = [];
  var declustered = false;
  for (var i = 0; i < clusters.length; i++) {
    if (clusters[i].scale < scale) {
      network.openCluster(clusters[i].id);
      lastClusterZoomLevel = scale;
      declustered = true;
     }
    else {
      newClusters.push(clusters[i])
      }
    }
  clusters = newClusters;
  if (declustered === true && document.getElementById('stabilize').checked === true) {
    // since we use the scale as a unique identifier, we do NOT want to fit after the stabilization
    network.setOptions({physics:{stabilization:{fit: false}}});
    network.stabilize();
    }
  }

// Switch physics on/off
function switchPhysics() {
  if (!network || !document.getElementById('physics')) return;
  options4Graph.physics.enabled = document.getElementById('physics').checked;
  network.setOptions(options4Graph);
  }

// Switch layout on/off
function switchLayout() {
  if (!network || !document.getElementById('glayout')) return;
  if (document.getElementById('glayout').checked) {
    var direction = "LR";
    if (document.getElementById('glayout_direction').checked) {
      direction = "UD";
      }
    var method = "directed";
    if (document.getElementById('glayout_method').checked) {
      method = "hubsize";
      }
    network.setOptions({layout:{improvedLayout:true, hierarchical:{direction:direction, sortMethod:method}}});
    }
  else {
    network.setOptions({layout:{improvedLayout:true, hierarchical:false}});
    }
  }

// Find in array
function findObjectByKey(array, key, value) {
  for (var i = 0; i < array.length; i++) {
    if (array[i][key] === value) {
      return array[i];
      }
    }
  return null;
  }

// Find in array
function findObjectByKey(array, key1, value1, key2, value2) {
  for (var i = 0; i < array.length; i++) {
    if (array[i][key1] === value1 && array[i][key2] === value2) {
      return array[i];
      }
    }
  return null;
  }

// Remove from array
// TBD: should be possible without redrawing
function removeObjectByKey(array, key, value) {
  var newArray = [];
  var j = 0;
  for (var i = 0; i < array.length; i++) {
    if (array[i][key] != value) {
      newArray[j++] = array[i];
      }
    }
  array.length = 0;
  for (var i = 0; i < newArray.length; i++) {
    array.push(newArray[i]);
    }
  }

// Remove from array
// TBD: should be possible without redrawing
function filterObjectByKey(array, key, value) {
  var newArray = [];
  var j = 0;
  for (var i = 0; i < array.length; i++) {
    if (array[i][key].includes(value)) {
      newArray[j++] = array[i];
      }
    }
  array.length = 0;
  for (var i = 0; i < newArray.length; i++) {
    array.push(newArray[i]);
    }
  }

// Open popup window
function popup(name, description, details) {
  var w = window.open('', '_blank', 'height=600,width=600,menubar=no,status=no,toolbar=no');
  if (!w) return false;
  var doc = w.document;
  doc.title = graphText(name);
  var heading = doc.createElement('b');
  heading.textContent = graphText(name);
  doc.body.appendChild(heading);
  doc.body.appendChild(doc.createElement('hr'));
  var content = doc.createElement('pre');
  var rows = graphDescriptionRows(description);
  if (rows !== null) {
    rows.forEach(function (row, index) {
      if (index) content.appendChild(doc.createElement('br'));
      content.appendChild(doc.createTextNode(row));
    });
  } else {
    content.appendChild(doc.createTextNode(typeof description === 'string' ? description :
      JSON.stringify(description) || ''));
  }
  doc.body.appendChild(content);
  if (details !== undefined) {
    doc.body.appendChild(doc.createElement('hr'));
    var detail = doc.createElement('pre');
    detail.appendChild(doc.createTextNode(graphText(details)));
    doc.body.appendChild(detail);
  }
  var close = doc.createElement('button');
  close.textContent = 'close';
  close.addEventListener('click', function () { w.close(); });
  doc.body.appendChild(close);
  w.focus();
  return false;
  }

// Call URL
function callInfo(element, key) {
  var http = new XMLHttpRequest();
  http.open("GET", "Info.jsp?element=" + element + "&key=" + encodeURIComponent(key), false);
  http.send();
  return http.responseText;
  }

// Get stylesheet value
// (title can be used by js)
// TBD: handle default if undefined
function stylesheetValue(nam, id, eMap, pMap, ifEdge, title) {
  var set = ifEdge ? 'E' : 'V';
  if (nam == null) return '';
  if (nam.gremlin) {
    var literal = ifEdge ? (validGraphId(id, true) ? "'" + id + "'" : null) : graphVertexLiteral(id);
    val = literal === null ? '' : callGremlinValues(gr + '.' + set + '(' + literal + ').' + nam.gremlin);
    }
  else if (nam.js) {
    val = graphStyleExpression(nam.js, title, eMap);
    }
  else if (eMap.has(nam)) {
    val = eMap.get(nam);
    }
  else {
    val = nam;
    }
  if (typeof(val) == 'number') {
    val = val.toString();
    }
  else if (typeof(val) == 'object') {
    if (Array.isArray(val)) {
      for (v in val) {
        if (JSON.stringify(val[v]).startsWith('{"@type":"g:Map"')) { // TBD: this is ugly
          if (typeof(val[v]['@value'][1]['@value']) == "number") {
            pMap.set(val[v]['@value'][0], val[v]['@value'][1]['@value']);
            }
          else if (typeof(val[v]['@value'][1]['@value'][0]) == "object") {
            pMap.set(val[v]['@value'][0], val[v]['@value'][1]['@value'][0]['@value']);
            }
          else {
            pMap.set(val[v]['@value'][0], val[v]['@value'][1]['@value'][0]);
            }
          }
        }
      }
    }
  return val;
  }

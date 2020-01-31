// Initial Gremlin call
function bootstrap() {
  nodes.length = 0;
  // edges.length = 0;
  document.getElementById("feedback").innerHTML = "";
  callGremlinGraph(document.getElementById('bootstrap_command').value,
                   document.getElementById('gremlin_server').value);
  }
  
// Send request to Gremlin server giving Graphson graph
// TBD: use POST
// TBD: check rc
function callGremlinGraph(request, newServer) {
  if (newServer == null) {
    newServer = server;
    }
  server = newServer;
  var http = new XMLHttpRequest();
  http.onload = function() {
    if (http.readyState === 4 && http.status === 200) {
      show(parseGraph(http.responseText)); 
      }
    };
  http.open("GET", server + '?gremlin=' + request);
  http.send(); 
  }  
  
// Send request to Gremlin server giving Graphson values
// TBD: use POST
// TBD: check rc
function callGremlinValues(request, newServer) {
  if (newServer == null) {
    newServer = server;
    }
  server = newServer;
  var http = new XMLHttpRequest();
  http.open("GET", server + '?gremlin=' + request, false);
  http.send();
  return parseValues(http.responseText)
  }

// Parse Graphson graph
function parseGraph(graphson) {
  var g = JSON.parse(graphson).result.data['@value'];
  document.getElementById("feedback").innerHTML += "Showing " + g.length + " new elements # ";
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
  // loop over all elements (just redraw if no elements)
  if (graph != null) {
    for (var i = 0; i < graph.length; i++) {
      color = {background:'white', border:'black', highlight:'#eeeeee', inherit:false};
      l = graph[i].label;
      id = graph[i].id;
      e = graph[i].element;
      eMap = new Map();
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
        title        = stylesheetValue(stylesheetNode.graphics.title,        id, eMap);
        title = (title === '') ? '' : l + ":" + title;                       
        subtitle     = stylesheetValue(stylesheetNode.graphics.subtitle,     id, eMap);
        label        = stylesheetValue(stylesheetNode.graphics.label,        id, eMap);
        group        = stylesheetValue(stylesheetNode.graphics.group,        id, eMap);
        value        = stylesheetValue(stylesheetNode.graphics.value,        id, eMap);
        shape        = stylesheetValue(stylesheetNode.graphics.shape,        id, eMap);
        borderDashes = stylesheetValue(stylesheetNode.graphics.borderDashes, id, eMap);
        borderWidth  = stylesheetValue(stylesheetNode.graphics.borderWidth,  id, eMap);
        borderWidth  = parseInt(borderWidth);
        if (shape === 'image') {
          image        = stylesheetValue(stylesheetNode.graphics.image, id, eMap);
          image = (image === '') ? '' : 'images/' + image;
          }
        else if (shape === 'box') {
          borderRadius = stylesheetValue(stylesheetNode.graphics.borderRadius, id, eMap);
          borderRadius = parseInt(borderRadius);
          }
        actionsArray = stylesheetValue(stylesheetNode.actions, id, eMap);
        actions = "";
        for (var k = 0; k < actionsArray.length; k++) {
          url = stylesheetValue(actionsArray[k].url, id, eMap);
          actions += "<a href= '" + url + "' target='RESULT'>" + actionsArray[k].name + "</a> - ";
          }
        if ((filter === '' || label.includes(filter)) && !findObjectByKey(nodes, 'id', id)) {
          nodes.push({id:id, value:value, label:label, title:(title + "<br/>" + subtitle), group:group, actions:actions, shape:shape, image:image, shapeProperties:{borderRadius:borderRadius, borderDashes:borderDashes}, borderWidth:borderWidth, color:color});
          }
        groups.push(group);
        }
      // edge
      else if (graph[i].type === 'edge') {
        stylesheetEdge = stylesheet.edges[l];
        if (!stylesheetEdge) {
          stylesheetEdge = stylesheet.edges["default"];
          }
        inVid = graph[i].inVid;
        outVid = graph[i].outVid;
        title        = stylesheetValue(stylesheetEdge.graphics.title,        id, eMap, true);
        title = (title === '') ? '' : l + ":" + title;                                 
        label        = stylesheetValue(stylesheetEdge.graphics.label,        id, eMap, true);
        group        = stylesheetValue(stylesheetEdge.graphics.group,        id, eMap, true);
        subtitle     = stylesheetValue(stylesheetEdge.graphics.subtitle,     id, eMap, true);
        value        = stylesheetValue(stylesheetEdge.graphics.value,        id, eMap, true);
        actionsArray = stylesheetValue(stylesheetEdge.actions,               id, eMap, true);
        actions = "";
        for (var k = 0; k < actionsArray.length; k++) {
          actions += "<a href= '" + actionsArray[k].url + "' target='RESULT'>" + actionsArray[k].name + "</a> - ";
          }
        arrows = 'middle';
        if (!findObjectByKey(edges, 'id', id) && !findObjectByKey(edges, 'from', inVid, 'to', outVid)) {
          if (findObjectByKey(nodes, 'id', inVid) && findObjectByKey(nodes, 'id', outVid) && findObjectByKey(nodes, 'id', inVid).group != findObjectByKey(nodes, 'id', outVid).group) {
            color = 'grey';
            edges.push({id:id, from:outVid, to:inVid, value:value, color:{color:color, highlight:color, hover:color, inherit:false}, arrows:arrows, label:label, title:(title + "<br/>" + subtitle), actions:actions});
            }
          else {
            edges.push({id:id, from:outVid, to:inVid, value:value, arrows:arrows, label:label, title:(title + "<br/>" + subtitle), actions:actions});
            }
          }
        }
      }
    }
  network = new vis.Network(container, data, options);
  if (graph != null) {
    clusterByGroups();   
    }
  network.on("click", function(params) {
    if (params.nodes.length == 1) {
      if (network.isCluster(params.nodes[0]) == true) {
        }
      else {
        selectedNode = findObjectByKey(nodes, 'id', params.nodes[0]);
        //document.getElementById("output").innerHTML = "<iframe width='100%' name='output' frameborder='0'/>";
        document.getElementById("commands").innerHTML = "<b><u>" + selectedNode.label + "</u></u>"
                                                                 + "&nbsp;<input type='button' onclick='removeNode(" + selectedNode.id + ")'   value='Remove'>"
                                                                 + "&nbsp;<input type='button' onclick='describeNode(" + selectedNode.id + ")' value='Describe'><br/>"
                                                                 + selectedNode.actions;
        }
      }
    else if (params.edges.length == 1) {
      selectedEdge = findObjectByKey(edges, 'id', params.edges[0]);
      if (selectedEdge) { // TBD: should test on cluster
        //document.getElementById("output").innerHTML = "<iframe width='100%' name='output' frameborder='0'/>";
        document.getElementById("cpmmands").innerHTML = "<b><u>" + selectedEdge.label + "</u></u>"
                                                                 + "&nbsp;<input type='button' onclick='removeEdge(\"" + selectedEdge.id + "\")'   value='Remove'>"
                                                                 + "&nbsp;<input type='button' onclick='describeEdge(\"" + selectedEdge.id + "\")' value='Describe'><br/>"
                                                                 + selectedEdge.actions;
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
        document.getElementById("feedback").innerHTML = "Expanding " + selectedNode.label + " # ";
        if (document.getElementById('removeOld').checked) {
          nodes.length = 0;
          //edges.length = 0;
          nodes.push(selectedNode);
          }
        if (document.getElementById('expandTo').checked) {
          callGremlinGraph("g.V(" + selectedNode.id + ").out().limit(10)");
           }
        if (document.getElementById('expandFrom').checked) {
          callGremlinGraph("g.V(" + selectedNode.id + ").in().limit(10)");
          }
        if (document.getElementById('expandTo').checked) {
           callGremlinGraph("g.V(" + selectedNode.id + ").outE().limit(10)");
          }
        if (document.getElementById('expandFrom').checked) {
           callGremlinGraph("g.V(" + selectedNode.id + ").inE().limit(10)");
          }
        }
      }
    });
  }
    
// Describe selected node
function describeNode(id) {
  popup(id, callGremlinValues("g.V('" + id + "').valueMap().toList().toString().replace(', ', '<br/>').replace(']', '').replace('[', '')"));
  }
    
// Describe selected edge
function describeEdge(id) {
  popup(id, callGremlinValues("g.E('" + id + "').valueMap().toList().toString().replace(', ', '<br/>').replace(']', '').replace('[', '')"));
  }
    
// Get stylesheet value
function stylesheetValue(nam, id, eMap, ifEdge) {
  var set = ifEdge ? 'E' : 'V';
  if (nam.gremlin) {
    val = callGremlinValues('g.' + set + '("' + id + '").' + nam.gremlin)[0];
    }
  else if (nam.js) {
    val = eval(nam.js);
    }
  else {
    val = eMap.get(nam);
    if (!val) {
      val = nam;
      }
    }
  if (typeof val == 'number') {
    val = val.toString();
    }
  return val;
  }

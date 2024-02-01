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

var helpButton  = "<button onClick=\"w2popup.load({url:'Help-Top.html', showMax: true})\" style=\"position:absolute; top:0; right:0\">";
    helpButton += "<img src=\"images/Help.png\" width=\"10\"/>";
    helpButton += "</button>";

// Initial Gremlin call
async function bootstrap(button, level = 0) {
  while (!container && !document.getElementById('gremlin_server') || !document.getElementById('bootstrap_command')) {
    await sleep(1000);
    }
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
                   level);
  showTab('graph');
  }
  
// Gremlin call
function gcall(command, level = 0) {
  document.getElementById('bootstrap_command').value = command;
  bootstrap('text', level);
  }
 
// Send request to Gremlin server giving JSON graph
// TBD: use POST
// TBD: check rc
async function callGremlinGraph(request, newServer, level = 0) {
  if (newServer == null) {
    newServer = server;
    }
  server = newServer;
  var select;
  while (!document.getElementById('select')) { // TBD: do better
    await new Promise(r => setTimeout(r, 1000));
    }
  select = document.getElementById('select').value.trim();
  if (select !== "") {
    request += "." + select;
    }
  var [host, port] = server.split("//")[1].split(":");
  document.getElementById("feedback").innerHTML += "Sending Gremlin request to " + server + ": " + request + "<br/>";
  var http = new XMLHttpRequest();
  http.onload = function() {
    if (http.readyState === 4 && http.status === 200) {
      show(parseGraph(http.responseText));
      expandNodes(level);
      }
    };
  if (server.includes("?")) {
    http.open("GET", server + '&gremlin=' + request, false);
    }
  else {
    http.open("GET", server + '?gremlin=' + request, false);
    }
  http.send(); 
  }  
  
// Send request to Gremlin server giving JSON values
// TBD: use POST
// TBD: check rc
function callGremlinValues(request, newServer) {
  if (newServer == null) {
    newServer = server;
    }
  server = newServer;
  var [host, port] = server.split("//")[1].split(":");
  document.getElementById("feedback").innerHTML += "Sending Gremlin request to " + server + ": " + request + "<br/>";
  var http = new XMLHttpRequest();
  if (server.includes("?")) {
    http.open("GET", server + '&gremlin=' + encodeURIComponent(request), false);
    }
  else {
    http.open("GET", server + '?gremlin=' + request, false);
    }
  http.send();
  return parseValues(http.responseText)
  }
  
// Parse Graphson graph
function parseGraph(graphson) {
  var g = JSON.parse(graphson).result.data['@value'];
  document.getElementById("feedback").innerHTML += "Showing " + g.length + " new elements<br/>";
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
        groups.push(group);
        value        = stylesheetValue(stylesheetNode.graphics.value,        id, eMap, pMap, false, title);
        shape        = stylesheetValue(stylesheetNode.graphics.shape,        id, eMap, pMap, false, title);
        borderDashes = stylesheetValue(stylesheetNode.graphics.borderDashes, id, eMap, pMap, false, title);
        borderWidth  = stylesheetValue(stylesheetNode.graphics.borderWidth,  id, eMap, pMap, false, title);
        borderWidth  = parseInt(borderWidth);
        if (shape === 'image') {
          image        = stylesheetValue(stylesheetNode.graphics.image, id, eMap, pMap, false, title);
          image = (image === '') ? '' : 'images/' + image;
          }
        else if (shape === 'box') {
          borderRadius = stylesheetValue(stylesheetNode.graphics.borderRadius, id, eMap, pMap, false, title);
          borderRadius = parseInt(borderRadius);
          }
        actionsArray = stylesheetValue(stylesheetNode.actions, id, eMap, pMap, false, title);
        actions = "";
        for (var k = 0; k < actionsArray.length; k++) {
          if (actionsArray[k].url) {
            url = stylesheetValue(actionsArray[k].url, id, eMap, pMap, false, title);
            if (url) {
              url = encodeURI(url);
              if (actionsArray[k].target == "external") {
                actions += "<a href='" + url + "' target='_blank'>" + actionsArray[k].name + "<sup><small>&#8599;</small></sup></a>";
                }
              else {
                actions += "<a href='#' onclick='loadPane(\"" + actionsArray[k].target + "\", \"" + url + "\")'>" + actionsArray[k].name + "</a>";
                }
              actions += " - ";
              }
            }
          }
        if ((filter === '' || label.includes(filter)) && !findObjectByKey(nodes, 'id', id)) {
          nodes.push({id:id, value:value, label:label, title:(title + "<br/>" + subtitle), group:group, actions:actions, shape:shape, image:image, shapeProperties:{borderRadius:borderRadius, borderDashes:borderDashes}, borderWidth:borderWidth, color:color});
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
        actionsArray = stylesheetValue(stylesheetEdge.actions,               id, eMap, pMap, true, title);
        actions = "";
        for (var k = 0; k < actionsArray.length; k++) {
          url = stylesheetValue(actionsArray[k].url, id, eMap, pMap, true, title);
          if (url) {
            url = encodeURI(url);
            if (actionsArray[k].target == "external") {
              actions += "<a href='" + url + "' target='_blank'>" + actionsArray[k].name + "<sup><small>&#8599;</small></sup></a>";
              }
            else {
              actions += "<a href='#' onclick='loadPane(\"" + actionsArray[k].target + "\", \"" + url + "\")'>" + actionsArray[k].name + "</a>";
              }
            actions += " - ";
            }
          }
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
  network = new vis.Network(container, data, options4Graph);
  if (graph != null && document.getElementById('clusterize').checked === true) {
    clusterByGroups();   
    }
  network.once('initRedraw', function() {
    if (lastClusterZoomLevel === 0) {
      lastClusterZoomLevel = network.getScale();
      }
    });
  network.on('zoom', function (params) {
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
        title = selectedNode.title.split("<br/>")[0];
        document.getElementById("commands").innerHTML = "<b><u>" + title + "</u></b>" + helpButton
                                                                 + "&nbsp;<input type='button' onclick='describeNode(" + selectedNode.id + ")'  title='describe' class='button-describe'>"
                                                                 + "&nbsp;<input type='button' onclick='removeNode("   + selectedNode.id + ")'  title='remove'   class='button-remove'><hr/>"
                                                                 + selectedNode.actions;
        }
      }
    else if (params.edges.length == 1) {
      selectedEdge = findObjectByKey(edges, 'id', params.edges[0]);
      if (selectedEdge) { // TBD: should test on cluster
        title = selectedEdge.title.split("<br/>")[0];
        document.getElementById("commands").innerHTML = "<b><u>" + title + "</u></b>" + helpButton
                                                                 + "&nbsp;<input type='button' onclick='describeEdge(\"" + selectedEdge.id + "\")' title='describe' class='button-describe'>"
                                                                 + "&nbsp;<input type='button' onclick='removeEdge(\""   + selectedEdge.id + "\")' title='remove'   class='button-remove'><hr/>"
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
        expand(selectedNode.id);
        }
      }
    });
  }
  
// Expand all Nodes by one level
function expandNodes(level = 1) {
  if (level > 0) {
    for (var i = 0; i < nodes.length; i++) {
      expand(nodes[i].id);
      }
    expandNodes(level - 1);
    }
  }

// Expand one more level
function expand(id) {
  //document.getElementById("feedback").innerHTML += "Expanding " + selectedNode.label + "<br/>";
  if (document.getElementById('removeOld').checked) {
    nodes.length = 0;
    //edges.length = 0;
    nodes.push(selectedNode);
    }
  if (document.getElementById('expandTo').checked) {
    callGremlinGraph(gr + ".V(" + id + ").out()");
     }
  if (document.getElementById('expandFrom').checked) {
    callGremlinGraph(gr + ".V(" + id + ").in()");
    }
  if (document.getElementById('expandTo').checked) {
     callGremlinGraph(gr + ".V(" + id + ").outE()");
    }
  if (document.getElementById('expandFrom').checked) {
     callGremlinGraph(gr + ".V(" + id + ").inE()");
    }
  }
      
// Describe selected node
function describeNode(id) {
  var node = findObjectByKey(nodes, 'id', id);
  //popup(node.title, callGremlinValues("v=" + gr + ".V('" + id + "').next();com.Lomikel.Januser.Wertex.enhance(v).properties().toList().toString().replace(', ', '<br/>').replace(']', '').replace('vp[', '').replace('[', '')") + 
  popup(node.title, callGremlinValues(gr + ".V('" + id + "').valueMap().toList().toString().replace(', ', '<br/>').replace(']', '').replace('vp[', '').replace('[', '')") + 
            "<hr/>" +
            JSON.stringify(node));
  }
    
// Describe selected edge
function describeEdge(id) {
  var edge = findObjectByKey(edges, 'id', id);
  popup(edge.title, callGremlinValues(gr + ".E('" + id + "').valueMap().toList().toString().replace(', ', '<br/>').replace(']', '').replace('[', '')") +
            "<hr/>" +
            JSON.stringify(edge));
  }
    
// Remove selected node
function removeNode(id, type) {
  removeObjectByKey(nodes, 'id', id);
  show(null, null);
  }
  
// Remove selected edge
function removeEdge(id) {
  removeObjectByKey(edges, 'id', id);
  show(null, null);
  }
    
// Apply Filter
function applyFilter() {
  var filter = document.getElementById('filter').value.trim();
  if (filter) {
    filterObjectByKey(nodes, 'label', filter);
    show(null, null);
    }
  }
  
// Cluser by Groups
function clusterByGroups() {
  network.setData(data);
  var clusterOptionsByData;
  for (var i = 0; i < groups.length; i++) {
    clusterGroup(groups[i]);
    }
  }
    
// Cluser Group
function clusterGroup(group) {
  if (group.trim() != '') {
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
      clusterNodeProperties: {id:('cluster:' + group), type:'cluster', borderWidth:3, shape:'star', label:('cluster:' + group), title:('cluster:' + group)}
      };
    network.cluster(clusterOptionsByData);
    }
  }

// Cluste by Hubs
function clusterByHubsize() {
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
  network.setData(data);
  var clusterOptionsByData = {
    joinCondition:function(childOptions) {
      return false;
      },
    };
  network.cluster(clusterOptionsByData);
  }

// Fill Edges
// TBD: limit to visible nodes
function fillEdges() {
  for (var i = 0; i < nodes.length; i++) {
    var selectedNode = nodes[i];
    document.getElementById("feedback").innerHTML += "Expanding " + selectedNode.label + "<br/>";
    if (document.getElementById('expandTo').checked) {
       callGremlinGraph(gr + ".V(" + selectedNode.id + ").outE()");
      }
    if (document.getElementById('expandFrom').checked) {
       callGremlinGraph(gr + ".V(" + selectedNode.id + ").inE()");
      }  
    }
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
  options4Graph.physics.enabled = document.getElementById('physics').checked;
  network.setOptions(options4Graph);
  }
  
// Switch layout on/off
function switchLayout() {
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
function popup(name, txt) {
	w = window.open('', name, 'height=600, width=600, menubar=no, status=no, toolbar=no, titlebar=no');
	var doc = w.document;
	doc.write('<html><title>' + name + "</title><body>");
  doc.write("<b>" + name + "</b>");
	doc.write("<hr/><pre>");
	doc.write(txt);
	doc.write("</pre><hr/>");
  doc.write("<input type='button' value='close' onclick='self.close()'>");
	doc.write('</body></html>');
	doc.close();
	if (window.focus) {
	  w.focus();
	  }
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
  console.log(nam + " " + id + " " + ifEdge + " " + title);
  console.log(eMap);
  console.log(pMap);
  var set = ifEdge ? 'E' : 'V';
  for ([key, value] of pMap.entries()) {
    eval(key + '=' + '"' + value + '"');
    }
  if (nam.gremlin) {
    val = callGremlinValues(gr + '.' + set + '("' + id + '").' + nam.gremlin);
    }
  else if (nam.js) {
    val = eval(nam.js);
    }
  else if (eMap.get(nam)) {
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
  console.log(val);
  return val;
  }

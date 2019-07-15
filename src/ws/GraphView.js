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
var container = document.getElementById('visnetwork');
var network;
  
// filter = document.getElementById('filter').value.trim();  
  
function show(nodesS, edgesS) {
  var ids = [];
  if (nodesS != null && nodesS.trim() != "") {
    //nodes = nodes.concat(JSON.parse(nodesS));
    for (n of nodes) {
      ids.push(n.id);
      }
    for (n of JSON.parse(nodesS)) {
      if (ids.indexOf(n.id) === -1) {
        nodes.push(n);
        ids.push(n.id);
        }
      }
    }
  var fts = [];
  if (edgesS != null && edgesS.trim() != "") {
    //edges = edges.concat(JSON.parse(edgesS));
    for (e of edges) {
      fts.push(e.from + "#" + e.to);
      }
    for (e of JSON.parse(edgesS)) {
      if (fts.indexOf(e.from + "#" + e.to) === -1) {
        edges.push(e);
        fts.push(e.from + "#" + e.to);
        }
      }
    }
  groups = [];
  for (var i = 0; i < nodes.length; i++) {
    nodes[i] = postProcNode(nodes[i]);
    for (g of nodes[i].group.split(" ")) {
      groups.push(g);
      }
    }
  for (var i = 0; i < edges.length; i++) {
    edges[i] = postProcEdge(edges[i]);
    for (g of edges[i].group.split(" ")) {
      groups.push(g);
      }
    }
  data = {
    nodes:nodes,
    edges:edges,
    };
  network = new vis.Network(container, data, options);
  if (nodesS != null || edgesS != null) {
    clusterByGroups();
    }
  network.on("click", function(params) {
    var type;
    if (params.nodes.length == 1) {
      if (network.isCluster(params.nodes[0]) == true) {
        }
      else {
        selectedNode = findObjectByKey(nodes, 'id', params.nodes[0]);
        type = selectedNode.type;
        if (executeNodeAction(selectedNode) != null) {
          eval(executeNodeAction(selectedNode));
          }
        else {
          document.getElementById("commands").innerHTML = "<b><u>" + type + ":</u>" + selectedNode.label + "</b>"
                                                        + "&nbsp;<input type='button' onclick='removeNode(\"" + selectedNode.id + "\", \"" + type + "\")' value='Remove Node'>"
                                                        + "&nbsp;<input type='button' onclick='describe(\""   + selectedNode.id + "\", \"" + type + "\")' value='Describe Node'>"
                                                        + "&nbsp;<input type='button' onclick='expand(\""     + selectedNode.id + "\", \"" + type + "\")' value='Expand Node'><br/>"
                                                        + formNodeAction(selectedNode);
          if (executeNodePostAction(selectedNode) != null) {
            eval(executeNodePostAction(selectedNode));
            }                                                        
          }
        }
      }
    else if (params.edges.length == 1) {
      selectedEdge = findObjectByKey(edges, 'id', params.edges[0]);
      if (executeEdgeAction(selectedEdge) != null) {
        eval(executeEdgeAction(selectedEdge));
        }
      else {
        document.getElementById("commands").innerHTML = "<b>" + selectedEdge.label + "</b>"
                                                      + "&nbsp;<input type='button' onclick='removeEdge(\"" + selectedEdge.id + "\")' value='Remove'>"
                                                      + "&nbsp;<input type='button' onclick='describe(\""   + selectedEdge.id + "\")' value='Describe'><br/>"
                                                      + formEdgeAction(selectedEdge);
        if (executeEdgePostAction(selectedEdge) != null) {
          eval(executeEdgePostAction(selectedEdge));
          }                                                        
        }
      }
    });
  network.on("doubleClick", function(params) {
    if (params.nodes.length == 1) {
      // is cluster
      if (network.isCluster(params.nodes[0]) == true) {
        network.openCluster(params.nodes[0]);
        }
      // is not cluster
      else {
        selectedNode = findObjectByKey(nodes, 'id', params.nodes[0]);
        for (g of selectedNode.group.split(" ")) {
          clusterGroup(g);
          }
        }
      }
    });
  network.once('initRedraw', function() {
    if (lastClusterZoomLevel === 0) {
      lastClusterZoomLevel = network.getScale();
      }
    });
  network.on('zoom', function (params) {
    if (document.getElementById('zoom').checked && params.direction == '-') {
      if (params.scale < lastClusterZoomLevel*clusterFactor) {
        makeClusters(params.scale);
        lastClusterZoomLevel = params.scale;
        }
      }
    else {
      openClusters(params.scale);
      }
    });
  }
  
// Expand from database
function expand(id, type) {
  var selectedNode = findObjectByKey(nodes, 'id', id);
  document.getElementById("feedback").innerHTML = "Expanding " + selectedNode.label;
  if (document.getElementById('removeOld').checked) {
    nodes.length = 0;
    edges.length = 0;
    nodes.push(selectedNode);
    }
  eval(expandNode(type, id));
  show(null, null);
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
  
// Describe selected node or edge
function describe(id, type) {
  var selectedNode = findObjectByKey(nodes, 'id', id);
  var txt = JSON.stringify(selectedNode, null, 2) + "<hr/>" + callInfo(type, id);
  popup(id, txt);
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
      clusterNodeProperties: postProcNode({id:('cluster:' + group), type:'cluster', borderWidth:3, shape:'star', label:('cluster:' + group), title:('cluster:' + group)})
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
      clusterOptions.font = {size: childrenCount*5+30}
      clusterOptions.id = 'cluster:' + clusterIndex;
      clusters.push({id:'cluster:' + clusterIndex, scale:scale});
      return clusterOptions;
      },
    clusterNodeProperties: {borderWidth: 3, shape: 'database', font: {size: 30}}
    }
  network.clusterOutliers(clusterOptionsByData);
  if (document.getElementById('stabilize').checked === true) {
    // since we use the scale as a unique identifier, we do NOT want to fit after the stabilization
    network.setOptions({physics:{stabilization:{fit: false}}});
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
  options.physics.enabled = document.getElementById('physics').checked;
  network.setOptions(options);
  }
  
// Switch layout on/off
function switchLayout() {
  if (document.getElementById('layout').checked) {
    var direction = "LR";
    if (document.getElementById('layout_direction').checked) {
      direction = "UD";
      }
    var method = "directed";
    if (document.getElementById('layout_method').checked) {
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
  
// Open popup window
function popup(name, txt) {
	w = window.open('', name, 'height=600, width=600, menubar=no, status=no, toolbar=no, titlebar=no');
	var doc = w.document;
	doc.write('<html><title>' + name + "</title><body>");
  doc.write("<h1>" + name + "</h1>");
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
  

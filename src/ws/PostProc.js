// Post Process (example)
// ============

// Post-process Node to be shown
function postProcNode(node) {
  switch (node.type) {
    case "/":
      node.shape = "image";
      node.image = "images/JHTools.png";
      break;
    default:
      break;     
    }
  return node;
  }
  
// Post-process Edge to be shown
function postProcEdge(edge) {
  switch (edge.label) {
    default:
      break;     
    }
  return edge;
  }
  

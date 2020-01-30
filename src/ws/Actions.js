// Form Actions (example)
// ============
  
// Create menu for Node
// (not used if executeNodeAction != null)
function formNodeAction(node) {
  var html = "";
  switch (node.type) {
    case "/":
      html += "<a class='button' href='CommandCenter.jsp' target='COMMAND'>Reload</a>";
      break;
    default:
      break;     
    }
  return html;
  }
  
// Create menu for Edsge
// (not used if executeEdgeAction != null)
function formEdgeAction(edge) {
  var html = "";
  return html;
  }
 
// Execute action for Node
function executeNodeAction(node) {
  }
  
// Execute action for Edge
function executeEdgeAction(edge) {
  }
  
// Execute action for Node after menu is created
function executeNodePostAction(node) {
  }
  
// Execute action for Edge after menu is created
function executeEdgePostAction(edge) {
  }
 
// Request new data for an element and 'show(...)' them
function expandNode(element, id) {
  }
  

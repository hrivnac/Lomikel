// Cookies Management (example)
// ==================

// Set a Cookie
function setCookie(cookie, exdays) {
  var d = new Date();
  d.setTime(d.getTime() + (exdays*24*60*60*1000));
  var expires = "expires=" + d.toUTCString();
  document.cookie = cookie + ";" + expires;
  }

// Load Cookies into nodes and edges 
function loadCookies() {
  var cs = document.cookie.split(';');
  var node;
  for (var i = 0; i < cs.length; i++) {
    if (cs[i].trim() != "") {
      node = JSON.parse(unescape(cs[i].trim().split("=")[1].trim()));
      removeObjectByKey(nodes, "id", node.id);
      removeObjectByKey(edges, "to", node.id);
      nodes.push(node);
      edges.push({"from":"From Cookies",
                  "to":node.id,
                  "label":" ",
                  "title":" ",
                  "group":" ",
                  "color":" ",
                  "arrows":"to",
                  "value":0});
      }
    }
  }

async function loadPane(pane, url, iframe, height) {
  while (!document.getElementById(pane)) {
    await sleep(1000);
    }
  if (document.getElementById("feedback")) {
    document.getElementById("feedback").innerHTML += "Loading " + pane + " : " + url + "<br/>"
    }
  url = encodeURI(url);
  if (!height) {
    height = "100%";
    }
  if (iframe) {
    document.getElementById(pane).innerHTML='<iframe height="' + height + '" width="100%" src="' + url + '">';
    }
  else {
    $("#" + pane).load(url);
    }
  if (pane == 'graph' || pane == 'image' || pane == 'plot') {
    showTab(pane);
    w2ui['layoutLeft']['panels'][2]['tabs'].click(pane.concat("Tab"));
    }
  }        

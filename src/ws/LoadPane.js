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
    //document.getElementById(pane).innerHTML='<iframe height="' + height + '" width="100%" src="' + url + '">';
    document.getElementById(pane).innerHTML='<iframe src="' + url + '">';
    }
  else {
    $("#" + pane).load(url);
    }
  if (pane == 'result' || pane == 'table' || pane == 'image' || pane == 'plot' || pane == 'skyview') {
    showTab(pane);
    w2ui['layoutMain']['panels'][0]['tabs'].click(pane.concat("Tab"));
    }
  }        

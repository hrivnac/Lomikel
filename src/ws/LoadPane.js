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
    console.log("iframe");
    //document.getElementById(pane).innerHTML='<iframe height="' + height + '" width="100%" src="' + url + '">';
    document.getElementById(pane).innerHTML='<iframe name="dynamicFrame" height="' + height + '" width="100%">';
    postUrlToIframe(
    url,
    "dynamicFrame"
      );
    }
  else {
    console.log("load");
    $("#" + pane).load(url);
    }
  if (pane == 'result' || pane == 'table' || pane == 'image' || pane == 'plot' || pane == 'skyview') {
    showTab(pane);
    w2ui['layoutMain']['panels'][0]['tabs'].click(pane.concat("Tab"));
    }
  }        

function postUrlToIframe(fullUrl, iframeName) {
    // Resolve relative URLs safely
    const url = new URL(fullUrl, document.baseURI);

    const action = url.pathname; // keeps it relative
    const form = document.createElement("form");
    form.method = "POST";
    form.action = action;
    form.target = iframeName;
    form.style.display = "none";

    // Add query params as hidden inputs
    url.searchParams.forEach((value, key) => {
        const input = document.createElement("input");
        input.type = "hidden";
        input.name = key;
        input.value = value;
        form.appendChild(input);
    });

    document.body.appendChild(form);
    form.submit();
    document.body.removeChild(form);
}

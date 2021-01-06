// Additions to the default menu

function modifyMenu(hform) {
  hform.fields.push({field:'latest', type: 'text', html: {caption: '* Latest', text : ' (show latest objects, time in minutes before now)' , attr: 'style="width: 100px"'}});
  hform.record.latest = '';
  }
  
function modifyRequest(hform) {
  return "&latest=" + hform.record.latest;
  }
  
// New search from the 'latests' alerts page
  
function searchDetails(url) {
  var dd = document.getElementsByClassName('details');
  var keys = "";
  for (var i = 0; i < dd.length; i++) {
    if (dd[i].checked) {
      keys += "key:key:" + dd[i].value + ",";
      }
    }
  if (keys === "") {
    for (var i = 0; i < dd.length; i++) {
      keys +=  "key:key:" + dd[i].value + ",";
      }
    }
  loadPane("result", url + "&filters=" + keys);  
  }
  
// New ranges search
  
function searchRanges(url) {
  var ramin  = $("#slider-ra"  ).slider("values", 0);
  var ramax  = $("#slider-ra"  ).slider("values", 1);
  var decmin = $("#slider-dec" ).slider("values", 0);
  var decmax = $("#slider-dec" ).slider("values", 1);
  var ra0    = $("#slider-ra0" ).slider("value");
  var dec0   = $("#slider-dec0").slider("value");
  var del    = $("#slider-del" ).slider("value");
  var ff  = document.getElementById('ffselector').value;
  var oc  = document.getElementById('othercol').value;
  del = del / 3600.0;
  if (ff != "") {
    formula     = ff;
    formulaArgs = "";
    }
  else if (del > 0) {
    formula     = "isNear(" + ra0 + "," + dec0 + "," + del + ")";
    formulaArgs = "ra,dec";
    }
  else {
    formula     = "isWithinGeoLimits(" + ramin + "," + ramax + "," + decmin + "," + decmax + ")";
    formulaArgs = "ra,dec";
    }
  if (oc != "") {
    formulaArgs += "," + oc;
    }
  loadPane("result", url + "&formula=" + formula + "&formulaArgs=" + formulaArgs); 
  }

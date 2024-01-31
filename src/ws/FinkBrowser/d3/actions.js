function actions(url, key) {
  
  if (url == "") {
    url = "http://134.158.75.151:24000";
    }
  
  objectId = '';
  for (w of key.split(' ')) {
    ww = w.split('=');
    if (ww[0] == 'objectId') {
      objectId = ww[1];
      }
    }
    
  if (objectId != "") {
    return "<a href='" + url + "/" + objectId + "' title='analyse with Fink Science Portal' target='_blank'>Analyse&#8599;</a>";
    }
  else {
    return "<a href='" + "' title='go to Fink Science Portal' target='_blank'>Fink&#8599;</a>";
    }
  }

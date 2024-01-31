function actions(url, key) {
  
  console.log(url);
  for w of key.split(' ')) {
    ww = w.split('=');
    if (ww[0] == 'objectId') {
      objectId = ww[1];
      }
    }
    
  if (objectId) {
    return "<a href='http://134.158.75.151:24000/" + key + "' title='analyse with Fink Science Portal' target='_blank'>Analyse&#8599;</a>";
    }
  else {
    return "xxx";
    }
  }

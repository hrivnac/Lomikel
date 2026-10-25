// Return the existing Plot action without interpolating raw values into HTML or JS.
function actions(url, key) {
  if (!url) return '';
  var target = String(url) + '&key=' + String(key == null ? '' : key);
  var handler = 'loadPane("result", ' + JSON.stringify(target) + '); return false;';
  var attribute = handler.replace(/[&<>"']/g, function (character) {
    return {'&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;'}[character];
  });
  return '<a href="#" onclick="' + attribute + '">nop</a>';
}

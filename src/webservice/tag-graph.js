/* "Tag" selection action: register the selected object vertex in the TAG OCol "GraphBrowser".
 *
 * The write runs on the Gremlin server the vertex was loaded from (its JanusGraph database), so a
 * ZTF object is tagged in the ZTF graph and an LSST object in the LSST graph. The script is built only
 * from a validated object ID, a fixed OCol name and a validated server, never from graph text. */
(function () {
  'use strict';
  var TAG_NAME = 'GraphBrowser';
  var OBJECT_ID = /^(?:ZTF[A-Za-z0-9_-]{1,80}|[0-9]{1,24})$/;
  var SERVER = /^(?:https?:)?\/\/[A-Za-z0-9.-]+(?::\d{1,5})?\/?$/;
  var TIMEOUT_MS = 60000;
  var CLASSIFIER = 'com.astrolabsoftware.FinkBrowser.Januser.Classifier';
  var busy = false;

  function say(message) {
    if (typeof window.feedbackLine === 'function') window.feedbackLine(message);
  }
  function url(server, script) {
    return server + (server.indexOf('?') >= 0 ? '&' : '?') + 'gremlin=' + encodeURIComponent(script);
  }
  // `Classifier` is not imported on the Gremlin servers, so the class name is fully qualified.
  // Classifier.Survey has only ZTF and LSST; either gives the same survey-independent TAG classifier.
  function registerScript(objectId) {
    var survey = objectId.indexOf('ZTF') === 0 ? 'ZTF' : 'LSST';
    return 'gr.registerOCol(' + CLASSIFIER + ".instance('TAG', '" + survey + "', ''), '" + TAG_NAME +
      "', '" + objectId + "', 1.0, '', '')";
  }
  function verifyScript(objectId) {
    return "g.V().has('lbl', 'OCol').has('classifier', 'TAG').has('cls', '" + TAG_NAME +
      "').out('deepcontains').has('lbl', 'object').has('objectId', '" + objectId + "').count()";
  }
  async function send(server, script, signal) {
    var response = await fetch(url(server, script), {cache: 'no-store', headers: {accept: 'application/json'}, signal: signal});
    var body = null;
    try { body = await response.json(); } catch (error) { body = null; }
    if (!response.ok || !body || !body.status || body.status.code !== 200) {
      var reason = body && typeof body.message === 'string' ? body.message : 'HTTP ' + response.status;
      throw new Error(reason.replace(/[^\x20-\x7e]/g, ' ').trim().slice(0, 200));
    }
    return body;
  }
  function count(body) {
    var data = body && body.result && body.result.data;
    var values = data && Array.isArray(data['@value']) ? data['@value'] : Array.isArray(data) ? data : [];
    var first = values[0];
    var number = first && typeof first === 'object' ? first['@value'] : first;
    return Number(number);
  }

  async function run() {
    var selected = window.selectedNode;
    var live = selected && Array.isArray(window.nodes) && window.nodes.some(function (node) { return node === selected; });
    var objectId = live && window.skyGraphState && window.skyGraphState.getId(selected.id);
    if (!objectId || !OBJECT_ID.test(objectId)) {
      say('Tag: select a live ZTF or LSST object vertex.');
      return false;
    }
    var server = typeof window.graphTableServerForVertex === 'function' ? window.graphTableServerForVertex(selected.id) : null;
    if (typeof server !== 'string' || !SERVER.test(server)) {
      say('Tag: the graph server of ' + objectId + ' is unknown; nothing was written.');
      return false;
    }
    if (busy) {
      say('Tag: another tagging request is still running.');
      return false;
    }
    if (typeof window.confirm === 'function' &&
        !window.confirm('Tag object ' + objectId + ' as "' + TAG_NAME + '" in the graph database at ' + server + '?')) {
      say('Tag: cancelled; nothing was written.');
      return false;
    }
    busy = true;
    var controller = typeof AbortController === 'function' ? new AbortController() : null;
    var timer = controller ? setTimeout(function () { controller.abort(); }, TIMEOUT_MS) : null;
    var signal = controller ? controller.signal : undefined;
    try {
      say('Tag: registering ' + objectId + ' as "' + TAG_NAME + '" at ' + server + ' …');
      await send(server, registerScript(objectId), signal);
      var members = count(await send(server, verifyScript(objectId), signal));
      if (members === 1) say('Tag: ' + objectId + ' is now tagged "' + TAG_NAME + '" (verified in ' + server + ').');
      else say('Tag: the request succeeded but ' + objectId + ' was not found in "' + TAG_NAME + '" afterwards; please check the graph.');
      return members === 1;
    } catch (error) {
      say('Tag failed for ' + objectId + ': ' + (error && error.name === 'AbortError' ? 'timed out' : (error && error.message) || 'unknown error') +
          '. The tag may or may not have been written; repeating it is safe.');
      return false;
    } finally {
      if (timer !== null) clearTimeout(timer);
      busy = false;
    }
  }

  window.tagGraphState = {run: run, registerScript: registerScript, verifyScript: verifyScript};
})();

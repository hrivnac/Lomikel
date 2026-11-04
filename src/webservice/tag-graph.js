/* "Tag" selection action: register the selected object vertex in the TAG OCol "GraphBrowser".
 *
 * The Gremlin servers behind the graph are read-only, so the write is requested from the WAR's own
 * TagObject.jsp, which owns the writable JanusGraph clients (LSST -> CC graph, ZTF -> IJCLab graph) and
 * calls gr.registerOCol(Classifier.instance('TAG', survey, ''), 'GraphBrowser', oid, 1.0, '', '').
 * Only a validated object ID is ever sent; the page decides the database from the ID. */
(function () {
  'use strict';
  var TAG_NAME = 'GraphBrowser';
  var OBJECT_ID = /^(?:ZTF[A-Za-z0-9_-]{1,80}|[0-9]{1,24})$/;
  var ENDPOINT = 'TagObject.jsp';
  var TIMEOUT_MS = 90000;
  var busy = false;

  function say(message) {
    if (typeof window.feedbackLine === 'function') window.feedbackLine(message);
  }
  function surveyOf(objectId) { return objectId.indexOf('ZTF') === 0 ? 'ZTF' : 'LSST'; }

  async function run() {
    var selected = window.selectedNode;
    var live = selected && Array.isArray(window.nodes) && window.nodes.some(function (node) { return node === selected; });
    var objectId = live && window.skyGraphState && window.skyGraphState.getId(selected.id);
    if (!objectId || !OBJECT_ID.test(objectId)) {
      say('Tag: select a live ZTF or LSST object vertex.');
      return false;
    }
    if (busy) {
      say('Tag: another tagging request is still running.');
      return false;
    }
    var survey = surveyOf(objectId);
    if (typeof window.confirm === 'function' &&
        !window.confirm('Tag object ' + objectId + ' as "' + TAG_NAME + '" in the ' + survey + ' graph database?')) {
      say('Tag: cancelled; nothing was written.');
      return false;
    }
    busy = true;
    var controller = typeof AbortController === 'function' ? new AbortController() : null;
    var timer = controller ? setTimeout(function () { controller.abort(); }, TIMEOUT_MS) : null;
    try {
      say('Tag: registering ' + objectId + ' as "' + TAG_NAME + '" in the ' + survey + ' graph …');
      var response = await fetch(ENDPOINT, {
        method: 'POST',
        headers: {'Content-Type': 'application/json', 'X-Fink-Tag': '1'},
        body: JSON.stringify({objectId: objectId}),
        credentials: 'same-origin',
        signal: controller ? controller.signal : undefined
      });
      var body = null;
      try { body = await response.json(); } catch (error) { body = null; }
      if (!response.ok || !body || body.ok !== true) {
        var reason = body && typeof body.error === 'string' ? body.error : 'HTTP ' + response.status;
        throw new Error(reason.replace(/[^\x20-\x7e]/g, ' ').trim().slice(0, 200));
      }
      if (body.objectId !== objectId || body.tag !== TAG_NAME) throw new Error('Unexpected tag response');
      say('Tag: ' + objectId + (body.alreadyTagged ? ' was already tagged "' : ' is now tagged "') + TAG_NAME + '" in the ' +
          body.survey + ' graph (verified). A read-only graph server may need a few minutes to show it.');
      return true;
    } catch (error) {
      say('Tag failed for ' + objectId + ': ' + (error && error.name === 'AbortError' ? 'timed out' : (error && error.message) || 'unknown error') +
          '. The tag may or may not have been written; repeating it is safe.');
      return false;
    } finally {
      if (timer !== null) clearTimeout(timer);
      busy = false;
    }
  }

  window.tagGraphState = {run: run};
})();

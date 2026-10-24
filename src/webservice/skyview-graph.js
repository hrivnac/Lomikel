/* Track the actual object properties while reusing the legacy vis graph controller. */
(function () {
  'use strict';
  var objectIds = new Map(); // graph vertex ID -> Fink object ID
  var sequence = 0;
  var activeRequest;
  var oldShow = window.show;
  var oldDispose = window.disposeGraphView;
  var MAX_OBJECTS = 500;
  function cancel() {
    sequence++;
    if (activeRequest) activeRequest.abort();
    activeRequest = null;
  }

  function getIds() {
    var distinct = new Set();
    // The graph's backing nodes include objects inside collapsed vis clusters.
    window.nodes.forEach(function (node) {
      var id = objectIds.get(node.id);
      if (id) distinct.add(id);
    });
    return Array.from(distinct);
  }
  window.show = function (graph) {
    if (Array.isArray(graph)) graph.forEach(function (item) {
      if (item.type !== 'vertex') return;
      if (item.label !== 'object') { objectIds.delete(item.id); return; }
      var field = (item.element || []).find(function (entry) { return entry.key === 'objectId'; });
      var objectId = field && field.value != null ? String(field.value).trim() : '';
      if (objectId) objectIds.set(item.id, objectId);
    });
    var result = oldShow.apply(this, arguments);
    var live = new Set(window.nodes.map(function (node) { return node.id; }));
    objectIds.forEach(function (_, vertexId) { if (!live.has(vertexId)) objectIds.delete(vertexId); });
    return result;
  };
  window.disposeGraphView = function () {
    cancel();
    objectIds.clear();
    return oldDispose.apply(this, arguments);
  };

  function showStatus(text) {
    var pane = document.getElementById('skyview');
    if (pane) pane.textContent = text;
    showTab('skyview');
    w2ui.layoutMain.panels[0].tabs.click('skyviewTab');
  }
  function plot(points, requested, missing) {
    // A private frame name avoids the legacy shared dynamicFrame used by Plot.
    // Submit data in the POST body instead of putting thousands of positions in
    // the activity log and URL through the generic loadPane helper.
    var frame = document.createElement('iframe');
    frame.name = 'skyGraphFrame';
    frame.title = 'Sky map of objects';
    frame.width = '100%';
    frame.height = '100%';
    document.getElementById('skyview').replaceChildren(frame);
    var form = document.createElement('form');
    form.hidden = true;
    form.method = 'POST';
    form.action = new URL('webservice/d3/skyview.jsp', document.baseURI).pathname;
    form.target = frame.name;
    [['points', JSON.stringify(points)], ['requested', String(requested)],
     ['missing', String(missing)]].forEach(function (pair) {
      var field = document.createElement('input');
      field.name = pair[0];
      field.value = pair[1];
      form.appendChild(field);
    });
    document.body.appendChild(form);
    form.submit();
    document.body.removeChild(form);
    showTab('skyview');
    w2ui.layoutMain.panels[0].tabs.click('skyviewTab');
  }
  async function openIds(objectIdsToPlot, source) {
    var current = ++sequence;
    if (activeRequest) activeRequest.abort();
    var ids = Array.from(new Set(objectIdsToPlot));
    if (!ids.length) { showStatus('No objects are in the current ' + source + '.'); return; }
    if (ids.length > MAX_OBJECTS) {
      showStatus('The ' + source + ' has more than ' + MAX_OBJECTS + ' objects. Reduce it before plotting all objects.');
      return;
    }
    showStatus('Looking up sky positions for ' + ids.length + ' objects…');
    var controller = new AbortController();
    activeRequest = controller;
    try {
      var response = await fetch('webservice/SkyPositions.jsp', {
        method: 'POST', credentials: 'same-origin', signal: controller.signal,
        headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ids: ids})
      });
      if (!response.ok) throw new Error('coordinate lookup failed (HTTP ' + response.status + ')');
      var payload = await response.json();
      if (current !== sequence || controller.signal.aborted) return;
      if (!payload || !Array.isArray(payload.points) || !Array.isArray(payload.missing)) {
        throw new Error('invalid coordinate response');
      }
      var wanted = new Set(ids);
      if (!payload.points.every(function (p) {
        return p && wanted.has(p.objectId) && Number.isFinite(p.ra) && p.ra >= 0 && p.ra < 360 &&
               Number.isFinite(p.dec) && p.dec >= -90 && p.dec <= 90;
      })) throw new Error('invalid sky coordinates');
      plot(payload.points, ids.length, payload.missing.length);
    } catch (error) {
      if (current === sequence && !controller.signal.aborted) showStatus('SkyView: ' + error.message);
    } finally {
      if (activeRequest === controller) activeRequest = null;
    }
  }
  function open() { return openIds(getIds(), 'graph'); }
  window.skyGraphState = {getIds: getIds, open: open, cancel: cancel, openIds: function (ids) {
    return openIds(ids, 'table');
  }};
})();

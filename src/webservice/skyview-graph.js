/* Track the actual object properties while reusing the legacy vis graph controller. */
(function () {
  'use strict';
  var objectIds = new Map(); // graph vertex ID -> Fink object ID
  var sequence = 0;
  var activeRequest;
  var oldShow = window.show;
  var oldDispose = window.disposeGraphView;
  var MAX_OBJECTS = 500;
  var MAX_SS_OBJECTS = 20; // A history fetch makes several bounded upstream calls.
  var MAX_POINTS = 12000;
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
  function getId(vertexId) {
    return window.nodes.some(function (node) { return node.id === vertexId; }) ?
      objectIds.get(vertexId) || null : null;
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
  function plot(points, requested, missing, trajectories) {
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
    form.action = new URL('d3/skyview.jsp', document.baseURI).pathname;
    form.target = frame.name;
    [['points', JSON.stringify(points)], ['trajectories', JSON.stringify(trajectories)], ['requested', String(requested)],
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
  function logExecuted(requests, ids) {
    if (typeof window.activityLogQuery !== 'function' || !Array.isArray(requests) || requests.length > 3) return;
    var allowed = new Set(ids);
    var entries = [];
    try {
      requests.forEach(function (request) {
        if (!request || !['/radec/_mget', '/dia_radec/_mget', '/ss_radec/_mget'].includes(request.path) ||
            typeof request.body !== 'string' || request.body.length > 100000) throw new Error('invalid activity');
        var body = JSON.parse(request.body);
        if (!body || Object.keys(body).length !== 1 || !Array.isArray(body.docs) ||
            !body.docs.length || body.docs.length > 500 || !body.docs.every(function (doc) {
              return doc && Object.keys(doc).length === 2 && typeof doc._id === 'string' &&
                allowed.has(doc._id) && (request.path === '/radec/_mget') === doc._id.startsWith('ZTF') &&
                (request.path === '/ss_radec/_mget' ? doc._source === false :
                  Array.isArray(doc._source) && doc._source.length === 1 && doc._source[0] === 'location');
            })) throw new Error('invalid activity');
        entries.push(request.path + ' ' + request.body);
      });
    } catch (error) { return; } // Untrusted metadata must not change plotting or reveal arbitrary fields.
    entries.forEach(function (entry) { window.activityLogQuery('ES', entry); });
  }
  function logHistoryEs(requests, id) {
    if (typeof window.activityLogQuery !== 'function' || !Array.isArray(requests) || requests.length > 2) return;
    var entries = [];
    try {
      requests.forEach(function (request) {
        if (!request || !['/ss_mjd/_mget','/ss_radec/_mget'].includes(request.path) ||
            typeof request.body !== 'string' || request.body.length > 512) throw new Error('invalid activity');
        var body = JSON.parse(request.body);
        if (!body || Object.keys(body).length !== 1 || !Array.isArray(body.ids) ||
            body.ids.length !== 1 || body.ids[0] !== id) throw new Error('invalid activity');
        entries.push(request.path + ' ' + request.body);
      });
    } catch (error) { return; }
    entries.forEach(function (entry) { window.activityLogQuery('ES', entry); });
  }
  function logFink(requests, id) {
    if (typeof window.activityLogQuery !== 'function' || !Array.isArray(requests) || requests.length > 2) return;
    var entries = [];
    try {
      requests.forEach(function (request) {
        if (!request || !['/api/v1/resolver', '/api/v1/sso'].includes(request.path) ||
            typeof request.body !== 'string' || request.body.length > 1024) throw new Error('invalid activity');
        var body = JSON.parse(request.body);
        if (!body || body['output-format'] !== 'json' ||
            (request.path === '/api/v1/resolver' &&
              (body.name_or_id !== id || body.resolver !== 'ssodnet' ||
               body.reverse !== true || body.nmax !== 10 ||
               Object.keys(body).some(function (key) {
                 return !['resolver','name_or_id','reverse','nmax','output-format'].includes(key);
               }))) ||
            (request.path === '/api/v1/sso' &&
              (typeof body.n_or_d !== 'string' || !/^[^\u0000-\u001f\u007f]{1,128}$/.test(body.n_or_d) ||
               body.columns !== 'r:diaSourceId,r:ssObjectId,r:midpointMjdTai,r:ra,r:dec' ||
               Object.keys(body).some(function (key) {
                 return !['n_or_d','columns','output-format'].includes(key);
               })))) throw new Error('invalid activity');
        entries.push('https://api.lsst.fink-portal.org' + request.path + ' ' + request.body);
      });
    } catch (error) { return; }
    entries.forEach(function (entry) { window.activityLogQuery('Fink', entry); });
  }
  function normalizeTrajectory(payload, id) {
    if (!payload || payload.objectId !== id || payload.esCoverageComplete !== true ||
        !Array.isArray(payload.sources) || !payload.sources.length ||
        payload.sources.length > MAX_POINTS) throw new Error('SS source history could not be verified');
    var seen = new Set();
    var sources = payload.sources.map(function (source) {
      if (!source || typeof source.sourceId !== 'string' || !/^\d{1,64}$/.test(source.sourceId) ||
          seen.has(source.sourceId) || !Number.isFinite(source.mjd) || source.mjd <= 0 ||
          !Number.isFinite(source.ra) || source.ra < 0 || source.ra > 360 ||
          !Number.isFinite(source.dec) || source.dec < -90 || source.dec > 90)
        throw new Error('Invalid SS source history');
      seen.add(source.sourceId);
      return {sourceId:source.sourceId, mjd:source.mjd, ra:source.ra % 360, dec:source.dec};
    });
    sources.sort(function (a, b) { return a.mjd - b.mjd || a.sourceId.localeCompare(b.sourceId); });
    var trajectory = {objectId:id, sources:sources};
    if (payload.esMjdAvailable === false) trajectory.esMjdAvailable = false;
    return trajectory;
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
      var response = await fetch('SkyPositions.jsp', {
        method: 'POST', credentials: 'same-origin', signal: controller.signal,
        headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ids: ids})
      });
      if (!response.ok) {
        // A proxy failure may still contain requests that reached Elasticsearch.
        try {
          var failed = await response.json();
          if (current === sequence && !controller.signal.aborted) logExecuted(failed.esRequests, ids);
        } catch (_error) { /* Keep the original HTTP error for non-JSON responses. */ }
        throw new Error('coordinate lookup failed (HTTP ' + response.status + ')');
      }
      var payload = await response.json();
      if (current !== sequence || controller.signal.aborted) return;
      logExecuted(payload && payload.esRequests, ids);
      if (!payload || !Array.isArray(payload.points) || !Array.isArray(payload.missing)) {
        throw new Error('invalid coordinate response');
      }
      var wanted = new Set(ids);
      if (!payload.points.every(function (p) {
        return p && wanted.has(p.objectId) && Number.isFinite(p.ra) && p.ra >= 0 && p.ra < 360 &&
               Number.isFinite(p.dec) && p.dec >= -90 && p.dec <= 90;
      })) throw new Error('invalid sky coordinates');
      var ssIds = payload.ssObjectIds;
      if (!Array.isArray(ssIds) || new Set(ssIds).size !== ssIds.length ||
          !ssIds.every(function (id) { return typeof id === 'string' && /^\d{1,24}$/.test(id) && wanted.has(id); }))
        throw new Error('invalid Solar System object list');
      if (ssIds.length > MAX_SS_OBJECTS)
        throw new Error('More than ' + MAX_SS_OBJECTS + ' SS objects; reduce the graph or table to show complete trajectories.');
      var next = 0, trajectories = [];
      var workers = Array.from({length:Math.min(3, ssIds.length)}, async function () {
        while (next < ssIds.length) {
          var id = ssIds[next++];
          var answer = await fetch('AlertsView/SSTrajectory.jsp?id=' + encodeURIComponent(id),
            {credentials:'same-origin', cache:'no-store', signal:controller.signal});
          var history = await answer.json();
          if (current !== sequence || controller.signal.aborted) return;
          logHistoryEs(history && history.esRequests, id);
          logFink(history && history.finkRequests, id);
          if (!answer.ok) throw new Error('SS source history unavailable (HTTP ' + answer.status + ')');
          trajectories.push(normalizeTrajectory(history, id));
        }
      });
      try { await Promise.all(workers); }
      catch (error) {
        if (current === sequence && !controller.signal.aborted) {
          controller.abort();
          showStatus('SkyView: ' + error.message);
        }
        return;
      }
      if (current !== sequence || controller.signal.aborted) return;
      trajectories.sort(function (a,b) { return ssIds.indexOf(a.objectId) - ssIds.indexOf(b.objectId); });
      var ssSet = new Set(ssIds);
      var staticPoints = payload.points.filter(function (p) { return !ssSet.has(p.objectId); });
      var total = staticPoints.length + trajectories.reduce(function (n,t) { return n + t.sources.length; }, 0);
      if (total > MAX_POINTS) throw new Error('Too many positions for the complete SkyView map');
      plot(staticPoints, ids.length, payload.missing.length, trajectories);
    } catch (error) {
      if (current === sequence && !controller.signal.aborted) showStatus('SkyView: ' + error.message);
    } finally {
      if (activeRequest === controller) activeRequest = null;
    }
  }
  function open() { return openIds(getIds(), 'graph'); }
  window.skyGraphState = {getIds: getIds, getId: getId, open: open, cancel: cancel, openIds: function (ids) {
    return openIds(ids, 'table');
  }};
})();

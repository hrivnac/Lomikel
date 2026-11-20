/* Fink Portal -> Graph Browser hand-off (version 1).
 *
 * A link such as index.jsp?profile=cc&handoff=v1&kind=dia&objectId=<id> opens one
 * LSST object, together with every OCol (classification collection) pointing to
 * it, instead of the profile's default graph. The URL carries only a validated
 * kind and decimal object ID: the graph server comes from the WAR's own server
 * list (data-survey="LSST") and the
 * Gremlin text is built here from fixed templates. Invalid links are reported and
 * leave the ordinary workspace start unchanged; ordinary visits are not affected.
 */
(function (root) {
  'use strict';
  var VERSION = 'v1';
  var ALLOWED = ['handoff', 'kind', 'objectId', 'profile', 'style'];
  var KINDS = {dia: 'DIA', ss: 'Solar System'};
  var CONTROL_WAIT_MS = 60000;    // Top.jsp normally loads within a second
  var DEFAULT_PORTAL = 'https://lsst.fink-portal.org';

  // The Portal that sent the object (its origin arrives as the referrer). Only the
  // public Portal or a loopback development Portal is accepted; anything else keeps
  // the default. Must match the portal check in PortalLink.jsp.
  function portalOrigin(referrer) {
    if (!referrer) return null;
    var url;
    try { url = new URL(referrer); } catch (error) { return null; }
    if (url.username || url.password) return null;
    if (url.origin === DEFAULT_PORTAL) return url.origin;
    if (!/^https?:$/.test(url.protocol)) return null;
    return ['127.0.0.1', 'localhost', '[::1]'].indexOf(url.hostname) >= 0 ? url.origin : null;
  }

  function portalBase() {
    return (root.finkPortalOrigin || DEFAULT_PORTAL) + '/';
  }

  function vertexStep(id) {
    return "g.V().has('lbl', 'object').has('objectId', '" + id + "')";
  }

  // Returns null for an ordinary visit, {error} for a rejected link, or the request.
  function parseFinkHandoff(search) {
    var params = new URLSearchParams(search || '');
    if (!params.has('handoff') && !params.has('kind') && !params.has('objectId')) return null;
    var unsupported = null;
    params.forEach(function (value, key) {
      if (unsupported === null && ALLOWED.indexOf(key) < 0) unsupported = key;
    });
    if (unsupported !== null) {
      return {error: 'unsupported parameter "' + unsupported.slice(0, 40) + '"'};
    }
    var handoff = params.getAll('handoff');
    if (handoff.length !== 1 || handoff[0] !== VERSION) {
      return {error: 'missing or unsupported hand-off version (expected handoff=' + VERSION + ')'};
    }
    var kind = params.getAll('kind');
    if (kind.length !== 1 || !Object.prototype.hasOwnProperty.call(KINDS, kind[0])) {
      return {error: 'kind must be given once as dia or ss'};
    }
    var objectId = params.getAll('objectId');
    // Decimal LSST IDs (at most 19 digits, the signed 64-bit range), kept as strings.
    if (objectId.length !== 1 || !/^[1-9][0-9]{0,18}$/.test(objectId[0])) {
      return {error: 'objectId must be given once as a decimal LSST object ID'};
    }
    var id = objectId[0];
    return {
      kind: kind[0],
      objectId: id,
      exists: vertexStep(id) + '.count()',
      // The object itself plus all OCol vertices pointing to it, each OCol
      // vertex followed by its edge to the object.
      graph: vertexStep(id) + ".limit(1).union(identity(), " +
             "inE().where(outV().has('lbl', 'OCol')).union(outV(), identity()))"
    };
  }

  function log(message) {
    if (typeof root.activityLogMessage === 'function') root.activityLogMessage(message);
    else if (typeof root.feedback === 'function') root.feedback(message);
  }

  // Replace the Selection actions text with plain text and an optional safe link.
  function notice(text, link) {
    var pane = root.document.getElementById('commands');
    log(text);
    if (!pane) return;
    var kids = [root.document.createTextNode(text)];
    if (link) {
      var anchor = root.document.createElement('a');
      anchor.href = link.href;
      anchor.target = '_blank';
      anchor.rel = 'noopener noreferrer';
      anchor.className = 'external-link';
      anchor.title = link.text + ' (opens in a new tab)';
      anchor.textContent = link.text;
      kids.push(root.document.createTextNode(' '), anchor);
    }
    pane.replaceChildren.apply(pane, kids);
  }

  function describe(request) {
    return 'LSST ' + KINDS[request.kind] + ' object ' + request.objectId;
  }

  function graphCount(values) {
    if (!Array.isArray(values) || values.length !== 1) return null;
    var value = values[0];
    if (value && typeof value === 'object') value = value['@value'];
    return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0 ? value : null;
  }

  function sleep(ms) {
    return new Promise(function (resolve) { root.setTimeout(resolve, ms); });
  }

  async function controls(token) {
    var waited = 0;
    for (;;) {
      if (token.cancelled) return null;
      var doc = root.document;
      var found = {
        server: doc.getElementById('gremlin_server'),
        command: doc.getElementById('bootstrap_command'),
        add: doc.getElementById('add2graph'),
        commands: doc.getElementById('commands'),
        // Wait for the Activity pane too, so the hand-off queries are logged.
        feedback: doc.getElementById('feedback')
      };
      if (found.server && found.command && found.add && found.commands && found.feedback) return found;
      if (waited >= CONTROL_WAIT_MS) return null;
      await sleep(200);
      waited += 200;
    }
  }

  function selectObject(request) {
    var list = Array.isArray(root.nodes) ? root.nodes : [];
    var node = list.find(function (item) {
      var id = root.skyGraphState && typeof root.skyGraphState.getId === 'function' ?
        root.skyGraphState.getId(item.id) : null;
      return id === request.objectId ||
        (item.label === request.objectId && /^object:/.test(String(item.heading || '')));
    });
    if (!node) return false;
    try {
      // An object hidden inside a collapsed cluster cannot be selected directly.
      if (root.network && typeof root.network.selectNodes === 'function') root.network.selectNodes([node.id]);
    }
    catch (error) { /* keep the graph; the actions below still apply */ }
    if (typeof root.showGraphCommands === 'function') root.showGraphCommands(node, false);
    return true;
  }

  async function open(request) {
    var token = root.graphViewToken || {cancelled: false};
    var ui = await controls(token);
    if (!ui) {
      if (!token.cancelled) log('Fink Portal link: the graph controls did not load.');
      return;
    }
    var option = ui.server.querySelector('option[data-survey="LSST"]');
    if (!option) {
      notice('Cannot open ' + describe(request) + ': this Graph Browser has no LSST graph server configured.');
      return;
    }
    ui.server.value = option.value;
    log('Opening ' + describe(request) + ' from Fink Portal.');
    var count = graphCount(root.callGremlinValues(request.exists, option.value));
    if (token.cancelled) return;
    if (count === null) {
      notice('The graph could not be checked for ' + describe(request) +
             ' (graph service unavailable). Try Search again later.');
      return;
    }
    if (count === 0) {
      notice(describe(request) + ' is not yet available in the graph. Graph data are imported after the Portal data.',
             request.kind === 'dia' ? {href: portalBase() + request.objectId, text: 'Open in Fink Portal'} : null);
      return;
    }
    ui.command.value = request.graph;
    ui.add.checked = false;
    var call = root.callGremlinGraph(request.graph, option.value, 0, token);
    if (root.wsBusy && typeof root.wsBusy.during === 'function') call = root.wsBusy.during(call);
    await call;
    if (token.cancelled) return;
    if (typeof root.showTab === 'function') root.showTab('graph');
    if (!selectObject(request)) log('Fink Portal link: ' + describe(request) + ' was not found in the graph response.');
  }

  // A rejected link leaves the profile start alone and only explains why.
  async function reject(error) {
    var ui = await controls(root.graphViewToken || {cancelled: false});
    var text = 'Fink Portal link ignored: ' + error + '.';
    if (ui) notice(text);
    else log(text);
  }

  root.parseFinkHandoff = parseFinkHandoff;
  var request = parseFinkHandoff(root.location && root.location.search);
  if (!request) return;
  // A valid link takes over the profile's default start, exactly once:
  // Reset reloads the graph fragment but does not repeat the hand-off.
  if (!request.error) {
    root.graphViewProfileBooted = true;
    // "Fink Portal" actions (Stylesheet.js finkPortalUrl) return to the calling Portal.
    var origin = portalOrigin(root.document.referrer);
    if (origin && origin !== DEFAULT_PORTAL) root.finkPortalOrigin = origin;
  }
  var started = false;
  function boot() {
    if (started || root.graphViewReady !== root.document.getElementById('graph')) return;
    started = true;
    root.removeEventListener('graphviewready', boot);
    root.finkHandoffDone = (request.error ? reject(request.error) : open(request)).catch(function (error) {
      notice('Cannot open ' + describe(request) + ': ' + error.message);
    });
  }
  root.addEventListener('graphviewready', boot);
  boot();
})(window);

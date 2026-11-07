/* Busy indicator: the header box left of "Version" shows Ready (green) or
 * Working (red) while a tracked operation runs.
 *
 * Tracked:
 * - Explore a graph (bootstrap: Search, Execute, profile start)
 * - Graph tools > Show all edges (fillEdges)
 * - double-click expansion of a node or edge (expand, expandEdge)
 * - selecting a node or edge (showGraphCommands: stylesheet lookups)
 * - Describe of a node or edge
 * - Show and Table selection actions (Node/Edge/Nodes/Edges.jsp loaded by jQuery)
 * - SkyView, Cutouts and LightCurve (graph actions and the table Sky View)
 * - plots loaded into an iframe (Evolution, Scatter, Correlogram, ...)
 * - Tag requests (tag-graph.js reports them with wsBusy.during)
 * Graph drawing is included: its stylesheet lookups run inside these calls.
 *
 * The legacy graph code uses synchronous requests, which freeze the page, so a
 * tracked operation first lets the browser paint "Working" and only then runs.
 * This file is evaluated by index.jsp and again by GraphView.jsp, because a
 * reloaded GraphView.js redefines the global graph functions. */
(function () {
  'use strict';

  var api = window.wsBusy;
  if (!api) {
    var active = 0;
    var generation = 0;

    var render = function () {
      var box = document.getElementById('busy-indicator');
      if (!box) return;
      var working = active > 0;
      box.textContent = working ? 'Working' : 'Ready';
      box.classList.toggle('ws-busy-working', working);
      box.classList.toggle('ws-busy-ready', !working);
      box.setAttribute('aria-busy', working ? 'true' : 'false');
    };

    // Count overlapping operations; the returned function ends this one once.
    // Work started before a Reset cannot end work started after it.
    var begin = function () {
      active++;
      render();
      var mine = generation;
      var ended = false;
      return function end() {
        if (ended) return;
        ended = true;
        if (mine !== generation) return;
        active = Math.max(0, active - 1);
        render();
      };
    };

    // Resolve after the next paint. A background tab may never run animation
    // frames, so a short timer is the fallback.
    var nextPaint = function () {
      return new Promise(function (resolve) {
        var done = false;
        var go = function () {
          if (done) return;
          done = true;
          setTimeout(resolve, 0);
        };
        if (typeof window.requestAnimationFrame === 'function') window.requestAnimationFrame(go);
        setTimeout(go, 100);
      });
    };

    var run = async function (work) {
      var end = begin();
      try {
        await nextPaint();
        return await work();
      }
      finally { end(); }
    };

    // Keep Working until a promise settles.
    var during = function (promise) {
      if (!promise || typeof promise.then !== 'function') return promise;
      var end = begin();
      promise.then(end, end);
      return promise;
    };

    // Same, but only for promises started inside a tracked operation.
    var track = function (promise) {
      return active === 0 ? promise : during(promise);
    };

    // Wait until the iframe now in a pane has loaded, or was removed/replaced.
    var frameLoaded = function (paneId) {
      var pane = document.getElementById(paneId);
      var frame = pane && typeof pane.querySelector === 'function' ? pane.querySelector('iframe') : null;
      if (!frame) return Promise.resolve();
      return new Promise(function (resolve) {
        var timer = null;
        var finish = function () {
          frame.removeEventListener('load', finish);
          if (timer !== null) clearInterval(timer);
          resolve();
        };
        frame.addEventListener('load', finish);
        timer = setInterval(function () {
          if (!frame.isConnected || pane.querySelector('iframe') !== frame) finish();
        }, 500);
      });
    };

    var reset = function () {
      generation++;
      active = 0;
      render();
    };

    api = window.wsBusy = {begin: begin, run: run, during: during, track: track, nextPaint: nextPaint,
                           frameLoaded: frameLoaded, reset: reset, render: render,
                           isWorking: function () { return active > 0; }};
  }

  function mark(fn) { fn.wsBusy = true; return fn; }

  // A top-level call paints Working first. A call made while another tracked
  // operation runs (e.g. expand() inside a Search) stays synchronous, because
  // the caller relies on its result before continuing.
  function wrapOperation(name) {
    var original = window[name];
    if (typeof original !== 'function' || original.wsBusy) return;
    window[name] = mark(function () {
      var self = this;
      var args = arguments;
      if (api.isWorking()) return api.track(original.apply(self, args));
      return api.run(function () { return original.apply(self, args); });
    });
  }

  // bootstrap() starts callGremlinGraph without awaiting it: track the request.
  function wrapGraphRequest() {
    var original = window.callGremlinGraph;
    if (typeof original !== 'function' || original.wsBusy) return;
    window.callGremlinGraph = mark(function () { return api.track(original.apply(this, arguments)); });
  }

  // Graph views: Working until the action finished and its iframe (if any) loaded.
  function wrapState(stateName, methods, paneId) {
    var state = window[stateName];
    if (!state || state.wsBusy) return;
    methods.forEach(function (method) {
      var original = state[method];
      if (typeof original !== 'function') return;
      state[method] = function () {
        var self = this;
        var args = arguments;
        return api.run(async function () {
          var result = await original.apply(self, args);
          if (paneId) api.during(api.frameLoaded(paneId));  // the caller need not wait for it
          return result;
        });
      };
    });
    state.wsBusy = true;
  }

  // loadPane: Show/Table pages loaded by jQuery, whose inline scripts query
  // the graph synchronously (jQuery fires ajaxComplete after those scripts ran,
  // for success and failure alike), and plots posted into an iframe.
  function wrapLoadPane() {
    var original = window.loadPane;
    if (typeof original !== 'function' || original.wsBusy) return;
    window.loadPane = mark(function (pane, url, iframe, height, encoded) {
      var self = this;
      var args = arguments;
      var $ = window.jQuery;
      if (iframe) {
        return api.run(async function () {
          var result = await original.apply(self, args);
          api.during(api.frameLoaded(pane));
          return result;
        });
      }
      if ((pane !== 'table' && pane !== 'result') || !$ || !/^(Nodes?|Edges?)\.jsp\?/.test(String(url))) {
        return original.apply(this, arguments);
      }
      var expected = encoded ? String(url) : encodeURI(String(url));
      var end = api.begin();
      var onComplete = function (event, xhr, settings) {
        var loaded = settings && String(settings.url);
        if (!loaded || (loaded !== expected && !loaded.endsWith('/' + expected))) return;
        $(document).off('ajaxComplete', onComplete);
        setTimeout(end, 0);  // let the page's ready handlers render it
      };
      $(document).on('ajaxComplete', onComplete);
      return api.nextPaint().then(function () {
        return original.apply(self, args);
      }).catch(function (error) {
        $(document).off('ajaxComplete', onComplete);
        end();
        throw error;
      });
    });
  }

  // Reset workspace abandons running work and shows Ready again.
  function wrapReset() {
    var original = window.reset;
    if (typeof original !== 'function' || original.wsBusy) return;
    window.reset = mark(function () {
      try { return original.apply(this, arguments); }
      finally { api.reset(); }
    });
  }

  ['bootstrap', 'fillEdges', 'expand', 'expandEdge', 'showGraphCommands', 'describeNode', 'describeEdge']
    .forEach(wrapOperation);
  wrapGraphRequest();
  wrapState('skyGraphState', ['open', 'openIds'], 'skyview');
  wrapState('lightcurveGraphState', ['open'], 'plot');
  wrapState('cutoutsGraphState', ['open'], null);
  wrapLoadPane();
  wrapReset();
  api.render();
})();

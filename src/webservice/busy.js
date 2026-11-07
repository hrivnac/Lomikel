/* Busy indicator: the header box left of "Version" shows Ready (green) or
 * Working (red) while a tracked operation runs.
 *
 * Tracked for now: Explore a graph (bootstrap: Search, Execute, profile start),
 * Graph tools > Show all edges (fillEdges) and the Table selection action
 * (Nodes.jsp / Edges.jsp in the table pane).
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
    var begin = function () {
      active++;
      render();
      var ended = false;
      return function end() {
        if (ended) return;
        ended = true;
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

    // Keep Working until a promise started during a tracked operation settles.
    var track = function (promise) {
      if (active === 0 || !promise || typeof promise.then !== 'function') return promise;
      var end = begin();
      promise.then(end, end);
      return promise;
    };

    api = window.wsBusy = {begin: begin, run: run, track: track, nextPaint: nextPaint,
                           render: render, isWorking: function () { return active > 0; }};
  }

  function wrapOperation(name) {
    var original = window[name];
    if (typeof original !== 'function' || original.wsBusy) return;
    var wrapped = function () {
      var self = this;
      var args = arguments;
      return api.run(function () { return original.apply(self, args); });
    };
    wrapped.wsBusy = true;
    window[name] = wrapped;
  }

  // bootstrap() starts callGremlinGraph without awaiting it: track the request.
  function wrapGraphRequest() {
    var original = window.callGremlinGraph;
    if (typeof original !== 'function' || original.wsBusy) return;
    var wrapped = function () { return api.track(original.apply(this, arguments)); };
    wrapped.wsBusy = true;
    window.callGremlinGraph = wrapped;
  }

  // The Table action loads Nodes.jsp/Edges.jsp with jQuery; their inline
  // scripts query the graph synchronously. jQuery fires ajaxComplete after
  // those scripts ran, for success and failure alike.
  function wrapTable() {
    var original = window.loadPane;
    if (typeof original !== 'function' || original.wsBusy) return;
    var wrapped = function (pane, url, iframe, height, encoded) {
      var $ = window.jQuery;
      if (pane !== 'table' || iframe || !$ || !/^(Nodes|Edges)\.jsp\?/.test(String(url))) {
        return original.apply(this, arguments);
      }
      var self = this;
      var args = arguments;
      var expected = encoded ? String(url) : encodeURI(String(url));
      var end = api.begin();
      var onComplete = function (event, xhr, settings) {
        var loaded = settings && String(settings.url);
        if (!loaded || (loaded !== expected && !loaded.endsWith('/' + expected))) return;
        $(document).off('ajaxComplete', onComplete);
        setTimeout(end, 0);  // let the table's ready handlers render it
      };
      $(document).on('ajaxComplete', onComplete);
      return api.nextPaint().then(function () {
        return original.apply(self, args);
      }).catch(function (error) {
        $(document).off('ajaxComplete', onComplete);
        end();
        throw error;
      });
    };
    wrapped.wsBusy = true;
    window.loadPane = wrapped;
  }

  wrapOperation('bootstrap');
  wrapOperation('fillEdges');
  wrapGraphRequest();
  wrapTable();
  api.render();
})();

/* The original w2ui layouts and pane IDs remain the integration boundary. */
(function () {
  'use strict';
  var scheduled = false;
  var observedGraph = null;
  var graphObserver = window.ResizeObserver ? new ResizeObserver(queueGraphResize) : null;

  function queueGraphResize() {
    window.height = window.innerHeight;
    window.width = window.innerWidth;
    if (scheduled) return;
    scheduled = true;
    window.setTimeout(function () {
      scheduled = false;
      var vis = document.getElementById('vis');
      if (!vis) return;
      window.visheight = Math.round(vis.getBoundingClientRect().height);
      if (window.network && typeof window.network.redraw === 'function') {
        window.network.redraw(); // preserve zoom and selected nodes
      }
    }, 80);
  }

  function watchGraph() {
    var graph = document.getElementById('graph');
    if (!graph || graph === observedGraph) return;
    if (graphObserver) {
      graphObserver.disconnect();
      graphObserver.observe(graph);
    }
    observedGraph = graph;
    queueGraphResize();
  }

  function resetLayout() {
    var outer = window.w2ui.layout;
    var left = window.w2ui.layoutLeft;
    var main = window.w2ui.layoutMain;
    if (!outer || !left || !main) return;
    outer.sizeTo('left', '48%');
    left.sizeTo('bottom', '78%');
    main.sizeTo('bottom', '10%');
    window.setTimeout(queueGraphResize, 100);
  }

  var legacyLoadPane = window.loadPane;
  // Only encoded graph-table Scatter requests have the JSON point contract.
  // Evolution and HBase use older payloads and remain on the legacy renderer.
  window.loadPane = function (pane, url, iframe, height, encoded) {
    if (pane === 'plot' && iframe && encoded && /^d3\/scatterplot\.jsp\?/.test(url)) {
      try {
        var query = new URL(url, document.baseURI).searchParams;
        if (query.has('tdata')) {
          var points = JSON.parse(query.get('tdata'));
          var rows = window.tdata;
          var xs = (query.get('x') || '').trim().split(/\s+/);
          var ys = (query.get('y') || '').trim().split(/\s+/);
          if (Array.isArray(points) && Array.isArray(rows) && xs[0] && ys[0]) {
            var pairs = [];
            rows.forEach(function (row) {
              xs.forEach(function (x) {
                ys.forEach(function (y) {
                  if (row[x] == null || row[y] == null || row[x] === '' || row[y] === '') return;
                  pairs.push({x: x, y: y, xValue: window.plotTableScalar(row[x]),
                              yValue: window.plotTableScalar(row[y])});
                });
              });
            });
            if (pairs.length === points.length) points.forEach(function (point, index) {
              var pair = pairs[index];
              if (String(point.x) === String(pair.xValue) && String(point.y) === String(pair.yValue)) {
                point.xField = pair.x;
                point.yField = pair.y;
              }
            });
          }
          query.set('tdata', JSON.stringify(points));
          url = 'webservice/d3/scatterplot.jsp?' + query.toString();
        }
      } catch (error) {
        // Malformed legacy payloads should keep their original route and error handling.
      }
    }
    if (pane === 'plot' && iframe && /^d3\/correlogram\.jsp\?/.test(url)) {
      var corrData = [];
      var invalidCorr = false;
      try {
        var corrQuery = new URL(url, document.baseURI).searchParams;
        var parsedCorr = JSON.parse(corrQuery.get('tdata'));
        if (Array.isArray(parsedCorr) && parsedCorr.every(function (item) {
          return item && typeof item === 'object' && 'x' in item && 'y' in item && 'value' in item && 'info' in item;
        })) corrData = parsedCorr;
        else invalidCorr = true;
      } catch (error) {
        invalidCorr = true;
      }
      // Never fall back to the legacy JSP: it embeds raw tdata in an inline script.
      var safeQuery = new URLSearchParams();
      safeQuery.set('tdata', JSON.stringify(corrData));
      if (invalidCorr) safeQuery.set('invalid', '1');
      url = 'webservice/d3/correlogram.jsp?' + safeQuery.toString();
      encoded = true;
    }
    return legacyLoadPane.call(this, pane, url, iframe, height, encoded);
  };

  // Preserve the legacy reset contract after moving panel 01 into the header.
  window.reset = function () {
    if (typeof window.disposeGraphView === 'function') window.disposeGraphView();
    loadPane('feedback', 'Feedback.jsp');
    loadPane('top', 'webservice/Top.jsp');
    loadPane('graph', 'webservice/GraphView.jsp');
    loadPane('image', 'Image.jsp');
    loadPane('plot', 'Plot.jsp');
    loadPane('result', 'Result.jsp');
    showTab('graph');
  };

  $(function () {
    var leftStyle = 'background-color:#e8f5ed;';
    var rightStyle = 'background-color:#edf4fb;';
    var rightBottomStyle = 'background-color:#dceafa;';
    $('#layout').w2layout({
      name: 'layout',
      panels: [
        { type: 'left', size: '48%', minSize: 380, resizable: true },
        { type: 'main', minSize: 400, resizable: true }
      ],
      onResize: queueGraphResize
    });
    $().w2layout({
      name: 'layoutLeft',
      panels: [
        { type: 'main', title: '02  /  Connection & actions <button type="button" class="ws-title-help" data-ws-help="Top" aria-label="Connection and actions help" title="Connection and actions help">?</button>', minSize: 90,
          resizable: true, overflow: 'auto', style: leftStyle },
        { type: 'bottom', title: '03  /  Graph <button type="button" class="ws-title-help" data-ws-help="GraphView" aria-label="Graph help" title="Graph help">?</button>', size: '78%', minSize: 210,
          resizable: true, overflow: 'auto', style: leftStyle }
      ],
      onResize: queueGraphResize
    });
    $().w2layout({
      name: 'layoutMain',
      panels: [
        { type: 'main', title: '04  /  Analysis <button type="button" class="ws-title-help" data-ws-help="Result" aria-label="Results help" title="Results help">?</button>', minSize: 220,
          resizable: true, overflow: 'auto', style: rightStyle,
          tabs: {
            name: 'tabs',
            active: 'resultTab',
            tabs: [
              { id: 'resultTab', caption: 'Results', tooltip: 'Results view' },
              { id: 'tableTab', caption: 'Table', tooltip: 'Table view' },
              { id: 'imageTab', caption: 'Image', tooltip: 'Image view' },
              { id: 'plotTab', caption: 'Plot', tooltip: 'Plot view' },
              { id: 'skyviewTab', caption: 'SkyView', tooltip: 'SkyView view' }
            ],
            onClick: function (event) {
              showTab(event.target.replace('Tab', ''));
              if (typeof window.wsSetAnalysisHelp === 'function') window.wsSetAnalysisHelp(event.target);
            }
          }
        },
        { type: 'bottom', title: '05  /  Activity & commands <button type="button" class="ws-title-help" data-ws-help="Feedback" aria-label="Activity help" title="Activity help">?</button>', size: '10%', minSize: 55,
          resizable: true, overflow: 'auto', style: rightBottomStyle }
      ],
      onResize: queueGraphResize
    });
    w2ui.layout.html('left', w2ui.layoutLeft);
    w2ui.layout.html('main', w2ui.layoutMain);
    w2ui.layoutLeft.load('main', 'webservice/Top.jsp');
    w2ui.layoutLeft.load('bottom', 'webservice/GraphView.jsp');
    w2ui.layoutMain.load('main', 'Tabs.jsp');
    w2ui.layoutMain.load('bottom', 'Feedback.jsp');
    if (typeof window.wsInstallHelp === 'function') window.wsInstallHelp();
    document.getElementById('restore-layout').addEventListener('click', resetLayout);
    document.getElementById('reset-workspace').addEventListener('click', window.reset);
    var versionDialog = document.getElementById('version-dialog');
    document.getElementById('version-button').addEventListener('click', function () {
      versionDialog.showModal();
    });
    if (window.MutationObserver) {
      new MutationObserver(watchGraph).observe(document.getElementById('layout'), {
        childList: true, subtree: true
      });
    }
    window.addEventListener('resize', queueGraphResize);
    watchGraph();
  });
})();

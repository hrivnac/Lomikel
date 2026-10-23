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
    left.sizeTo('top', '12%');
    left.sizeTo('bottom', '45%');
    main.sizeTo('bottom', '20%');
    window.setTimeout(queueGraphResize, 100);
  }

  $(function () {
    var leftStyle = 'background-color:#e8f5ed;';
    var leftTopStyle = 'background-color:#d1eadb;';
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
        { type: 'top', title: '01  /  Workspace', size: '12%', minSize: 85,
          resizable: true, overflow: 'auto', style: leftTopStyle },
        { type: 'main', title: '02  /  Connection & actions', minSize: 135,
          resizable: true, overflow: 'auto', style: leftStyle },
        { type: 'bottom', title: '03  /  Graph', size: '45%', minSize: 210,
          resizable: true, overflow: 'auto', style: leftStyle }
      ],
      onResize: queueGraphResize
    });
    $().w2layout({
      name: 'layoutMain',
      panels: [
        { type: 'main', title: '04  /  Analysis', minSize: 220,
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
            onClick: function (event) { showTab(event.target.replace('Tab', '')); }
          }
        },
        { type: 'bottom', title: '05  /  Activity & commands', size: '20%', minSize: 110,
          resizable: true, overflow: 'auto', style: rightBottomStyle }
      ],
      onResize: queueGraphResize
    });
    w2ui.layout.html('left', w2ui.layoutLeft);
    w2ui.layout.html('main', w2ui.layoutMain);
    w2ui.layoutLeft.load('top', 'webservice/TopMini.jsp');
    w2ui.layoutLeft.load('main', 'webservice/Top.jsp');
    w2ui.layoutLeft.load('bottom', 'GraphView.jsp');
    w2ui.layoutMain.load('main', 'Tabs.jsp');
    w2ui.layoutMain.load('bottom', 'Feedback.jsp');
    document.getElementById('restore-layout').addEventListener('click', resetLayout);
    if (window.MutationObserver) {
      new MutationObserver(watchGraph).observe(document.getElementById('layout'), {
        childList: true, subtree: true
      });
    }
    window.addEventListener('resize', queueGraphResize);
    watchGraph();
  });
})();

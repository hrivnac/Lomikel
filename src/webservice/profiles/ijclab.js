loadPane("result", "Result.jsp");
// The fragment loads the controller and wrappers; profile arrival can race it.
(function () {
  function bootGraph() {
    if (window.graphViewProfileBooted ||
        window.graphViewReady !== document.getElementById('graph')) return;
    window.graphViewProfileBooted = true;
    window.removeEventListener('graphviewready', bootGraph);
    bootstrap('text', 0);
  }
  window.addEventListener('graphviewready', bootGraph);
  bootGraph();
})();

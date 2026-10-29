loadPane("result", "Result.jsp");
if (window.graphViewVariant === 'new') {
  // The fragment owns the controller; its ready event follows both graph wrappers.
  function bootNewGraph() {
    if (!window.graphViewNewReady || window.graphViewNewReady !== document.getElementById('graph')) return;
    window.removeEventListener('graphviewnewready', bootNewGraph);
    bootstrap('text', 0);
  }
  window.addEventListener('graphviewnewready', bootNewGraph);
  bootNewGraph(); // The fragment can arrive before the profile.
} else {
  $.getScript("GraphView.js", function() {bootstrap('text', 0);});
}

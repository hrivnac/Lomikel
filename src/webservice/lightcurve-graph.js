/* Plot one selected graph object's light curve in the existing Analysis/Plot pane. */
(function () {
  'use strict';
  var frame = null;
  var sequence = 0;
  var previousDispose = window.disposeGraphView;
  function cancel() {
    sequence++;
    if (frame) frame.src = 'about:blank'; // unload aborts the iframe's active fetch
    frame = null;
    var pane = document.getElementById('plot');
    if (pane) pane.classList.remove('ws-lightcurve');
  }
  function resize() {
    // A resized w2ui pane does not always dispatch resize inside its iframe.
    if (!frame || !frame.isConnected) return;
    try {
      if (typeof frame.contentWindow.lightCurveResize === 'function') frame.contentWindow.lightCurveResize();
    } catch (error) { /* A replaced or unloading frame has no plot to resize. */ }
  }
  function activate() {
    showTab('plot');
    w2ui.layoutMain.panels[0].tabs.click('plotTab');
  }
  function open() {
    cancel();
    var pane = document.getElementById('plot');
    if (!pane) return;
    var selected = window.selectedNode;
    var live = selected && window.nodes.some(function (node) { return node === selected; });
    var id = live && window.skyGraphState && window.skyGraphState.getId(selected.id);
    activate();
    if (!id || !/^(?:ZTF[A-Za-z0-9_-]{1,80}|[0-9]{1,24})$/.test(id)) {
      pane.textContent = 'Select a live ZTF or LSST object to plot its light curve.';
      return;
    }
    pane.classList.add('ws-lightcurve');
    frame = document.createElement('iframe');
    frame.name = 'wsLightCurve-' + sequence;
    frame.title = 'Light curve for ' + id;
    frame.width = '100%';
    frame.height = '100%';
    pane.replaceChildren(frame);
    var form = document.createElement('form');
    form.method = 'POST';
    form.hidden = true;
    form.action = new URL('d3/lightcurve.jsp', document.baseURI).pathname;
    form.target = frame.name;
    var field = document.createElement('input');
    field.name = 'objectId';
    field.value = id;
    form.appendChild(field);
    document.body.appendChild(form);
    try { form.submit(); }
    finally { document.body.removeChild(form); }
  }
  window.disposeGraphView = function () {
    cancel();
    return previousDispose.apply(this, arguments);
  };
  window.lightcurveGraphState = {open:open, cancel:cancel, resize:resize};
  // GraphView.js renders href="#" action links under a WAR-root <base>. If an
  // inline onclick is blocked or throws, its default link navigation abandons
  // the preview. Handle this one preview action before the legacy inline code.
  // GraphView.jsp can be reloaded, so install the delegated listener only once.
  if (!window.wsLightCurveClickInstalled) {
    window.wsLightCurveClickInstalled = true;
    document.addEventListener('click', function (event) {
      var link = event.target.closest && event.target.closest('a');
      var commands = document.getElementById('commands');
      if (!link || !commands || !commands.contains(link) ||
          link.getAttribute('href') !== '#' || link.textContent.trim() !== 'LightCurve') return;
      event.preventDefault();
      event.stopImmediatePropagation();
      try { window.lightcurveGraphState.open(); }
      catch (error) {
        var pane = document.getElementById('plot');
        if (pane) pane.textContent = 'Could not open the light curve. Check the browser console.';
        console.error('LightCurve action failed', error);
      }
    }, true);
  }
})();

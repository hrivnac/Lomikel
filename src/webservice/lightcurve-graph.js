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
    frame = document.createElement('iframe');
    frame.name = 'wsLightCurve-' + sequence;
    frame.title = 'Light curve for ' + id;
    frame.width = '100%';
    frame.height = '100%';
    pane.replaceChildren(frame);
    var form = document.createElement('form');
    form.method = 'POST';
    form.hidden = true;
    form.action = new URL('webservice/d3/lightcurve.jsp', document.baseURI).pathname;
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
  window.lightcurveGraphState = {open:open, cancel:cancel};
})();

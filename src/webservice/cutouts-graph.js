/* Graph-wide cutout contact sheet in the existing Image tab. */
(function () {
  'use strict';
  var kinds = ['Science', 'Template', 'Difference'];
  var MAX_OBJECTS = 30;
  var generation = 0;
  var requests = [];

  function cancel() {
    generation++;
    requests.forEach(function (controller) { controller.abort(); });
    requests = [];
  }
  function clear() {
    cancel();
    var pane = document.getElementById('image');
    if (!pane) return;
    pane.classList.remove('ws-cutouts');
    var placeholder = document.getElementById('ws-image-placeholder');
    if (placeholder) pane.innerHTML = placeholder.innerHTML;
  }
  function activate() {
    showTab('image');
    w2ui.layoutMain.panels[0].tabs.click('imageTab');
  }
  function cell(kind) {
    var block = document.createElement('div');
    block.className = 'ws-cutout-cell';
    var label = document.createElement('strong');
    label.textContent = kind;
    block.appendChild(label);
    return block;
  }
  function stamp(block, objectId, sourceId, kind) {
    var query = new URLSearchParams();
    query.set(sourceId ? 'diaSourceId' : 'objectId', sourceId || objectId);
    query.set('kind', kind);
    var image = document.createElement('img');
    image.src = 'https://api.' + (sourceId ? 'lsst' : 'ztf') + '.fink-portal.org/api/v1/cutouts?' + query.toString();
    image.alt = kind + ' cutout of ' + objectId;
    image.loading = 'lazy';
    image.onerror = function () {
      image.remove();
      var note = document.createElement('span');
      note.className = 'ws-cutout-error';
      note.textContent = kind + ' cutout unavailable';
      block.appendChild(note);
    };
    block.appendChild(image);
  }
  function row(pane, id) {
    var container = document.createElement('section');
    container.className = 'ws-cutout-row ws-cutout-' + (id.startsWith('ZTF') ? 'ztf' : 'lsst');
    var heading = document.createElement('h3');
    heading.textContent = id;
    container.appendChild(heading);
    kinds.forEach(function (kind) { container.appendChild(cell(kind)); });
    pane.appendChild(container);
    return container;
  }
  async function source(id, container, current) {
    var controller = new AbortController();
    requests.push(controller);
    try {
      var response = await fetch('webservice/CutoutSource.jsp', {
        method: 'POST', credentials: 'same-origin', signal: controller.signal,
        headers: {'Content-Type': 'application/json'}, body: JSON.stringify({diaObjectId: id})
      });
      if (!response.ok) throw new Error('HTTP ' + response.status);
      var data = await response.json();
      if (generation !== current || controller.signal.aborted) return;
      if (!data || !/^[0-9]{1,24}$/.test(data.diaSourceId || '')) throw new Error('no recent source');
      kinds.forEach(function (kind, index) { stamp(container.children[index + 1], id, data.diaSourceId, kind); });
    } catch (error) {
      if (generation !== current || controller.signal.aborted) return;
      kinds.forEach(function (_, index) {
        var note = document.createElement('span');
        note.className = 'ws-cutout-error';
        note.textContent = 'Source unavailable';
        container.children[index + 1].appendChild(note);
      });
    } finally {
      var index = requests.indexOf(controller);
      if (index !== -1) requests.splice(index, 1);
    }
  }
  async function open() {
    cancel();
    var current = generation;
    var pane = document.getElementById('image');
    if (!pane) return;

    var ids = Array.from(new Set(window.skyGraphState.getIds()));
    pane.replaceChildren();
    activate();
    if (!ids.length) { pane.textContent = 'No objects are in the current graph.'; return; }
    if (ids.length > MAX_OBJECTS) {
      pane.textContent = 'The graph has more than ' + MAX_OBJECTS + ' objects. Reduce it before loading cutouts.';
      return;
    }
    if (!ids.every(function (id) { return /^(?:ZTF[A-Za-z0-9_-]{1,80}|[0-9]{1,24})$/.test(id); })) {
      pane.textContent = 'Invalid object ID in the current graph.';
      return;
    }
    pane.className = 'ws-cutouts';
    var title = document.createElement('h2');
    title.textContent = 'Latest cutouts · ' + ids.length + ' object' + (ids.length === 1 ? '' : 's');
    pane.appendChild(title);
    var lsst = [];
    ids.forEach(function (id) {
      var container = row(pane, id);
      if (id.startsWith('ZTF')) kinds.forEach(function (kind, index) {
        stamp(container.children[index + 1], id, '', kind);
      });
      else lsst.push({id:id, container:container});
    });
    // A few concurrent source lookups avoid flooding the public Fink API.
    var next = 0;
    async function worker() {
      while (next < lsst.length && generation === current) {
        var item = lsst[next++];
        await source(item.id, item.container, current);
      }
    }
    await Promise.all(Array.from({length: Math.min(3, lsst.length)}, worker));
  }
  window.cutoutsGraphState = {open:open, cancel:cancel, clear:clear};
})();

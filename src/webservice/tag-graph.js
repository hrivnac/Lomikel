/* "Tag" selection action: tag the selected object vertex in a TAG OCol.
 *
 * A small dialog asks for the tag name (configured list from TagObject.jsp, i.e. TagNames.txt), the tag
 * value, author, annotation and reference. The WAR's TagObject.jsp then writes with its own writable
 * JanusGraph client (LSST -> CC graph, ZTF -> IJCLab graph), or forwards it to the WAR of the site that does:
 *   Classifier.instance('TAG', survey, '').classify(gr, oid, tag, value, author, annotation, ref)
 * Only a validated object ID and the dialog values are sent; the page decides the database from the ID. */
(function () {
  'use strict';
  var OBJECT_ID = /^(?:ZTF[A-Za-z0-9_-]{1,80}|[0-9]{1,24})$/;
  var TAG_NAME = /^[A-Za-z0-9_.-]{1,64}$/;
  var ENDPOINT = 'TagObject.jsp';
  var TIMEOUT_MS = 90000;
  var LIMITS = {author: 200, annotation: 2000, ref: 1000};
  var busy = false;
  var dialogBusy = false;

  function say(message) {
    if (typeof window.feedbackLine === 'function') window.feedbackLine(message);
  }
  function surveyOf(objectId) { return objectId.indexOf('ZTF') === 0 ? 'ZTF' : 'LSST'; }
  function clean(message) { return String(message).replace(/[^\x20-\x7e]/g, ' ').trim().slice(0, 200); }

  // Requests show Working in the header box; the open dialog does not.
  function call(options, timeout) {
    var work = send(options, timeout);
    return window.wsBusy && typeof window.wsBusy.during === 'function' ? window.wsBusy.during(work) : work;
  }

  async function send(options, timeout) {
    var controller = typeof AbortController === 'function' ? new AbortController() : null;
    var timer = controller ? setTimeout(function () { controller.abort(); }, timeout) : null;
    try {
      options.signal = controller ? controller.signal : undefined;
      options.credentials = 'same-origin';
      var response = await fetch(ENDPOINT, options);
      var body = null;
      try { body = await response.json(); } catch (error) { body = null; }
      if (!response.ok || !body) {
        var failure = new Error(body && typeof body.error === 'string' ? clean(body.error) : 'HTTP ' + response.status);
        failure.status = response.status;
        throw failure;
      }
      return body;
    } finally {
      if (timer !== null) clearTimeout(timer);
    }
  }

  async function tagNames() {
    var body = await call({method: 'GET', headers: {Accept: 'application/json'}, cache: 'no-store'}, 15000);
    var names = Array.isArray(body.tags) ? body.tags.filter(function (name) {
      return typeof name === 'string' && TAG_NAME.test(name);
    }) : [];
    if (!names.length) throw new Error('no tag names are configured');
    return names;
  }

  // Validated tag request from the dialog values, or a message explaining what is wrong.
  function request(objectId, values, names) {
    var tag = String(values.tag || '');
    if (names.indexOf(tag) < 0) return {error: 'choose one of the configured tag names'};
    var text = String(values.value == null ? '' : values.value).trim();
    var value = Number(text);
    if (text === '' || !isFinite(value) || value < 0) return {error: 'the tag value must be a number >= 0'};
    var result = {objectId: objectId, tag: tag, value: value};
    for (var key in LIMITS) {
      var field = String(values[key] == null ? '' : values[key]).replace(/[\x00-\x1f\x7f\u2028\u2029]/g, ' ').trim();
      if (field.length > LIMITS[key]) return {error: key + ' is longer than ' + LIMITS[key] + ' characters'};
      result[key] = field;
    }
    return {request: result};
  }

  function element(tag, attributes, text) {
    var node = document.createElement(tag);
    for (var key in attributes || {}) node.setAttribute(key, attributes[key]);
    if (text !== undefined) node.textContent = text;
    return node;
  }
  function row(form, label, control) {
    var line = element('label', {'class': 'tag-dialog-row'});
    line.appendChild(element('span', {}, label));
    line.appendChild(control);
    form.appendChild(line);
    return control;
  }

  // The dialog resolves with the validated request, or null when cancelled.
  function ask(objectId, names) {
    return new Promise(function (resolve) {
      var dialog = element('dialog', {'class': 'tag-dialog', 'aria-labelledby': 'tag-dialog-title'});
      var form = element('form', {method: 'dialog'});
      form.appendChild(element('h2', {id: 'tag-dialog-title'}, 'Tag object'));
      var id = row(form, 'object id', element('input', {type: 'text', readonly: 'readonly', name: 'objectId'}));
      id.value = objectId;
      var tag = row(form, 'tag name', element('select', {name: 'tag'}));
      names.forEach(function (name) { tag.appendChild(element('option', {value: name}, name)); });
      tag.value = names[0];
      var value = row(form, 'tag value', element('input', {type: 'number', name: 'value', step: 'any', min: '0', required: 'required'}));
      value.value = '1.0';
      var author = row(form, 'author', element('input', {type: 'text', name: 'author', maxlength: String(LIMITS.author)}));
      var annotation = row(form, 'annotation', element('input', {type: 'text', name: 'annotation', maxlength: String(LIMITS.annotation)}));
      var ref = row(form, 'ref', element('input', {type: 'text', name: 'ref', maxlength: String(LIMITS.ref)}));
      var problem = element('p', {'class': 'tag-dialog-error', role: 'alert'});
      form.appendChild(problem);
      var buttons = element('div', {'class': 'tag-dialog-buttons'});
      var cancel = element('button', {type: 'button', value: 'cancel'}, 'Cancel');
      var submit = element('button', {type: 'submit', value: 'tag'}, 'Tag');
      buttons.appendChild(cancel);
      buttons.appendChild(submit);
      form.appendChild(buttons);
      dialog.appendChild(form);
      var done = false;
      function finish(result) {
        if (done) return;
        done = true;
        if (dialog.open && typeof dialog.close === 'function') dialog.close();
        if (dialog.parentNode) dialog.parentNode.removeChild(dialog);
        resolve(result);
      }
      cancel.addEventListener('click', function () { finish(null); });
      dialog.addEventListener('cancel', function () { finish(null); });
      dialog.addEventListener('close', function () { finish(null); });
      form.addEventListener('submit', function (event) {
        event.preventDefault();
        var checked = request(objectId, {tag: tag.value, value: value.value, author: author.value,
          annotation: annotation.value, ref: ref.value}, names);
        if (checked.error) {
          problem.textContent = 'Cannot tag: ' + checked.error + '.';
          return;
        }
        finish(checked.request);
      });
      document.body.appendChild(dialog);
      if (typeof dialog.showModal === 'function') dialog.showModal();
      else dialog.setAttribute('open', 'open');
      if (typeof value.focus === 'function') value.focus();
    });
  }

  async function run() {
    var selected = window.selectedNode;
    var live = selected && Array.isArray(window.nodes) && window.nodes.some(function (node) { return node === selected; });
    var objectId = live && window.skyGraphState && window.skyGraphState.getId(selected.id);
    if (!objectId || !OBJECT_ID.test(objectId)) {
      say('Tag: select a live ZTF or LSST object vertex.');
      return false;
    }
    if (busy || dialogBusy) {
      say('Tag: another tagging request is still running.');
      return false;
    }
    dialogBusy = true;
    var tagRequest;
    try {
      var names;
      try { names = await tagNames(); }
      catch (error) {
        say('Tag: cannot load the configured tag names (' + (error && error.name === 'AbortError' ? 'timed out' :
            clean((error && error.message) || 'unknown error')) + '); nothing was written.');
        return false;
      }
      tagRequest = await ask(objectId, names);
    } finally {
      dialogBusy = false;
    }
    if (!tagRequest) {
      say('Tag: cancelled; nothing was written.');
      return false;
    }
    busy = true;
    var survey = surveyOf(objectId);
    try {
      say('Tag: tagging ' + objectId + ' as "' + tagRequest.tag + '" (' + tagRequest.value + ') in the ' + survey + ' graph …');
      var body = await call({
        method: 'POST',
        headers: {'Content-Type': 'application/json', 'X-Fink-Tag': '1'},
        body: JSON.stringify(tagRequest)
      }, TIMEOUT_MS);
      if (body.ok !== true || body.objectId !== objectId || body.tag !== tagRequest.tag) throw new Error('Unexpected tag response');
      say('Tag: ' + objectId + ' is now tagged "' + tagRequest.tag + '" (' + tagRequest.value + ') in the ' + body.survey +
          ' graph' + (typeof body.site === 'string' && /^[A-Za-z0-9_.-]{1,40}$/.test(body.site) ? ' by the ' + body.site + ' server' : '') +
          ' (verified, ' + body.entries + (body.entries === 1 ? ' entry' : ' entries') +
          ' for this object). A read-only graph server may need a few minutes to show it.');
      return true;
    } catch (error) {
      // TagObject.jsp answers 4xx and 503 before any write; other failures leave the outcome unknown.
      var refused = error && (error.status === 503 || (error.status >= 400 && error.status < 500));
      say('Tag failed for ' + objectId + ': ' + (error && error.name === 'AbortError' ? 'timed out' : clean((error && error.message) || 'unknown error')) +
          (refused ? '; nothing was written.' : '. The tag may or may not have been written.'));
      return false;
    } finally {
      busy = false;
    }
  }

  window.tagGraphState = {run: run, request: request};
})();

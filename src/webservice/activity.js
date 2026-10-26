/* Shared, text-only activity rendering for graph and data-service requests. */
(function (root) {
  'use strict';
  function append(entry) {
    var pane = document.getElementById('feedback');
    if (!pane) return;
    pane.appendChild(entry);
    var scroller = pane.parentElement || pane;
    scroller.scrollTop = scroller.scrollHeight;
  }
  root.activityLogMessage = function (message) {
    var text = String(message);
    if (text.length > 42 || /[\r\n]/.test(text)) {
      var details = document.createElement('details');
      details.className = 'activity-entry activity-message';
      var summary = document.createElement('summary');
      summary.textContent = text.replace(/\s+/g, ' ').trim().slice(0, 42) + '…';
      var full = document.createElement('pre');
      full.className = 'activity-query';
      full.textContent = text;
      details.appendChild(summary);
      details.appendChild(full);
      append(details);
      return;
    }
    var entry = document.createElement('div');
    entry.className = 'activity-message';
    entry.textContent = text;
    append(entry);
  };
  root.activityLogQuery = function (target, query) {
    var entry = document.createElement('details');
    entry.className = 'activity-entry';
    var summary = document.createElement('summary');
    var badge = document.createElement('span');
    badge.className = 'activity-target';
    badge.textContent = String(target);
    var preview = document.createElement('span');
    preview.className = 'activity-preview';
    var compact = String(query).replace(/\s+/g, ' ').trim();
    preview.textContent = compact.length > 42 ? compact.slice(0, 42) + '…' : compact;
    summary.appendChild(badge);
    summary.appendChild(preview);
    var content = document.createElement('pre');
    content.className = 'activity-query';
    content.textContent = String(query);
    entry.appendChild(summary);
    entry.appendChild(content);
    append(entry);
  };
})(window);

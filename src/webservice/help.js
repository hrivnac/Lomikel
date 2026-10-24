/* Preview-only panel help: keep controls in panel chrome when pane contents change. */
(function () {
  'use strict';
  var views = {resultTab: 'Result', tableTab: 'Table', imageTab: 'Image',
               plotTab: 'Plot', skyviewTab: 'SkyView'};
  var allowed = {Top: true, GraphView: true, Result: true, Table: true,
                 Image: true, Plot: true, SkyView: true, Feedback: true};

  window.wsSetAnalysisHelp = function (tab) {
    var name = views[tab];
    var button = document.querySelector('#layout_layoutMain_panel_main .w2ui-panel-title .ws-title-help');
    if (!name || !button) return;
    var label = (name === 'Result' ? 'Results' : name) + ' help';
    button.setAttribute('data-ws-help', name);
    button.setAttribute('aria-label', label);
    button.title = label;
  };

  window.wsInstallHelp = function () {
    var layout = document.getElementById('layout');
    if (!layout) return;
    layout.addEventListener('click', function (event) {
      var button = event.target.closest && event.target.closest('.ws-title-help');
      if (!button || !layout.contains(button)) return;
      event.preventDefault();
      event.stopPropagation();
      var name = button.getAttribute('data-ws-help');
      if (Object.prototype.hasOwnProperty.call(allowed, name)) {
        w2popup.load({url: 'webservice/Help-' + name + '.html', showMax: true,
          onOpen: function (popupEvent) {
            // w2ui calls onOpen only in the before phase. onComplete runs after
            // the template has been installed and the opening animation finishes.
            popupEvent.onComplete = function () {
              var popup = document.getElementById('w2ui-popup');
              if (!popup || !popup.querySelector('#ws-help-popup-title')) return;
              popup.setAttribute('role', 'dialog');
              popup.setAttribute('aria-modal', 'true');
              popup.setAttribute('aria-labelledby', 'ws-help-popup-title');
              popup.setAttribute('tabindex', '-1');
              popup.focus();
            };
          }});
      }
    });
  };

  // Graph/table actions also switch the visible tab without clicking its tab strip.
  var originalShowTab = window.showTab;
  window.showTab = function (name) {
    var result = originalShowTab.apply(this, arguments);
    window.wsSetAnalysisHelp(name + 'Tab');
    return result;
  };
})();

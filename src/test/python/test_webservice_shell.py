"""Offline integration and WAR packaging checks for the default FinkBrowser shell."""
from pathlib import Path
import unittest
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[3]
SOURCE = ROOT / 'src/webservice'
WAR = ROOT / 'lib/FinkBrowser.war'


class WebserviceShellTest(unittest.TestCase):
    def test_default_shell_preserves_panel_and_action_contracts(self):
        jsp = (SOURCE / 'index.jsp').read_text()
        js = (SOURCE / 'workspace.js').read_text()
        top = (SOURCE / 'Top.jsp').read_text()
        graph = (SOURCE / 'GraphView.jsp').read_text()
        for name in ('layout', 'layoutLeft', 'layoutMain'):
            self.assertIn("name: '" + name + "'", js)
        for name in ('Top.jsp', 'GraphView.jsp', 'Tabs.jsp', 'Feedback.jsp'):
            self.assertIn("'" + name + "'", js)
        for name in ('resultTab', 'tableTab', 'imageTab', 'plotTab', 'skyviewTab'):
            self.assertIn("id: '" + name + "'", js)
        for api in ('window.feedback', 'window.commands', 'window.visheight', 'window.height'):
            self.assertIn(api, jsp)
        self.assertIn("showTab(event.target.replace('Tab', ''))", js)
        self.assertIn('network.redraw()', js)
        self.assertIn('id="mini"', jsp)
        self.assertIn('id="top"', top)
        self.assertIn('id="commands"', top)
        self.assertIn('id="graph"', graph)
        self.assertIn('id="vis"', graph)
        self.assertIn('graph-table-origin.js', graph)
        self.assertIn('SurveyStylesheet.js', graph)
        self.assertIn('window.activityLogMessage(txt)', jsp)
        self.assertIn('activity.js?v=query-activity-1', jsp)
        self.assertIn('content.textContent = String(query)', (SOURCE / 'activity.js').read_text())
        self.assertIn('class="workspace"', jsp)
        self.assertIn('request.getContextPath() %>/', jsp)
        self.assertIn('"Init.jsp"', jsp)
        self.assertIn('"Servers.jsp"', top)
        self.assertIn('"Graphs.jsp"', top)

    def test_build_sources_one_application(self):
        build = (ROOT / 'ant/build.xml').read_text()
        war = build[build.index('<target name="war"'):]
        war = war[:war.index('</target>')]
        self.assertIn('file="../src/webservice/index.jsp"', war)
        self.assertIn('webxml="../src/webservice/web.xml"', war)
        self.assertIn('dir="../src/webservice"', war)
        self.assertNotIn('../src/ws', war)
        self.assertNotIn('Lomikel.war', build)
        for app in ('AlertsView', 'LightCurveView', 'ClassificationView'):
            self.assertTrue((SOURCE / app / 'index.html').is_file())
            self.assertIn(app, build)
        for app in ('LightCurveView', 'ClassificationView'):
            self.assertIn('../src/webservice/' + app, build)
        for name in ('workspace.css', 'web.xml', 'Init.jsp', 'LoadPane.js',
                     'GraphView.js', 'Nodes.jsp', 'Edges.jsp'):
            self.assertTrue((SOURCE / name).is_file(), name)

    def test_plot_and_venn_contracts(self):
        for name in ('Nodes.jsp', 'Edges.jsp'):
            page = (SOURCE / name).read_text()
            self.assertIn('TableView.js?v=table-1', page)
            self.assertIn('Nodes.css?v=table-1', page)
            self.assertIn('plotTableDetailFormatter(index, row)', page)
        self.assertIn('showCorrelogram("from", "to", "sizeIn", "sizeOut")',
                      (SOURCE / 'Edges.jsp').read_text())
        jsp = (SOURCE / 'Venn.jsp').read_text()
        popup = (SOURCE / 'd3/vennPopup.jsp').read_text()
        standalone = (SOURCE / 'd3/venn.jsp').read_text()
        self.assertIn('errorPage="../ExceptionHandler.jsp"', standalone)
        css = (SOURCE / 'd3/venn.css').read_text()
        for page in (jsp, standalone, (SOURCE / 'd3/correlogram.jsp').read_text()):
            self.assertIn('venn.js?v=vertical-2', page)
        self.assertIn('venn.css?v=vertical-2', popup)
        self.assertIn('class="venn-view"', jsp)
        self.assertIn('class="venn-view"', popup)
        self.assertIn('class="venn-view"', standalone)
        self.assertIn('.venn-stats', css)
        self.assertIn('JSONObject.quote(name)', (SOURCE / 'd3/scatterplot.jsp').read_text())

    @unittest.skipUnless(WAR.exists(), 'Build locally with ant war first')
    def test_packaged_root_is_complete_and_substituted(self):
        with ZipFile(WAR) as archive:
            names = archive.namelist()
            self.assertEqual(len(names), len(set(names)), 'WAR entries must be unique')
            for name in ('index.jsp', 'Top.jsp', 'Graphs.jsp', 'Servers.jsp',
                         'GraphView.jsp', 'Tabs.jsp', 'Feedback.jsp', 'WEB-INF/web.xml',
                         'GraphView.js', 'activity.js', 'Stylesheet.js', 'SurveyStylesheet.js',
                         'TableView.js', 'LoadPane.js', 'Nodes.jsp', 'Edges.jsp',
                         'Venn.jsp', 'd3/venn.jsp', 'd3/vennPopup.jsp',
                         'd3/actions.js', 'd3/correlogram.jsp', 'd3/scatterplot.jsp',
                         'AlertsView/index.html', 'AlertsView/LatestAlerts.jsp',
                         'LightCurveView/index.html', 'LightCurveView/vendor/plotly-gl2d-3.1.0.min.js',
                         'ClassificationView/index.html', 'ClassificationView/fink_graph.js',
                         'ClassificationView/d3.min.js', 'WEB-INF/classes/d3/actions.js'):
                self.assertIn(name, names)
            self.assertNotIn('webservice/index.jsp', names)
            self.assertNotIn('TopMini.jsp', names)
            for name in ('index.jsp', 'Top.jsp', 'Apps.html'):
                page = archive.read(name).decode()
                for token in ('@NAME@', '@HOME@', '@VERSION@', '@BUILD@', '@BOOT@'):
                    self.assertNotIn(token, page, (name, token))
            self.assertIn('class="workspace"', archive.read('index.jsp').decode())
            self.assertIn('Init.initWS("FinkBrowser")', archive.read('Init.jsp').decode())
            self.assertIn('FinkDataExplorer', archive.read('WEB-INF/web.xml').decode())
            for name in ('GraphView.js', 'Stylesheet.js', 'd3/venn.js'):
                self.assertEqual(archive.read(name), (SOURCE / name).read_bytes())
            self.assertFalse(any(name.endswith(('.md', '.cjs', '.py')) or '/tests/' in name
                                 for name in names if name.startswith(('AlertsView/', 'LightCurveView/',
                                                                        'ClassificationView/'))))


if __name__ == '__main__':
    unittest.main()

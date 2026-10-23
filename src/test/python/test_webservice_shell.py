"""Offline packaging and integration checks for the opt-in webservice shell."""
from pathlib import Path
import re
import unittest
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[3]
SOURCE = ROOT / "src/webservice"
WAR = ROOT / "lib/FinkBrowser.war"


class WebserviceShellTest(unittest.TestCase):
    def test_keeps_legacy_panel_apis(self):
        jsp = (SOURCE / "index.jsp").read_text()
        js = (SOURCE / "workspace.js").read_text()
        for name in ("layout", "layoutLeft", "layoutMain"):
            self.assertIn("name: '" + name + "'", js)
        for name in ("webservice/Top.jsp", "webservice/GraphView.jsp", "Tabs.jsp", "Feedback.jsp"):
            self.assertTrue("'" + name + "'" in js, name)
        for name in ("resultTab", "tableTab", "imageTab", "plotTab", "skyviewTab"):
            self.assertIn("id: '" + name + "'", js)
        for api in ("window.feedback", "window.commands", "window.visheight", "window.height"):
            self.assertIn(api, jsp)
        self.assertIn("showTab(event.target.replace('Tab', ''))", js)
        self.assertIn("network.redraw()", js)

    def test_preview_is_packaged_separately_from_root(self):
        build = (ROOT / "ant/build.xml").read_text()
        self.assertIn('tofile="../build/webservice/index.jsp"', build)
        self.assertIn('prefix="webservice"', build)
        self.assertIn('<copy file="../src/ws/index.jsp"', build)
        self.assertIn('destfile="../lib/FinkBrowser.war"', build)
        self.assertIn('href="<%= request.getContextPath() %>/"', (SOURCE / "index.jsp").read_text())
        self.assertTrue((SOURCE / "workspace.css").is_file())
        css = (SOURCE / "workspace.css").read_text()
        self.assertIn("#layout_layoutMain_panel_main .w2ui-panel-title", css)
        self.assertIn("#layout_layoutMain_panel_bottom .w2ui-panel-title", css)

    def test_preview_panels_keep_existing_action_contract(self):
        header = (SOURCE / "index.jsp").read_text()
        top = (SOURCE / "Top.jsp").read_text()
        js = (SOURCE / "workspace.js").read_text()
        self.assertIn('id="mini"', header)  # GraphView.jsp still requires #mini.
        self.assertIn('id="reset-workspace"', header)
        self.assertIn("loadPane('top', 'webservice/Top.jsp')", js)
        self.assertIn('window.disposeGraphView()', js)
        self.assertIn("pane.appendChild(document.createTextNode(txt))", header)
        self.assertNotIn("pane.innerHTML += txt", header)
        self.assertIn('id="top"', top)
        self.assertIn('id="commands"', top)
        for name in ("gremlin_server", "bootstrap_graph", "bootstrap_command", "add2graph"):
            self.assertIn('id="' + name + '"', top)
        self.assertIn("bootstrap('selection')", top)
        self.assertIn("bootstrap('text')", top)
        self.assertIn('../Servers.jsp', top)
        self.assertIn('../Graphs.jsp', top)
        self.assertIn("w2ui.layoutLeft.load('main', 'webservice/Top.jsp')", js)
        self.assertIn('webservice/panels.css', header)
        build = (ROOT / "ant/build.xml").read_text()
        self.assertIn('tofile="../build/webservice/Top.jsp"', build)
        self.assertIn('file="../build/webservice/Top.jsp"', build)
        self.assertNotIn('src/webservice/TopMini.jsp', build)

    def test_header_replaces_panel_01_and_halves_control_panels(self):
        jsp = (SOURCE / "index.jsp").read_text()
        js = (SOURCE / "workspace.js").read_text()
        css = (SOURCE / "workspace.css").read_text()
        for snippet in ('id="mini"', 'src="images/LomikelOverlap.png"', 'href="@HOME@"',
                        'id="version-dialog"', 'id="version-button"', 'id="reset-workspace"'):
            self.assertTrue(snippet in jsp, snippet)
        for token in ('@VERSION@', '@BUILD@'):
            self.assertTrue(token in jsp, token)
        self.assertTrue('window.reset = function' in js)
        self.assertTrue("load('top'," not in js)
        self.assertTrue("size: '78%'" in js)
        self.assertTrue("size: '10%'" in js)
        self.assertTrue('showModal()' in js or 'showModal()' in jsp)
        self.assertTrue('.version-dialog' in css)

    def test_preview_graph_menu_preserves_interaction_contract(self):
        self.assertTrue((SOURCE / "GraphView.jsp").is_file(), "preview graph fragment missing")
        graph = (SOURCE / "GraphView.jsp").read_text()
        js = (SOURCE / "workspace.js").read_text()
        for token in ('id="graph"', 'id="manip"', 'id="vis"', 'id="select"', 'id="filter"',
                      'id="expandTo"', 'id="expandFrom"', 'id="removeOld"', 'id="zoom"',
                      'id="clusterize"', 'id="stabilize"', 'id="glayout"', 'id="physics"'):
            self.assertIn(token, graph)
        for call in ('clusterByGroups()', 'clusterByHubsize()', 'clusterExpand()', 'fillEdges()',
                     'switchLayout()', 'switchPhysics()', 'applyFilter()'):
            self.assertIn(call, graph)
        self.assertIn('<details', graph)
        self.assertIn('GraphView.css', graph)  # Legacy context action icon classes remain visible.
        self.assertIn('webservice/GraphView.jsp', js)
        self.assertIn('webservice/graph.css', (SOURCE / "index.jsp").read_text())
        self.assertIn('#layout_layoutMain_panel_bottom .w2ui-panel-content', (SOURCE / "graph.css").read_text())
        self.assertNotIn('2.16', graph)  # No viewport-derived magic canvas height.

    @unittest.skipUnless(WAR.exists(), "Build locally with ant war first")
    def test_preview_war_has_substituted_panel_fragments(self):
        with ZipFile(WAR) as archive:
            names = archive.namelist()
            for path in ("webservice/Top.jsp", "webservice/panels.css", "webservice/index.jsp"):
                self.assertEqual(names.count(path), 1, path)
            self.assertNotIn("webservice/TopMini.jsp", names)
            preview = archive.read("webservice/index.jsp").decode()
            top = archive.read("webservice/Top.jsp").decode()
            for token in ("@VERSION@", "@BUILD@", "@NAME@", "@HOME@"):
                self.assertNotIn(token, preview)
            self.assertNotIn("@BOOT@", top)
            self.assertIn("Fink Graph Browser", preview)
            self.assertIn("images/LomikelOverlap.png", names)

    @unittest.skipUnless(WAR.exists(), "Build locally with ant war first")
    def test_built_war_contains_both_shells_and_dependencies(self):
        with ZipFile(WAR) as archive:
            names = set(archive.namelist())
            for name in ("index.jsp", "webservice/index.jsp", "webservice/workspace.js",
                         "webservice/workspace.css", "webservice/GraphView.jsp", "webservice/graph.css",
                         "Top.jsp", "GraphView.jsp", "GraphView.js",
                         "Tabs.jsp", "Feedback.jsp", "WEB-INF/web.xml"):
                self.assertIn(name, names)
            root = archive.read("index.jsp").decode()
            preview = archive.read("webservice/index.jsp").decode()
            self.assertIn("w2ui['layoutLeft'].load", root)
            self.assertIn("webservice/workspace.js", preview)
            self.assertNotIn("@NAME@", preview)
            self.assertNotIn("@ICON@", preview)
            self.assertNotIn("Workspace preview", root)
            self.assertNotIn("webservice/README.md", names)
            self.assertFalse(any(re.match(r"^(?:webservice/)?(?:build-local.properties|.*\.log)$", x)
                                 for x in names))


if __name__ == "__main__":
    unittest.main()

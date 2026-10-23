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
        for name in ("TopMini.jsp", "Top.jsp", "GraphView.jsp", "Tabs.jsp", "Feedback.jsp"):
            self.assertIn("'" + name + "'", js)
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

    @unittest.skipUnless(WAR.exists(), "Build locally with ant war first")
    def test_built_war_contains_both_shells_and_dependencies(self):
        with ZipFile(WAR) as archive:
            names = set(archive.namelist())
            for name in ("index.jsp", "webservice/index.jsp", "webservice/workspace.js",
                         "webservice/workspace.css", "Top.jsp", "GraphView.jsp",
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

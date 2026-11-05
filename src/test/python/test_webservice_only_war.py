"""FinkBrowser must ship from one application source tree."""
from pathlib import Path
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[3]
WAR = ROOT / 'lib/FinkBrowser.war'


class WebserviceOnlyWarTest(unittest.TestCase):
    def test_build_uses_only_webservice_application_sources(self):
        build = (ROOT / 'ant/build.xml').read_text()
        war = build[build.index('<target name="war"'):build.index('</target>', build.index('<target name="war"'))]
        self.assertNotIn('../src/ws', war)
        self.assertNotIn('Lomikel.war', war)
        self.assertIn('destfile="../lib/FinkBrowser.war"', war)
        self.assertIn('webxml="../src/webservice/web.xml"', war)
        for name in ('index.jsp', 'Init.jsp', 'GraphView.jsp', 'LoadPane.js',
                     'AlertsView/index.html', 'LightCurveView/index.html',
                     'ClassificationView/index.html'):
            self.assertTrue((ROOT / 'src/webservice' / name).is_file(), name)

    def test_build_packs_tag_routing_for_the_site(self):
        build = (ROOT / 'ant/build.xml').read_text()
        self.assertIn('<copy file="../src/webservice/TagRouting.properties" tofile="../build/TagRouting.properties"', build)
        self.assertIn('<replace file="../build/TagRouting.properties" token="@SITE@" value="${Finkbrowsersite}"/>', build)
        self.assertIn('<zipfileset file="../build/TagRouting.properties" prefix="WEB-INF"/>', build)
        self.assertIn('<exclude name="TagRouting.properties"/>', build, 'the template is not served from the WAR root')
        routing = (ROOT / 'src/webservice/TagRouting.properties').read_text()
        for line in ('site=@SITE@', 'owner.LSST=CC', 'owner.ZTF=IJCLab',
                     'url.CC=http://134.158.243.144:24450/FinkBrowser/TagObject.jsp',
                     'url.IJCLab=http://157.136.253.253:24450/FinkBrowser/TagObject.jsp'):
            self.assertIn(line, routing.splitlines())

    def test_build_adds_the_site_to_the_browser_title(self):
        build = (ROOT / 'ant/build.xml').read_text()
        self.assertIn('property="Finkbrowsertitle" value="${Finkbrowsername} (${site})" else="${Finkbrowsername}"', build)
        self.assertIn('token="@NAME@" value="${Finkbrowsertitle}"', build)
        self.assertIn('pattern="^[A-Za-z0-9_.-]+$" string="${site}"', build, 'only plain site names reach the HTML')

    @unittest.skipUnless(WAR.exists(), 'Build with ant war first')
    def test_war_root_has_default_app_and_three_launchers(self):
        with zipfile.ZipFile(WAR) as archive:
            names = set(archive.namelist())
            for name in ('index.jsp', 'Top.jsp', 'Init.jsp', 'GraphView.jsp',
                         'AlertsView/index.html', 'LightCurveView/index.html',
                         'ClassificationView/index.html'):
                self.assertIn(name, names)
            index = archive.read('index.jsp').decode()
            self.assertIn('class="workspace"', index)
            self.assertIn('AlertsView/index.html', index)
            self.assertNotIn('@NAME@', index)

    @unittest.skipUnless(WAR.exists(), 'Build with ant war first')
    def test_war_includes_only_canonical_graph_assets(self):
        with zipfile.ZipFile(WAR) as archive:
            names = set(archive.namelist())
            for ext in ('jsp', 'js', 'css'):
                self.assertIn('GraphView.' + ext, names)
                self.assertNotIn('GraphViewNew.' + ext, names)
            self.assertIn('GraphView.js', archive.read('GraphView.jsp').decode())
            self.assertNotIn('graphViewVariant', archive.read('index.jsp').decode())


if __name__ == '__main__':
    unittest.main()

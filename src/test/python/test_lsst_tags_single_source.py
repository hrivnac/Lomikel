"""Canonical LSST tag list consumer and WAR packaging regression checks."""
from pathlib import Path
import re
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[3]
LIST = ROOT / 'src/sh/cron/CC/LSSTTags.lst'
OLD_LIST = ROOT / 'src/work/CC/LSSTTags.lst'
IMPORT = ROOT / 'src/sh/cron/CC/importTags.sh'
PARAMS = ROOT / 'src/webservice/AlertsView/params.js'
BUILD = ROOT / 'ant/build.xml'
WAR = ROOT / 'lib/FinkBrowser.war'


class TagsSourceTests(unittest.TestCase):
    def test_one_valid_unique_canonical_list(self):
        self.assertTrue(LIST.is_file())
        self.assertFalse(OLD_LIST.exists())
        entries = LIST.read_text().splitlines()
        self.assertTrue(entries)
        self.assertEqual(len(entries), len(set(entries)))
        self.assertTrue(all(re.fullmatch(r'rubin\.tag_[a-z0-9_]+', item) for item in entries))

    def test_importer_uses_absolute_canonical_list_not_inline_values(self):
        script = IMPORT.read_text()
        self.assertIn('/home/almalinux/Lomikel/src/sh/cron/CC/LSSTTags.lst', script)
        self.assertIn('mapfile -t TAGS < "${TAGS_LIST}"', script)
        self.assertIn('${#TAGS[@]} == 0', script)
        self.assertIn('for T in "${TAGS[@]}"; do', script)
        self.assertNotRegex(script, r'for\s+T\s+in\s+rubin\.tag_')
        self.assertIn('[[ ! "${T}" =~ ^rubin\\.tag_', script)

    def test_war_stages_one_canonical_list_at_alertsview_root(self):
        build = BUILD.read_text()
        self.assertIn('file="../src/sh/cron/CC/LSSTTags.lst"', build)
        self.assertIn('prefix="AlertsView"', build)
        self.assertIn('<zipfileset file="../src/sh/cron/CC/LSSTTags.lst" prefix="AlertsView"/>', build)

    def test_params_loads_packaged_list_before_initial_refresh(self):
        code = PARAMS.read_text()
        self.assertIn('fetch("LSSTTags.lst"', code)
        self.assertIn('loadLsstTagTables()', code)
        self.assertNotIn('const classesLSSTTags = {', code)

    @unittest.skipUnless(WAR.exists(), 'Build FinkBrowser.war first')
    def test_built_war_has_exact_canonical_list(self):
        with zipfile.ZipFile(WAR) as archive:
            names = archive.namelist()
            self.assertEqual(names.count('AlertsView/LSSTTags.lst'), 1)
            self.assertEqual(archive.read('AlertsView/LSSTTags.lst'), LIST.read_bytes())


if __name__ == '__main__':
    unittest.main()

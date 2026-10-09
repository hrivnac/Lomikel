"""Offline checks of CC script opt-in paths; never execute top-level Groovy."""
from pathlib import Path
import unittest

CC = Path(__file__).resolve().parents[1] / 'work' / 'CC'


class CCFillWiringTest(unittest.TestCase):
    def test_all_cc_scripts_use_optional_day_strict_directory_traversal(self):
        for name in ('fillES-radec.groovy', 'fillES-mjd.groovy', 'fillOCol.groovy'):
            with self.subTest(name=name):
                text = (CC / name).read_text()
                active = '\n'.join(line for line in text.splitlines() if not line.lstrip().startswith('//'))
                self.assertFalse('reader.processDir(' in active, name)
                self.assertFalse('reader.processDirStrict(' in active, name)
                self.assertTrue('reader.processOptionalDirStrict(' in active, name)

    def test_es_scripts_clear_only_at_record_boundary(self):
        for name in ('fillES-radec.groovy', 'fillES-mjd.groovy'):
            with self.subTest(name=name):
                text = (CC / name).read_text()
                self.assertIn('protected void beginRecord()', text)
                self.assertIn('props().clear();', text.split('protected void beginRecord()', 1)[1].split('}', 1)[0])
                self.assertIn('esclient', text)


if __name__ == '__main__':
    unittest.main()

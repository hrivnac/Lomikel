"""The Graph Browser link icon is the one generated from the Lomikel logo.

Run: python3 -m unittest discover -s src/test/python -p 'test_graph_browser_icon.py' -v
The regeneration check needs numpy and Pillow and is skipped without them.
"""
import importlib.util
from pathlib import Path
import struct
import unittest

ROOT = Path(__file__).resolve().parents[3]
ICON = ROOT / "src/webservice/images/LomikelGraphBrowser.png"
LOGO = ROOT / "src/LomikelOverlap.png"
SCRIPT = ROOT / "src/python/make-graph-browser-icon.py"


class GraphBrowserIconTest(unittest.TestCase):
    def test_icon_is_a_64px_rgba_png(self):
        data = ICON.read_bytes()
        self.assertEqual(data[:8], b"\x89PNG\r\n\x1a\n")
        width, height, depth, colour_type = struct.unpack(">IIBB", data[16:26])
        self.assertEqual((width, height, depth, colour_type), (64, 64, 8, 6))

    def test_icon_matches_generator_output(self):
        try:
            import numpy as np
            from PIL import Image
        except ImportError:
            self.skipTest("numpy and Pillow are needed to regenerate the icon")
        spec = importlib.util.spec_from_file_location("make_icon", SCRIPT)
        assert spec is not None and spec.loader is not None
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        fresh = np.asarray(module.make_icon(LOGO, 64)).astype(int)
        stored = np.asarray(Image.open(ICON).convert("RGBA")).astype(int)
        # Allow small resampling differences between Pillow versions.
        self.assertLessEqual(np.abs(fresh - stored).max(), 8)
        # Transparent corners: the icon sits on any background.
        self.assertEqual(stored[0, 0, 3], 0)
        self.assertEqual(stored[-1, -1, 3], 0)


if __name__ == "__main__":
    unittest.main()

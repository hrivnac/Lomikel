import importlib.util
import math
from pathlib import Path
import unittest

import pandas as pd

ROOT = Path(__file__).resolve().parents[1]


def load_preprocessor():
    spec = importlib.util.spec_from_file_location("lightcurve_preprocess", ROOT / "preprocessLC.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class PreprocessLightcurveTest(unittest.TestCase):
    def test_import_is_side_effect_free_and_samples_are_order_invariant(self):
        module = load_preprocessor()
        rows = pd.DataFrame({
            "MJD": [3.0, 1.0, 2.0, 1.0],
            "BAND": ["u", "u", "u", "u"],
            "magnitude": [30.0, 12.0, 20.0, 10.0],
        })

        result = module.unify_lightcurve(rows, ["u", "g"])

        self.assertEqual(result["u"]["times"], [1.0, 2.0, 3.0])
        self.assertEqual(result["u"]["values"], [11.0, 20.0, 30.0])
        self.assertEqual(result["g"]["times"], [])
        self.assertEqual(result["g"]["values"], [])
        self.assertNotIn(0.0, result["g"]["values"])


if __name__ == "__main__":
    unittest.main()

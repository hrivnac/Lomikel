"""Offline contract tests for animate.py (run with python -m unittest test_animate)."""

import importlib.util
import io
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from PIL import Image

SCRIPT = Path(__file__).with_name("animate.py")


def module():
    spec = importlib.util.spec_from_file_location("cutout_animation", SCRIPT)
    assert spec is not None and spec.loader is not None
    result = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = result
    spec.loader.exec_module(result)
    return result


def image_bytes(color, size=(30, 30)):
    out = io.BytesIO()
    Image.new("RGB", size, color).save(out, format="PNG")
    return out.getvalue()


class FakeClient:
    def __init__(self, dia_rows, resolver=None, ss_rows=None, fail_source=None, variable=False):
        self.dia_rows = dia_rows
        self.resolver = resolver or []
        self.ss_rows = ss_rows or []
        self.fail_source = fail_source
        self.variable = variable
        self.calls = []

    def post_json(self, path, body):
        self.calls.append((path, body))
        return {"/api/v1/sources": self.dia_rows,
                "/api/v1/resolver": self.resolver,
                "/api/v1/sso": self.ss_rows}[path]

    def cutout(self, source_id, kind):
        self.calls.append(("/api/v1/cutouts", source_id, kind))
        if source_id == self.fail_source:
            raise RuntimeError("cutout unavailable")
        return image_bytes({"101": "red", "102": "green", "103": "blue"}[source_id],
                           (34, 34) if self.variable and source_id == "103" else (30, 30))


class AnimationTests(unittest.TestCase):
    def test_dia_history_is_sorted_by_source_time_not_api_order(self):
        rows = [{"r:diaObjectId": 1234, "r:diaSourceId": int(s),
                 "r:midpointMjdTai": mjd, "r:band": "g"}
                for s, mjd in [("103", 3), ("101", 1), ("102", 2)]]
        kind, sources = module().fetch_history("1234", FakeClient(rows))
        self.assertEqual(kind, "dia")
        self.assertEqual([s.source_id for s in sources], ["101", "102", "103"])

    def test_ss_history_requires_exact_identity_and_sorts_by_mjd(self):
        rows = [{"r:ssObjectId": 9876, "r:diaSourceId": int(s),
                 "r:midpointMjdTai": mjd, "r:band": "r"}
                for s, mjd in [("103", 3), ("101", 1), ("102", 2)]]
        client = FakeClient([], [{"r:ssObjectId": "9876",
                                  "r:packed_primary_provisional_designation": "K02P26Y"}], rows)
        kind, sources = module().fetch_history("9876", client)
        self.assertEqual(kind, "ss")
        self.assertEqual([s.source_id for s in sources], ["101", "102", "103"])
        self.assertEqual(client.calls[-1][1]["n_or_d"], "K02P26Y")
        rows[1]["r:ssObjectId"] = 1111
        with self.assertRaisesRegex(ValueError, "identity"):
            module().fetch_history("9876", client)

    def test_animation_is_chronological_and_atomic(self):
        sources = [module().Source(str(i), float(i), "g") for i in (101, 102, 103)]
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "cutouts.gif"
            client = FakeClient([])
            module().write_gif(sources, output, client, "Difference", 120, 4)
            with Image.open(output) as gif:
                self.assertEqual(getattr(gif, "n_frames"), 3)
                self.assertGreaterEqual(gif.size[0], 120)
                self.assertEqual([gif.seek(i) or gif.convert("RGB").getpixel((50, 50))
                                  for i in range(3)], [(255, 0, 0), (0, 128, 0), (0, 0, 255)])
            self.assertEqual([c[1] for c in client.calls], ["101", "102", "103"])
            output.unlink()
            with self.assertRaisesRegex(RuntimeError, "unavailable"):
                module().write_gif(sources, output, FakeClient([], fail_source="102"),
                                   "Difference", 120, 4)
            self.assertFalse(output.exists())

    def test_bounded_sampling_includes_first_and_last(self):
        sources = [module().Source(str(i), float(i), "g") for i in range(101, 110)]
        self.assertEqual([s.source_id for s in module().select_frames(sources, 3)],
                         ["101", "105", "109"])

    def test_real_cutouts_of_different_sizes_are_centered_on_equal_frames(self):
        sources = [module().Source(str(i), float(i), "g") for i in (101, 102, 103)]
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "variable.gif"
            module().write_gif(sources, output, FakeClient([], variable=True),
                               "Difference", 120, 4)
            with Image.open(output) as gif:
                self.assertEqual(getattr(gif, "n_frames"), 3)
                self.assertGreaterEqual(gif.size[0], 136)
                self.assertEqual(gif.size[1], 172)
                for i in range(3):
                    gif.seek(i)
                    self.assertNotEqual(gif.convert("RGB").getpixel((gif.width // 2, 60)), (0, 0, 0))

    def test_scale_one_keeps_full_source_label(self):
        from PIL import ImageDraw
        source = module().Source("123456789012345678", 61000.25, "g")
        client = FakeClient([])
        client.cutout = lambda source_id, kind: image_bytes("red")
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "low-scale.gif"
            module().write_gif([source], output, client, "Difference", 120, 1)
            with Image.open(output) as gif:
                label_width = ImageDraw.Draw(Image.new("RGB", (1, 1))).textbbox(
                    (0, 0), f"source {source.source_id}")[2]
                self.assertGreaterEqual(gif.width, label_width + 8)

    def test_memory_budget_rejects_before_replacing_existing_gif(self):
        animation = module()
        setattr(animation, "MAX_FRAME_RGB_BYTES", 1000)
        sources = [animation.Source(str(i), float(i), "g") for i in (101, 102, 103)]
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "old.gif"
            output.write_bytes(b"previous")
            with self.assertRaisesRegex(ValueError, "memory budget"):
                animation.write_gif(sources, output, FakeClient([]), "Difference", 120, 4)
            self.assertEqual(output.read_bytes(), b"previous")

    def test_help_and_invalid_objectid(self):
        result = subprocess.run([sys.executable, str(SCRIPT), "--help"], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("objectid", result.stdout)
        with self.assertRaisesRegex(ValueError, "objectid"):
            module().fetch_history("0007;rm", FakeClient([]))


if __name__ == "__main__":
    unittest.main()

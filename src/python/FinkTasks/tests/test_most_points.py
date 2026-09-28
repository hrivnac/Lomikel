import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from fink_tasks.most_points import (
    RankedObject,
    ResolverMatch,
    _validate_service_url,
    build_lightcurve_document,
    build_ranking_query,
    fetch_ranked_objects,
    fetch_ss_lightcurve,
    main,
    normalize_ss_lightcurve,
    parse_radec_response,
    parse_ranking_response,
    plot_ss_lightcurve,
    select_resolver_match,
    selected_object_types,
)


class ElasticsearchRankingTests(unittest.TestCase):
    def test_query_ranks_by_mjd_cardinality(self):
        query = build_ranking_query(3)
        self.assertEqual(query["size"], 3)
        script_sort = query["sort"][0]["_script"]
        self.assertEqual(script_sort["script"]["source"], "doc['mjd'].size()")
        self.assertEqual(script_sort["order"], "desc")

    def test_parses_and_verifies_ranked_objects(self):
        response = {
            "hits": {
                "hits": [
                    {"_id": "object-a", "sort": [3.0], "_source": {"mjd": [1, 2, 3]}},
                    {"_id": "object-b", "sort": [1.0], "_source": {"mjd": 4}},
                ]
            }
        }
        rows = parse_ranking_response(response, "ss")
        self.assertEqual(
            [(row.object_id, row.point_count) for row in rows],
            [("object-a", 3), ("object-b", 1)],
        )
        self.assertEqual(rows[0].mjd_min, 1.0)
        self.assertEqual(rows[0].mjd_max, 3.0)

    def test_rejects_count_disagreement(self):
        response = {
            "hits": {
                "hits": [
                    {"_id": "object-a", "sort": [2.0], "_source": {"mjd": [1]}}
                ]
            }
        }
        with self.assertRaisesRegex(ValueError, "cardinality"):
            parse_ranking_response(response, "dia")

    def test_rejects_non_finite_mjd_values(self):
        for value in (float("nan"), float("inf"), float("-inf")):
            with self.subTest(value=value):
                response = {
                    "hits": {
                        "hits": [
                            {"_id": "object-a", "sort": [1], "_source": {"mjd": [value]}}
                        ]
                    }
                }
                with self.assertRaisesRegex(ValueError, "finite MJD"):
                    parse_ranking_response(response, "ss")

    def test_rejects_non_finite_or_fractional_script_cardinality(self):
        for count in (float("nan"), float("inf"), float("-inf"), 1.5):
            with self.subTest(count=count):
                response = {
                    "hits": {
                        "hits": [
                            {"_id": "object-a", "sort": [count], "_source": {"mjd": [1]}}
                        ]
                    }
                }
                with self.assertRaisesRegex(ValueError, "cardinality"):
                    parse_ranking_response(response, "ss")

    def test_malformed_elasticsearch_rows_raise_value_error(self):
        malformed_responses = [
            {"hits": {"hits": [None]}},
            {"hits": {"hits": [{"sort": [1], "_source": {"mjd": [1]}}]}},
            {"hits": {"hits": [{"_id": "a", "sort": [], "_source": {"mjd": [1]}}]}},
            {"hits": {"hits": [{"_id": "a", "sort": [1], "_source": []}]}},
        ]
        for response in malformed_responses:
            with self.subTest(response=response):
                with self.assertRaises(ValueError):
                    parse_ranking_response(response, "ss")

        malformed_radec = {"docs": [None]}
        with self.assertRaises(ValueError):
            parse_radec_response(malformed_radec, ["a"])

    def test_crosschecks_coordinate_cardinality_in_paired_index(self):
        response = {
            "docs": [
                {"_id": "ss-a", "found": True, "_source": {"location": [{}, {}]}},
                {"_id": "dia-a", "found": True, "_source": {"location": {}}},
            ]
        }
        self.assertEqual(
            parse_radec_response(response, ["ss-a", "dia-a"]),
            {"ss-a": 2, "dia-a": 1},
        )

    def test_rejects_missing_paired_coordinate_document(self):
        response = {"docs": [{"_id": "missing", "found": False}]}
        with self.assertRaisesRegex(ValueError, "not found"):
            parse_radec_response(response, ["missing"])


class SolarSystemLightcurveTests(unittest.TestCase):
    def test_selects_exact_reverse_resolver_match(self):
        rows = [
            {
                "r:ssObjectId": "other",
                "r:unpacked_primary_provisional_designation": "Other",
            },
            {
                "r:ssObjectId": "123",
                "r:packed_primary_provisional_designation": "K01A36R",
                "r:unpacked_primary_provisional_designation": "2001 AR36",
            },
        ]
        match = select_resolver_match(rows, "123")
        self.assertEqual(match.unpacked_designation, "2001 AR36")
        self.assertEqual(match.packed_designation, "K01A36R")

    def test_normalizes_all_bands_and_sorts_by_time(self):
        rows = [
            {"r:diaSourceId": 2, "r:ssObjectId": 123, "r:midpointMjdTai": 20, "r:band": "r"},
            {"r:diaSourceId": 1, "r:ssObjectId": 123, "r:midpointMjdTai": 10, "r:band": "g"},
            {"r:diaSourceId": 3, "r:ssObjectId": 123, "r:midpointMjdTai": 30, "r:band": "i"},
        ]
        normalized = normalize_ss_lightcurve(rows, "123")
        self.assertEqual([row["r:band"] for row in normalized], ["g", "r", "i"])
        document = build_lightcurve_document("123", "2001 AR36", 3, normalized)
        self.assertEqual(document["bands"], {"g": 1, "i": 1, "r": 1})
        self.assertEqual(document["rest_source_count"], 3)

    def test_rejects_duplicate_source_ids(self):
        rows = [
            {"r:diaSourceId": 1, "r:ssObjectId": 123, "r:midpointMjdTai": 10, "r:band": "g"},
            {"r:diaSourceId": 1, "r:ssObjectId": 123, "r:midpointMjdTai": 11, "r:band": "r"},
        ]
        with self.assertRaisesRegex(ValueError, "duplicate diaSourceId"):
            normalize_ss_lightcurve(rows, "123")

    def test_malformed_resolver_rows_raise_value_error(self):
        for rows in ([None], [{"r:ssObjectId": "123"}]):
            with self.subTest(rows=rows):
                with self.assertRaises(ValueError):
                    select_resolver_match(rows, "123")

    def test_requires_present_nonempty_source_id(self):
        for source_id in (None, "", "   "):
            with self.subTest(source_id=source_id):
                rows = [
                    {
                        "r:diaSourceId": source_id,
                        "r:ssObjectId": 123,
                        "r:midpointMjdTai": 10,
                        "r:band": "g",
                    }
                ]
                with self.assertRaisesRegex(ValueError, "diaSourceId"):
                    normalize_ss_lightcurve(rows, "123")

    def test_requires_finite_lightcurve_mjd(self):
        for mjd in ("not-a-number", float("nan"), float("inf"), float("-inf")):
            with self.subTest(mjd=mjd):
                rows = [
                    {
                        "r:diaSourceId": 1,
                        "r:ssObjectId": 123,
                        "r:midpointMjdTai": mjd,
                        "r:band": "g",
                    }
                ]
                with self.assertRaisesRegex(ValueError, "finite MJD"):
                    normalize_ss_lightcurve(rows, "123")

    def test_malformed_sso_rows_raise_value_error(self):
        for rows in ([None], [["not", "an", "object"]]):
            with self.subTest(rows=rows):
                with self.assertRaises(ValueError):
                    normalize_ss_lightcurve(rows, "123")

    def test_plot_reports_missing_matplotlib_cleanly(self):
        real_import = __import__

        def without_matplotlib(name, *args, **kwargs):
            if name == "matplotlib":
                raise ModuleNotFoundError("No module named 'matplotlib'")
            return real_import(name, *args, **kwargs)

        with patch("builtins.__import__", side_effect=without_matplotlib):
            with self.assertRaisesRegex(
                RuntimeError, r'python3 -m pip install "matplotlib>=3\.7"'
            ):
                plot_ss_lightcurve({"sources": []}, Path("unused.png"))

    def test_plot_contains_valid_png_signature(self):
        sources = [
            {
                "r:diaSourceId": 1,
                "r:ssObjectId": 123,
                "r:midpointMjdTai": 10,
                "r:band": "g",
                "r:scienceFlux": 100.0,
                "r:scienceFluxErr": 2.0,
                "r:psfFlux": -5.0,
                "r:psfFluxErr": 1.0,
            },
            {
                "r:diaSourceId": 2,
                "r:ssObjectId": 123,
                "r:midpointMjdTai": 11,
                "r:band": "r",
                "r:scienceFlux": 120.0,
                "r:scienceFluxErr": 3.0,
                "r:psfFlux": 8.0,
                "r:psfFluxErr": 1.5,
            },
        ]
        document = build_lightcurve_document("123", "Example", 2, sources)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "lightcurve.png"
            plot_ss_lightcurve(document, path)
            self.assertEqual(path.read_bytes()[:8], b"\x89PNG\r\n\x1a\n")
            self.assertGreater(path.stat().st_size, 1000)


class NetworkWorkflowTests(unittest.TestCase):
    def test_fetch_ranked_objects_uses_matching_mjd_index(self):
        response = {
            "hits": {
                "hits": [
                    {"_id": "1", "sort": [2.0], "_source": {"mjd": [10, 11]}}
                ]
            }
        }
        radec_response = {
            "docs": [
                {"_id": "1", "found": True, "_source": {"location": [{}, {}]}}
            ]
        }
        with patch(
            "fink_tasks.most_points._post_json",
            side_effect=[response, radec_response],
        ) as post:
            rows = fetch_ranked_objects("http://es.test", "ss", 1, 30.0)
        self.assertEqual(rows[0].point_count, 2)
        self.assertEqual(rows[0].radec_point_count, 2)
        self.assertEqual(post.call_args_list[0].args[0], "http://es.test/ss_mjd/_search")
        self.assertEqual(post.call_args_list[1].args[0], "http://es.test/ss_radec/_mget")
        self.assertFalse(post.call_args_list[0].kwargs["allow_redirects"])

    def test_fetch_ss_lightcurve_uses_packed_resolver_designation_for_sso(self):
        resolver = [
            {
                "r:ssObjectId": "123",
                "r:packed_primary_provisional_designation": "J21V00A",
                "r:unpacked_primary_provisional_designation": "A921 VA",
            }
        ]
        sso = [
            {
                "r:diaSourceId": 1,
                "r:ssObjectId": 123,
                "r:midpointMjdTai": 10,
                "r:band": "g",
                "r:scienceFlux": 1.0,
                "r:psfFlux": 2.0,
            }
        ]
        with patch(
            "fink_tasks.most_points._post_json", side_effect=[resolver, sso]
        ) as post:
            match, rows = fetch_ss_lightcurve(
                "https://api.example.test", "123", 30.0
            )
        self.assertEqual(match.unpacked_designation, "A921 VA")
        self.assertEqual(len(rows), 1)
        self.assertEqual(post.call_args_list[1].args[1]["n_or_d"], "J21V00A")
        self.assertFalse(post.call_args_list[1].kwargs["allow_redirects"])


class CliSelectionTests(unittest.TestCase):
    def test_single_file_copy_runs_help_as_executable(self):
        source = Path(__file__).parents[1] / "src/fink_tasks/most_points.py"
        with tempfile.TemporaryDirectory() as tmp:
            copied = Path(tmp) / "most_points.py"
            shutil.copyfile(source, copied)
            copied.chmod(0o755)
            completed = subprocess.run(
                [str(copied), "--help"],
                text=True,
                capture_output=True,
                check=False,
            )
        self.assertEqual(completed.returncode, 0, completed.stderr)
        self.assertIn("usage: most_points.py", completed.stdout)

    def test_service_urls_reject_userinfo(self):
        urls = (
            "https://user@example.test",
            "https://user:" + "p@example.test",
            "http://user@es.example.test",
            "http://:p@es.example.test",
        )
        for url in urls:
            with self.subTest(url=url):
                with self.assertRaisesRegex(ValueError, "userinfo"):
                    _validate_service_url(url, "service", True)

    def test_cli_rejects_es_userinfo_before_request(self):
        with patch("fink_tasks.most_points.fetch_ranked_objects") as fetch:
            with self.assertRaises(SystemExit) as raised:
                main(
                    [
                        "--allow-insecure-es",
                        "--es-url",
                        "http://" + "user:s@es.example.test",
                    ]
                )
        self.assertEqual(raised.exception.code, 2)
        fetch.assert_not_called()

    def test_default_selection_contains_both_types(self):
        self.assertEqual(selected_object_types("both"), ["ss", "dia"])

    def test_single_selection_contains_only_requested_type(self):
        self.assertEqual(selected_object_types("ss"), ["ss"])
        self.assertEqual(selected_object_types("dia"), ["dia"])

    def test_main_writes_both_rankings_and_ss_lightcurve_artifacts(self):
        def ranked(_url, object_type, _count, _timeout):
            return [RankedObject(object_type, "1", 1, 10.0, 10.0)]

        sources = [
            {
                "r:diaSourceId": 11,
                "r:ssObjectId": 1,
                "r:midpointMjdTai": 10.0,
                "r:band": "g",
                "r:scienceFlux": 1.0,
                "r:psfFlux": 2.0,
            }
        ]
        resolver = ResolverMatch("1", "PACKED", "2001 AB")
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            with (
                patch("fink_tasks.most_points.fetch_ranked_objects", side_effect=ranked),
                patch(
                    "fink_tasks.most_points.fetch_ss_lightcurve",
                    return_value=(resolver, sources),
                ),
                patch(
                    "fink_tasks.most_points.plot_ss_lightcurve",
                    side_effect=lambda _doc, path: path.write_bytes(b"PNG"),
                ),
            ):
                self.assertEqual(
                    main(
                        [
                            "--allow-insecure-es",
                            "--results",
                            "1",
                            "--lightcurves",
                            "--output-dir",
                            str(output),
                        ]
                    ),
                    0,
                )
            expected = {
                "ss_most_points.json",
                "dia_most_points.json",
                "ss_1_lightcurve.json",
                "ss_1_lightcurve.png",
                "manifest.json",
            }
            self.assertTrue(expected.issubset({path.name for path in output.iterdir()}))
            ranking = json.loads((output / "ss_most_points.json").read_text())
            self.assertEqual(ranking["objects"][0]["point_count"], 1)


if __name__ == "__main__":
    unittest.main()

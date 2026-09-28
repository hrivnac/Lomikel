import io
import json
import math
import threading
import unittest
from contextlib import redirect_stdout
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from unittest.mock import patch

from fink_tasks.object_neighbors import (
    Neighbor,
    RankedNeighbor,
    _format_rest_value,
    _json_ready,
    _post_json,
    angular_distance_arcsec,
    fetch_object_data,
    fetch_positions,
    parse_neighborhood_response,
    parse_rest_columns,
    print_table,
    sort_neighbors,
    tie_boundary_is_complete,
    trim_ranked_results,
    validate_graph_transport,
    validate_nmax,
    validate_safe_token,
)


class _RedirectHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        self.send_response(302)
        self.send_header("Location", "/final")
        self.end_headers()

    def do_GET(self):
        body = b'{"followed": true}'
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        pass


class TransportTests(unittest.TestCase):
    def test_post_json_can_disable_redirects_for_graph_integrity(self):
        server = ThreadingHTTPServer(("127.0.0.1", 0), _RedirectHandler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            url = f"http://127.0.0.1:{server.server_port}/graph"
            with self.assertRaisesRegex(RuntimeError, "HTTP 302"):
                _post_json(url, {"gremlin": "g.V().count()"}, 5.0, allow_redirects=False)
        finally:
            server.shutdown()
            server.server_close()
            thread.join()


class NeighborhoodParsingTests(unittest.TestCase):
    def test_parses_graphson_map_entries_and_class_weights(self):
        response = {
            "status": {"code": 200, "message": ""},
            "result": {
                "data": {
                    "@type": "g:List",
                    "@value": [
                        {
                            "@type": "g:Map",
                            "@value": [
                                {
                                    "@type": "g:Map",
                                    "@value": [
                                        "object-b",
                                        {"@type": "g:Double", "@value": 0.25},
                                    ],
                                },
                                {
                                    "@type": "g:Map",
                                    "@value": [
                                        "class-a",
                                        {"@type": "g:Double", "@value": 0.75},
                                        "class-b",
                                        {"@type": "g:Double", "@value": 0.25},
                                    ],
                                },
                            ],
                        }
                    ],
                }
            },
        }

        self.assertEqual(
            parse_neighborhood_response(response),
            [
                Neighbor(
                    object_id="object-b",
                    graph_distance=0.25,
                    classes={"class-a": 0.75, "class-b": 0.25},
                )
            ],
        )

    def test_rejects_unsuccessful_gremlin_status(self):
        response = {"status": {"code": 500, "message": "failed"}}
        with self.assertRaisesRegex(RuntimeError, "Gremlin status 500"):
            parse_neighborhood_response(response)

    def test_rejects_non_finite_graph_distance(self):
        response = {
            "status": {"code": 200},
            "result": {
                "data": {
                    "@value": [
                        {
                            "@value": [
                                {"@value": ["object-b", {"@value": "NaN"}]},
                                {"@value": ["class-a", {"@value": 1.0}]},
                            ]
                        }
                    ]
                }
            },
        }
        with self.assertRaisesRegex(ValueError, "graph distance"):
            parse_neighborhood_response(response)

    def test_rejects_invalid_rest_coordinates(self):
        with patch(
            "fink_tasks.object_neighbors._post_json",
            return_value=[{"r:diaObjectId": 1, "r:ra": "NaN", "r:dec": 0.0}],
        ):
            with self.assertRaisesRegex(ValueError, "RA"):
                fetch_positions(["1"], "https://api.example.test", 10.0)


class RestColumnTests(unittest.TestCase):
    def test_parses_repeated_and_comma_separated_columns(self):
        self.assertEqual(
            parse_rest_columns(
                ["r:g_psfFluxMax,r:nDiaSources", "f:main_label_classifier"]
            ),
            ["r:g_psfFluxMax", "r:nDiaSources", "f:main_label_classifier"],
        )

    def test_rejects_unsafe_rest_column(self):
        with self.assertRaisesRegex(ValueError, "REST column"):
            parse_rest_columns(["r:ra,evil column"])

    def test_fetches_requested_columns_alongside_coordinates(self):
        response = [
            {
                "r:diaObjectId": 1,
                "r:ra": 12.0,
                "r:dec": -4.0,
                "r:g_psfFluxMax": 42.5,
            }
        ]
        with patch("fink_tasks.object_neighbors._post_json", return_value=response) as post:
            data = fetch_object_data(
                ["1"],
                "https://api.example.test",
                10.0,
                ["r:g_psfFluxMax"],
            )
        self.assertEqual(data["1"]["r:g_psfFluxMax"], 42.5)
        payload = post.call_args.args[1]
        self.assertEqual(
            payload["columns"],
            "r:diaObjectId,r:ra,r:dec,r:g_psfFluxMax",
        )

    def test_ranked_neighbor_carries_requested_rest_values(self):
        ranked = sort_neighbors(
            [Neighbor("candidate", 0.0, {})],
            (0.0, 0.0),
            {"candidate": (1.0, 0.0)},
            {"candidate": {"r:g_psfFluxMax": 42.5}},
        )
        self.assertEqual(ranked[0].rest_values, {"r:g_psfFluxMax": 42.5})

    def test_json_output_normalizes_non_finite_rest_values(self):
        row = RankedNeighbor(
            Neighbor("candidate", 0.0, {}),
            1.0,
            2.0,
            3.0,
            {"r:x": float("nan"), "r:nested": [float("inf"), 4.0]},
        )
        encoded = json.dumps(_json_ready(row), allow_nan=False)
        decoded = json.loads(encoded)
        self.assertIsNone(decoded["rest"]["r:x"])
        self.assertEqual(decoded["rest"]["r:nested"], [None, 4.0])

    def test_table_displays_target_rest_values(self):
        output = io.StringIO()
        with redirect_stdout(output):
            print_table(
                [],
                ["r:g_psfFluxMax"],
                "target-id",
                {"r:g_psfFluxMax": 1017.82635},
            )
        self.assertIn("target-id", output.getvalue())
        self.assertIn("1017.82635", output.getvalue())

    def test_table_escapes_control_characters_in_rest_strings(self):
        self.assertEqual(_format_rest_value("line1\nline2\t\x1b"), '"line1\\nline2\\t\\u001b"')


class DistanceTests(unittest.TestCase):
    def test_angular_distance_is_zero_for_same_position(self):
        self.assertEqual(angular_distance_arcsec(10.0, -20.0, 10.0, -20.0), 0.0)

    def test_angular_distance_handles_ra_wraparound(self):
        distance = angular_distance_arcsec(359.9, 0.0, 0.1, 0.0)
        self.assertAlmostEqual(distance, 720.0, places=6)

    def test_sorts_by_graph_distance_then_angular_distance(self):
        neighbors = [
            Neighbor("far-same-graph", 0.1, {}),
            Neighbor("closer-worse-graph", 0.2, {}),
            Neighbor("near-same-graph", 0.1, {}),
        ]
        positions = {
            "far-same-graph": (1.0, 0.0),
            "closer-worse-graph": (0.01, 0.0),
            "near-same-graph": (0.1, 0.0),
        }

        ranked = sort_neighbors(neighbors, (0.0, 0.0), positions)

        self.assertEqual(
            [row.neighbor.object_id for row in ranked],
            ["near-same-graph", "far-same-graph", "closer-worse-graph"],
        )

    def test_missing_position_sorts_last_within_equal_graph_distance(self):
        neighbors = [Neighbor("missing", 0.0, {}), Neighbor("located", 0.0, {})]
        ranked = sort_neighbors(neighbors, (0.0, 0.0), {"located": (1.0, 0.0)})
        self.assertEqual([row.neighbor.object_id for row in ranked], ["located", "missing"])
        self.assertTrue(math.isinf(ranked[-1].angular_distance_arcsec))


class ResultLimitTests(unittest.TestCase):
    def test_accepts_positive_integer_result_count(self):
        self.assertEqual(validate_nmax(10.0), 10.0)

    def test_accepts_relative_gap_cutoff_between_zero_and_one(self):
        self.assertEqual(validate_nmax(0.2), 0.2)

    def test_zero_requests_all_graph_neighbors(self):
        self.assertEqual(validate_nmax(0.0), 0.0)

    def test_rejects_negative_values(self):
        with self.assertRaisesRegex(ValueError, "non-negative"):
            validate_nmax(-0.1)

    def test_rejects_fractional_counts_above_one(self):
        with self.assertRaisesRegex(ValueError, "whole number"):
            validate_nmax(10.5)

    def test_accepts_safe_object_and_classifier_tokens(self):
        self.assertEqual(validate_safe_token("170028486134595648", "object ID"), "170028486134595648")
        self.assertEqual(validate_safe_token("FINK=default", "classifier"), "FINK=default")

    def test_rejects_gremlin_code_in_tokens(self):
        with self.assertRaisesRegex(ValueError, "object ID"):
            validate_safe_token("x');g.V().drop();//", "object ID")

    def test_remote_plaintext_graph_requires_explicit_opt_in(self):
        with self.assertRaisesRegex(ValueError, "allow-insecure-graph"):
            validate_graph_transport("http://example.test:24444", False)
        self.assertEqual(
            validate_graph_transport("http://example.test:24444", True),
            "http://example.test:24444",
        )

    def test_https_and_loopback_graph_transports_are_accepted(self):
        self.assertEqual(
            validate_graph_transport("https://graph.example.test", False),
            "https://graph.example.test",
        )
        self.assertEqual(
            validate_graph_transport("http://127.0.0.1:24444", False),
            "http://127.0.0.1:24444",
        )

    def test_detects_incomplete_tie_at_probe_boundary(self):
        rows = [Neighbor(str(i), 0.0, {}) for i in range(4)]
        self.assertFalse(tie_boundary_is_complete(rows, requested_count=2, probe_size=4))

    def test_detects_complete_tie_when_later_distance_is_present(self):
        rows = [
            Neighbor("a", 0.0, {}),
            Neighbor("b", 0.0, {}),
            Neighbor("c", 0.25, {}),
            Neighbor("d", 0.5, {}),
        ]
        self.assertTrue(tie_boundary_is_complete(rows, requested_count=2, probe_size=4))

    def test_trims_only_count_mode_after_angular_tie_break(self):
        neighbors = [Neighbor("far", 0.0, {}), Neighbor("near", 0.0, {})]
        ranked = sort_neighbors(
            neighbors,
            (0.0, 0.0),
            {"far": (1.0, 0.0), "near": (0.1, 0.0)},
        )
        self.assertEqual(
            [row.neighbor.object_id for row in trim_ranked_results(ranked, 1.0)],
            ["near"],
        )
        self.assertEqual(len(trim_ranked_results(ranked, 0.2)), 2)


if __name__ == "__main__":
    unittest.main()

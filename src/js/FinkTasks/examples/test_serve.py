"""Local-only regression tests for the Python-backed browser examples."""

import json
import subprocess
import sys
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from pathlib import Path
from unittest.mock import patch

from serve import (Handler, ThreadingHTTPServer, LimitExceeded, MAX_FILE,
                   MAX_OUTPUT, MAX_RUNS, run_size, run_task, script_arguments)


class ArgumentsTest(unittest.TestCase):
    def test_result_count_cap_preserves_zero_and_fractional_cutoff(self):
        for result in (0, 0.5, 100):
            args, _ = script_arguments("object-neighbors", {"objectId": "x", "results": result})
            self.assertEqual(args[args.index("--results") + 1], str(result))
        for task in ("object-neighbors", "most-points"):
            with self.assertRaises(ValueError):
                script_arguments(task, {"objectId": "x", "results": 101} if task == "object-neighbors" else {"results": 101})
        with self.assertRaises(ValueError):
            script_arguments("most-points", {"results": 0.5})

    def test_trusted_loopback_client_can_choose_service_urls(self):
        args, _ = script_arguments("object-neighbors", {
            "objectId": "x", "apiUrl": "http://127.0.0.1:1234",
            "graphUrl": "http://127.0.0.1:5678"})
        self.assertEqual(args[args.index("--api-url") + 1], "http://127.0.0.1:1234")
        self.assertEqual(args[args.index("--graph-url") + 1], "http://127.0.0.1:5678")

    def test_python_defaults_and_repeatable_columns(self):
        command, directory = script_arguments("object-neighbors", {
            "objectId": "170028486134595648", "restColumns": ["r:a,r:b", "r:c"]
        })
        self.assertIsNone(directory)
        self.assertEqual(command[-1], "170028486134595648")
        self.assertEqual(command.count("--rest-columns"), 2)
        self.assertEqual(command[command.index("--graph-url") + 1], "http://134.158.243.144:24444")
        self.assertEqual(command[command.index("--results") + 1], "10")
        self.assertNotIn("--allow-insecure-graph", command)
        ranking, directory = script_arguments("most-points", {})
        self.assertEqual(directory, "fink-most-points-output")
        self.assertEqual(ranking[ranking.index("--object-type") + 1], "both")
        self.assertEqual(ranking[ranking.index("--es-url") + 1], "http://134.158.243.139:24499")

    def test_rejects_unknown_fields_and_output_path_escape(self):
        for body in ({"outputDir": "../escape"}, {"outputDir": "/tmp/out"}, {"unknown": "x"}):
            with self.subTest(body=body), self.assertRaises(ValueError):
                script_arguments("most-points", body)
        with self.assertRaises(ValueError):
            script_arguments("object-neighbors", {"objectId": "x';rm -rf /"})


class ServerTest(unittest.TestCase):
    def test_host_origin_and_path_rejection(self):
        with tempfile.TemporaryDirectory() as root:
            Handler.runs = Path(root)
            server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            base = f"http://127.0.0.1:{server.server_port}"
            try:
                for headers in ({"Host": "evil.example"}, {"Origin": "http://evil.example"}):
                    request = urllib.request.Request(base + "/run/most-points", data=b"{}",
                        headers={"Content-Type": "application/json", **headers})
                    with self.subTest(headers=headers), self.assertRaises(urllib.error.HTTPError) as error:
                        urllib.request.urlopen(request)
                    self.assertEqual(error.exception.code, 403)
                for path in ("/artifacts/../x/y", "/artifacts/not-a-run/../x", "/examples/serve.py"):
                    with self.subTest(path=path), self.assertRaises(urllib.error.HTTPError) as error:
                        urllib.request.urlopen(base + path)
                    self.assertEqual(error.exception.code, 404)
                folder = Path(root) / "valid" / "output"
                folder.mkdir(parents=True)
                (folder / "large.json").write_bytes(b"x" * 32)
                (folder / "link.json").symlink_to(folder / "large.json")
                with patch("serve.MAX_FILE", 16):
                    with self.assertRaises(urllib.error.HTTPError) as error:
                        urllib.request.urlopen(base + "/artifacts/valid/output/large.json")
                    self.assertEqual(error.exception.code, 413)
                    self.assertLess(len(error.exception.read()), 512)
                with self.assertRaises(urllib.error.HTTPError) as error:
                    urllib.request.urlopen(base + "/artifacts/valid/output/link.json")
                self.assertEqual(error.exception.code, 404)
                with patch("serve.Path.open", side_effect=OSError("private path")):
                    with self.assertRaises(urllib.error.HTTPError) as error:
                        urllib.request.urlopen(base + "/artifacts/valid/output/large.json")
                    self.assertEqual(error.exception.code, 500)
                    self.assertNotIn(b"private path", error.exception.read())
            finally:
                server.shutdown()
                server.server_close()
                thread.join(timeout=2)

    def test_huge_json_integer_returns_bounded_400(self):
        with tempfile.TemporaryDirectory() as root:
            Handler.runs = Path(root)
            server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            base = f"http://127.0.0.1:{server.server_port}"
            try:
                for field in ("results", "timeout"):
                    payload = json.dumps({field: 10 ** 1000}).encode()
                    request = urllib.request.Request(base + "/run/most-points", data=payload,
                        headers={"Content-Type": "application/json", "Origin": base})
                    with self.subTest(field=field), self.assertRaises(urllib.error.HTTPError) as error:
                        urllib.request.urlopen(request)
                    self.assertEqual(error.exception.code, 400)
                    self.assertLess(len(error.exception.read()), 512)
            finally:
                server.shutdown()
                server.server_close()
                thread.join(timeout=2)

    def test_busy_and_oserror_are_bounded_json(self):
        with tempfile.TemporaryDirectory() as root:
            Handler.runs = Path(root)
            server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            base = f"http://127.0.0.1:{server.server_port}"
            entered, release = threading.Event(), threading.Event()
            def slow_run(*args, **kwargs):
                entered.set()
                release.wait(3)
                raise OSError("secret filesystem details " + "x" * 10000)
            def request():
                return urllib.request.Request(base + "/run/most-points", data=b"{}",
                    headers={"Content-Type": "application/json", "Origin": base})
            first_result = []
            def read_first():
                try:
                    urllib.request.urlopen(request())
                    first_result.append((200, b""))
                except urllib.error.HTTPError as error:
                    first_result.append((error.code, error.read()))
            try:
                with patch("serve.run_task", side_effect=slow_run):
                    first = threading.Thread(target=read_first, daemon=True)
                    first.start()
                    self.assertTrue(entered.wait(2))
                    with self.assertRaises(urllib.error.HTTPError) as error:
                        urllib.request.urlopen(request())
                    self.assertEqual(error.exception.code, 429)
                    self.assertLess(len(error.exception.read()), 512)
                    release.set()
                    first.join(timeout=3)
                    self.assertEqual(len(first_result), 1)
                    self.assertEqual(first_result[0][0], 500)
                    self.assertLess(len(first_result[0][1]), 512)
                    self.assertNotIn(b"secret", first_result[0][1])
            finally:
                release.set()
                server.shutdown()
                server.server_close()
                thread.join(timeout=2)


    def test_loopback_post_and_download(self):
        with tempfile.TemporaryDirectory() as root:
            Handler.runs = Path(root)
            server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            base = f"http://127.0.0.1:{server.server_port}"
            try:
                def fake_run(argv, run):
                    directory = Path(run) / "fink-most-points-output"
                    directory.mkdir()
                    (directory / "manifest.json").write_text('{"ok":true}')
                    from subprocess import CompletedProcess
                    return CompletedProcess(argv, 0, "generated\n", "")

                payload = json.dumps({"objectType": "ss", "results": 1}).encode()
                request = urllib.request.Request(base + "/run/most-points", data=payload,
                    headers={"Content-Type": "application/json", "Origin": base})
                with patch("serve.run_task", side_effect=fake_run):
                    with urllib.request.urlopen(request) as response:
                        data = json.load(response)
                self.assertTrue(data["ok"])
                self.assertEqual([f["name"] for f in data["artifacts"]], ["manifest.json"])
                with urllib.request.urlopen(base + data["artifacts"][0]["url"]) as response:
                    self.assertEqual(json.load(response), {"ok": True})
                request = urllib.request.Request(base + "/run/most-points", data=payload,
                    headers={"Content-Type": "application/json", "Origin": "https://foreign.example"})
                with self.assertRaises(urllib.error.HTTPError) as error:
                    urllib.request.urlopen(request)
                self.assertEqual(error.exception.code, 403)
            finally:
                server.shutdown()
                server.server_close()
                thread.join(timeout=2)


class ResourceTest(unittest.TestCase):
    def test_time_limit_terminates_child(self):
        with tempfile.TemporaryDirectory() as root, patch("serve.MAX_SECONDS", 0.01):
            with self.assertRaises(TimeoutError):
                run_task([sys.executable, "-c", "import time;time.sleep(3)"], Path(root))

    def test_posix_file_limit_on_real_child(self):
        if sys.platform == "win32":
            self.skipTest("POSIX resource limit")
        with tempfile.TemporaryDirectory() as root, tempfile.TemporaryDirectory() as scripts:
            script = Path(scripts) / "write.py"
            script.write_text("with open('big.bin','wb') as f: f.write(b'x' * (17 * 1024 * 1024))")
            result = run_task([sys.executable, str(script)], Path(root))
            self.assertNotEqual(result.returncode, 0)
            self.assertLessEqual((Path(root) / "big.bin").stat().st_size, MAX_FILE)

    def test_output_overflow_is_terminated_without_log_files(self):
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaisesRegex(LimitExceeded, "output"):
                run_task([sys.executable, "-c", f"print('x'*{MAX_OUTPUT + 100})"], Path(root))
            self.assertEqual(list(Path(root).iterdir()), [])
            with self.assertRaisesRegex(LimitExceeded, "output"):
                run_task([sys.executable, "-c",
                          f"import sys;sys.stderr.write('x'*{MAX_OUTPUT + 100})"], Path(root))

    def test_file_size_and_count_limits(self):
        with tempfile.TemporaryDirectory() as root:
            file = Path(root) / "huge.json"
            with file.open("wb") as stream:
                stream.truncate(MAX_FILE + 1)
            with self.assertRaisesRegex(LimitExceeded, "oversized"):
                run_size(Path(root))
            file.unlink()
            for number in range(257):
                (Path(root) / str(number)).touch()
            with self.assertRaisesRegex(LimitExceeded, "too many"):
                run_size(Path(root))

    def test_retention_evicts_old_run(self):
        with tempfile.TemporaryDirectory() as root:
            Handler.runs = Path(root)
            for number in range(MAX_RUNS):
                (Path(root) / f"old{number}").mkdir()
            server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            base = f"http://127.0.0.1:{server.server_port}"
            try:
                request = urllib.request.Request(base + "/run/most-points", data=b"{}",
                    headers={"Content-Type": "application/json", "Origin": base})
                with patch("serve.run_task", return_value=subprocess.CompletedProcess([], 0, "", "")):
                    with urllib.request.urlopen(request) as response:
                        self.assertTrue(json.load(response)["ok"])
                self.assertEqual(len(list(Path(root).iterdir())), MAX_RUNS)
            finally:
                server.shutdown()
                server.server_close()
                thread.join(timeout=2)


if __name__ == "__main__":
    unittest.main()

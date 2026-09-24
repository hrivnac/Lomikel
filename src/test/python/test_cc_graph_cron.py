"""Offline contracts for the LSST Januser cron entrypoints.

No HBase or graph connections are made: shell commands are stubbed and paths
are redirected into a temporary HOME. Run: python3 -m unittest discover
-s src/test/python -p 'test_cc_graph_cron.py' -v
"""
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time
import unittest

ROOT = Path(__file__).resolve().parents[3]
CRON = ROOT / "src/sh/cron/CC"
WORK = ROOT / "src/work/CC"


class CcGraphCronTest(unittest.TestCase):
    def run_stubbed(self, script, fail=True):
        with tempfile.TemporaryDirectory(prefix="cc-graph-cron-") as directory:
            root = Path(directory)
            home = root / "home"
            (home / "Lomikel/ant").mkdir(parents=True)
            (home / "Lomikel/ant/setup.sh").write_text("version=test\n")
            bindir = root / "bin"
            bindir.mkdir()
            calls = root / "calls"
            stub = bindir / "java"
            stub.write_text(
                '#!/bin/sh\nprintf "%s\\n" "$*" >> "$CRON_TEST_CALLS"\n'
                'printf "stub java stderr\\n" >&2\n'
                'exit "$CRON_TEST_STATUS"\n'
            )
            stub.chmod(0o755)
            # Baseline scripts hard-code /tmp; redirect a *copy* so the test
            # cannot touch a real cron lock. Fixed scripts may use TMPDIR.
            text = (CRON / script).read_text().replace("/tmp/", str(root) + "/")
            test_script = root / script
            test_script.write_text(text)
            env = os.environ.copy()
            env.update(HOME=str(home), TMPDIR=str(root),
                       PATH=str(bindir) + os.pathsep + env["PATH"],
                       CRON_TEST_CALLS=str(calls),
                       CRON_TEST_STATUS="17" if fail else "0")
            result = subprocess.run(["bash", str(test_script)], env=env,
                                    text=True, capture_output=True, timeout=10)
            logs = [p.read_text() for p in root.glob("*.log")]
            invocations = calls.read_text().splitlines() if calls.exists() else []
            return result, logs, invocations

    def test_import_failure_is_reported_and_stops_before_next_tag(self):
        result, logs, invocations = self.run_stubbed("importTags.sh")
        self.assertEqual(result.returncode, 17, result.stdout + result.stderr)
        self.assertEqual(len(invocations), 1)
        self.assertTrue(any("stub java stderr" in log for log in logs), logs)

    def test_process_failure_is_reported_and_logged(self):
        result, logs, invocations = self.run_stubbed("processTags.sh")
        self.assertEqual(result.returncode, 17, result.stdout + result.stderr)
        self.assertEqual(len(invocations), 1)
        self.assertTrue(any("stub java stderr" in log for log in logs), logs)

    def test_import_scan_keeps_row_keys_for_poll(self):
        script = (WORK / "importTags.groovy").read_text()
        match = re.search(r"client\.startScan\(null,\s*null,\s*null,\s*"
                          r"now - 90000000 \* delay,\s*now,\s*(true|false),",
                          script)
        if match is None:
            self.fail("cannot identify importer scan contract")
        self.assertEqual(match.group(1), "true", "poll() needs key:key")

    def test_scan_io_failure_reaches_async_client(self):
        base = (ROOT / "src/java/com/Lomikel/HBaser/HBaseClient.java").read_text()
        async_client = (ROOT / "src/java/com/Lomikel/HBaser/AsynchHBaseClient.java").read_text()
        # The synchronous API still returns partial rows, while the async
        # importer must turn scanner IO failures into a failed cron run.
        self.assertRegex(base, r"catch\s*\(IOException e\)\s*\{\s*"
                               r"log\.error\(\"Cannot search\", e\);\s*"
                               r"handleScanIOException\(e\)")
        self.assertRegex(async_client, r"handleScanIOException\(IOException failure\)\s*\{\s*"
                                      r"throw new UncheckedIOException\(")

    def test_concurrent_processor_is_rejected(self):
        with tempfile.TemporaryDirectory(prefix="cc-cron-concurrency-") as directory:
            root = Path(directory)
            home = root / "home"
            (home / "Lomikel/ant").mkdir(parents=True)
            (home / "Lomikel/ant/setup.sh").write_text("version=test\n")
            bindir = root / "bin"
            bindir.mkdir()
            java = bindir / "java"
            java.write_text(
                '#!/bin/sh\ntouch "$CRON_TEST_STARTED"\n'
                'while [ ! -e "$CRON_TEST_RELEASE" ]; do sleep 0.05; done\n'
            )
            java.chmod(0o755)
            script = root / "processTags.sh"
            script.write_text((CRON / script.name).read_text().replace(
                "/tmp/", str(root) + "/"))
            env = os.environ.copy()
            env.update(HOME=str(home), TMPDIR=str(root),
                       PATH=str(bindir) + os.pathsep + env["PATH"],
                       CRON_TEST_STARTED=str(root / "started"),
                       CRON_TEST_RELEASE=str(root / "release"))
            first = subprocess.Popen(["bash", str(script)], env=env,
                                     stdout=subprocess.PIPE,
                                     stderr=subprocess.PIPE, text=True)
            try:
                for _ in range(100):
                    if (root / "started").exists():
                        break
                    time.sleep(0.02)
                self.assertTrue((root / "started").exists(), "first run did not start")
                second = subprocess.run(["bash", str(script)], env=env,
                                        text=True, capture_output=True, timeout=3)
                self.assertEqual(second.returncode, 75, second.stdout + second.stderr)
            finally:
                (root / "release").touch()
                first.communicate(timeout=3)


if __name__ == "__main__":
    unittest.main()

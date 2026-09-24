"""Offline subprocess checks for IJCLab's cron entry point."""
import os
from pathlib import Path
import subprocess
import tempfile
import time
import unittest

SOURCE = Path(__file__).resolve().parents[2] / "sh/cron/IJCLab/fillOCol.sh"


class FillOColShellTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="fillocol-shell-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        ant = self.root / "Lomikel/ant"
        ant.mkdir(parents=True)
        (ant / "setup.sh").write_text('version=test\n')
        (self.root / "bin").mkdir()
        self.logdir = self.root / "logs"
        self.logdir.mkdir()
        self.lock = self.root / ".cache/Lomikel/cron/fillOCol.lock"
        self.script = SOURCE
        self.env = dict(os.environ, HOME=str(self.root),
                        TMPDIR=str(self.logdir),
                        PATH=f"{self.root}/bin:{os.environ['PATH']}")

    def fake_java(self, script):
        executable = self.root / "bin/java"
        executable.write_text('#!/usr/bin/bash\n' + script)
        executable.chmod(0o755)

    def invoke(self):
        return subprocess.run(['bash', str(self.script)], env=self.env,
                              capture_output=True, text=True, timeout=5)

    def logs(self):
        return '\n'.join(file.read_text() for file in self.logdir.glob('*.log'))

    def test_java_failure_exits_nonzero_and_logs_both_streams(self):
        self.fake_java('printf "stdout-marker\\n"\nprintf "stderr-marker\\n" >&2\nexit 23\n')
        result = self.invoke()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('stdout-marker', self.logs())
        self.assertIn('stderr-marker', self.logs())
        self.assertTrue(self.lock.exists(), 'flock file remains after failure')

    def test_concurrent_invocation_does_not_start_java_twice(self):
        marker = self.root / 'entered'
        release = self.root / 'release'
        self.fake_java(f'printf "started\\n" >> "{marker}"\n'
                       f'while [[ ! -e "{release}" ]]; do sleep 0.05; done\n')
        first = subprocess.Popen(['bash', str(self.script)], env=self.env,
                                 stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        try:
            for _ in range(100):
                if marker.exists():
                    break
                time.sleep(0.02)
            self.assertTrue(marker.exists(), 'first invocation never reached Java')
            second = self.invoke()
            self.assertNotEqual(second.returncode, 0)
            self.assertEqual(marker.read_text().splitlines(), ['started'])
        finally:
            release.touch()
            first.communicate(timeout=5)
        self.assertEqual(first.returncode, 0)
        self.assertEqual(self.invoke().returncode, 0, 'lock must be reusable after exit')
        self.assertEqual(marker.read_text().splitlines(), ['started', 'started'])


if __name__ == '__main__':
    unittest.main()

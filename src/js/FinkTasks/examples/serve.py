#!/usr/bin/env python3
"""Loopback-only example UI for the original standalone FinkTasks Python CLIs."""

from __future__ import annotations

import argparse
import json
import math
import os
import re
import shutil
import signal
import subprocess
import sys
import tempfile
import threading
import time
import uuid
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import cast
from urllib.parse import urlsplit

STATIC_ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = Path(__file__).resolve().parents[3] / "python/FinkTasks/src/fink_tasks"
TOKEN = re.compile(r"^[A-Za-z0-9_.:=-]+$")
FILE_TOKEN = re.compile(r"^[A-Za-z0-9_.-]+$")
MAX_BODY = 16_384
MAX_RESULTS = 100
MAX_OUTPUT = 256 * 1024  # per stream; no temporary log files
MAX_FILE = 16 * 1024 * 1024
MAX_RUN = 64 * 1024 * 1024
MAX_FILES = 256
MAX_RUNS = 8
MAX_SECONDS = 600
active = threading.Lock()


class LimitExceeded(Exception):
    pass


def run_size(run: Path) -> int:
    """Measure regular files without following links, and reject file floods."""
    size = count = 0
    for parent, dirs, files in os.walk(run, followlinks=False):
        count += len(dirs) + len(files)
        if count > MAX_FILES:
            raise LimitExceeded("task created too many files")
        for name in files:
            file = Path(parent) / name
            if file.is_symlink():
                continue
            length = file.stat().st_size
            if length > MAX_FILE:
                raise LimitExceeded("task created an oversized file")
            size += length
            if size > MAX_RUN:
                raise LimitExceeded("task exceeded storage limit")
    return size


def run_task(args: list[str], run: Path) -> subprocess.CompletedProcess:
    """Drain pipes incrementally; abort on excess output, storage, or wall time."""
    command = args
    if os.name == "posix" and args[1] != "-c":
        # Applied inside the fresh interpreter, not preexec_fn (unsafe in a threaded server).
        wrapper = ("import resource,runpy,sys; "
                   "resource.setrlimit(resource.RLIMIT_AS,(4294967296,4294967296)); "
                   f"resource.setrlimit(resource.RLIMIT_FSIZE,({MAX_FILE},{MAX_FILE})); "
                   "sys.argv=sys.argv[1:]; runpy.run_path(sys.argv[0],run_name='__main__')")
        command = [args[0], "-c", wrapper, *args[1:]]
    process = subprocess.Popen(command, cwd=run, stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE, start_new_session=os.name == "posix")
    buffers = [bytearray(), bytearray()]
    overflow = threading.Event()

    def drain(stream, buffer):
        try:
            while chunk := stream.read(8192):
                remaining = MAX_OUTPUT - len(buffer)
                buffer.extend(chunk[:remaining])
                if len(chunk) > remaining:
                    overflow.set()
        finally:
            stream.close()

    readers = [threading.Thread(target=drain, args=(stream, buffer), daemon=True)
               for stream, buffer in zip((process.stdout, process.stderr), buffers)]
    for reader in readers:
        reader.start()
    started = time.monotonic()
    try:
        while process.poll() is None:
            if overflow.is_set():
                raise LimitExceeded("task output exceeded limit")
            if time.monotonic() - started > MAX_SECONDS:
                raise TimeoutError("task exceeded the server's time limit")
            run_size(run)
            time.sleep(0.1)
        for reader in readers:
            reader.join(timeout=max(0, MAX_SECONDS - (time.monotonic() - started)))
        if any(reader.is_alive() for reader in readers):
            raise TimeoutError("task pipes remained open past the time limit")
        if overflow.is_set():
            raise LimitExceeded("task output exceeded limit")
        run_size(run)
        return subprocess.CompletedProcess(args, process.returncode,
            buffers[0].decode("utf-8", errors="replace"),
            buffers[1].decode("utf-8", errors="replace"))
    except BaseException:
        if os.name == "posix":
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
        elif process.poll() is None:
            process.kill()
        process.wait()
        for reader in readers:
            reader.join(timeout=1)
        raise


def script_arguments(task: str, values: dict) -> tuple[list[str], str | None]:
    """Map the browser's named fields to an allowlisted argv, never a shell string."""
    if not isinstance(values, dict):
        raise ValueError("request must be a JSON object")
    script = {"object-neighbors": "object_neighbors.py", "most-points": "most_points.py"}.get(task)
    if script is None:
        raise ValueError("unknown task")
    allowed = ({"objectId", "classifier", "distance", "results", "restColumns", "json",
                "allowInsecureGraph", "graphUrl", "apiUrl", "timeout"} if task == "object-neighbors"
               else {"objectType", "results", "lightcurves", "outputDir", "allowInsecureEs",
                     "esUrl", "apiUrl", "timeout"})
    if set(values) - allowed:
        raise ValueError("unknown input fields")
    args = [sys.executable, str(SCRIPTS / script)]
    try:
        timeout = float(values.get("timeout", 180))
        result = float(values.get("results", 10))
    except (TypeError, ValueError, OverflowError) as exc:
        raise ValueError("results and timeout must be finite numbers") from exc
    if not math.isfinite(timeout) or timeout <= 0 or timeout > 300:
        raise ValueError("timeout must be between 0 and 300 seconds")
    if not math.isfinite(result) or result < (0 if task == "object-neighbors" else 1) or result > MAX_RESULTS:
        raise ValueError("invalid results")
    if result >= 1 and not result.is_integer():
        raise ValueError("result count must be an integer")
    args += ["--results", str(int(result) if result.is_integer() else result), "--timeout", str(timeout)]
    api_url = values.get("apiUrl", "https://api.lsst.fink-portal.org")
    if not isinstance(api_url, str):
        raise ValueError("apiUrl must be a string")
    args += ["--api-url", api_url]
    if task == "object-neighbors":
        object_id = values.get("objectId", "")
        classifier = values.get("classifier", "FINK")
        if not all(isinstance(s, str) and TOKEN.fullmatch(s) for s in (object_id, classifier)):
            raise ValueError("object ID and classifier must be safe nonempty tokens")
        distance = values.get("distance", "JensenShannon")
        if distance not in ("JensenShannon", "Euclidean", "Cosine"):
            raise ValueError("invalid distance metric")
        graph_url = values.get("graphUrl", "http://134.158.243.144:24444")
        if not isinstance(graph_url, str):
            raise ValueError("graphUrl must be a string")
        args += ["--classifier", classifier, "--distance", distance, "--graph-url", graph_url]
        columns = values.get("restColumns", [])
        if not isinstance(columns, list) or any(not isinstance(s, str) for s in columns):
            raise ValueError("restColumns must be a list of strings")
        for column in columns:
            args += ["--rest-columns", column]
        for key, flag in (("json", "--json"), ("allowInsecureGraph", "--allow-insecure-graph")):
            if not isinstance(values.get(key, False), bool):
                raise ValueError(f"{key} must be a boolean")
            if values.get(key):
                args.append(flag)
        args.append(object_id)
        return args, None
    object_type = values.get("objectType", "both")
    if object_type not in ("both", "ss", "dia"):
        raise ValueError("invalid object type")
    es_url = values.get("esUrl", "http://134.158.243.139:24499")
    if not isinstance(es_url, str):
        raise ValueError("esUrl must be a string")
    directory = values.get("outputDir", "fink-most-points-output")
    if not isinstance(directory, str) or not FILE_TOKEN.fullmatch(directory) or directory in (".", ".."):
        raise ValueError("outputDir must be one safe directory name")
    args += ["--object-type", object_type, "--es-url", es_url]
    for key, flag in (("lightcurves", "--lightcurves"), ("allowInsecureEs", "--allow-insecure-es")):
        if not isinstance(values.get(key, False), bool):
            raise ValueError(f"{key} must be a boolean")
        if values.get(key):
            args.append(flag)
    if values.get("lightcurves", False) and object_type == "dia":
        raise ValueError("lightcurves require SS objects")
    return args, directory


class Handler(SimpleHTTPRequestHandler):
    runs: Path

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(STATIC_ROOT), **kwargs)

    def _local_host(self) -> bool:
        port = cast(ThreadingHTTPServer, self.server).server_port
        return self.headers.get("Host") in (f"127.0.0.1:{port}", f"localhost:{port}")

    def _json(self, status: int, payload: dict) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:
        if not self._local_host():
            self.send_error(403)
            return
        path = urlsplit(self.path).path
        if path in ("/examples/object_neighbors.html", "/examples/most_points.html"):
            super().do_GET()
            return
        parts = path.strip("/").split("/")
        if (len(parts) == 4 and parts[0] == "artifacts" and
                all(FILE_TOKEN.fullmatch(part) and part not in (".", "..") for part in parts[1:])):
            try:
                file = self.runs / parts[1] / parts[2] / parts[3]
                if not file.is_symlink() and not file.parent.is_symlink() and file.is_file():
                    if file.stat().st_size > MAX_FILE:
                        self._json(413, {"error": "artifact exceeds download limit"})
                        return
                    with file.open("rb") as source:
                        data = source.read(MAX_FILE + 1)
                    if len(data) > MAX_FILE:
                        self._json(413, {"error": "artifact exceeds download limit"})
                        return
                    self.send_response(200)
                    self.send_header("Content-Type", "image/png" if file.suffix == ".png" else "application/json")
                    self.send_header("Content-Disposition", f'attachment; filename="{file.name}"')
                    self.send_header("Content-Length", str(len(data)))
                    self.end_headers()
                    self.wfile.write(data)
                    return
            except OSError:
                self._json(500, {"error": "artifact operation failed"})
                return
        self.send_error(404)

    def do_POST(self) -> None:
        port = cast(ThreadingHTTPServer, self.server).server_port
        if not self._local_host() or self.headers.get("Origin") not in (
            None, f"http://127.0.0.1:{port}", f"http://localhost:{port}"
        ):
            self._json(403, {"error": "only same-origin loopback requests are accepted"})
            return
        task = self.path.removeprefix("/run/") if self.path.startswith("/run/") else ""
        if task not in ("object-neighbors", "most-points"):
            self._json(404, {"error": "unknown task"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if self.headers.get("Content-Type", "").split(";")[0] != "application/json" or not 0 < length <= MAX_BODY:
                raise ValueError("expected a bounded JSON request")
            values = json.loads(self.rfile.read(length))
            args, output_dir = script_arguments(task, values)
            if not active.acquire(blocking=False):
                self._json(429, {"error": "a task is already running"})
                return
            try:
                # The single active worker owns retention/eviction too.
                for old in sorted(self.runs.iterdir(), key=lambda p: p.stat().st_mtime)[:-(MAX_RUNS - 1)]:
                    if old.is_dir():
                        shutil.rmtree(old)
                run_id = uuid.uuid4().hex
                run = self.runs / run_id
                run.mkdir()
                try:
                    if output_dir:
                        args += ["--output-dir", output_dir]
                    completed = run_task(args, run)
                    run_size(run)
                    folder = run / output_dir if output_dir else None
                    files = sorted(folder.iterdir()) if folder and folder.is_dir() and not folder.is_symlink() else []
                    artifacts = [{"name": file.name, "url": f"/artifacts/{run_id}/{output_dir}/{file.name}"}
                                 for file in files if file.is_file() and not file.is_symlink()
                                 and file.stat().st_size <= MAX_FILE and FILE_TOKEN.fullmatch(file.name)]
                    self._json(200, {"ok": completed.returncode == 0, "stdout": completed.stdout,
                                     "stderr": completed.stderr, "artifacts": artifacts})
                except BaseException:
                    shutil.rmtree(run, ignore_errors=True)
                    raise
            finally:
                active.release()
        except (ValueError, TypeError, json.JSONDecodeError) as exc:
            self._json(400, {"error": str(exc)})
        except subprocess.TimeoutExpired:
            self._json(504, {"error": "task exceeded the server's time limit"})
        except TimeoutError:
            self._json(504, {"error": "task exceeded the server's time limit"})
        except LimitExceeded as exc:
            self._json(413, {"error": str(exc)})
        except OSError:
            self._json(500, {"error": "task or filesystem operation failed"})


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8766)
    options = parser.parse_args()
    if not 1024 <= options.port <= 65535:
        parser.error("port must be between 1024 and 65535")
    with tempfile.TemporaryDirectory(prefix="finktasks-web-") as directory:
        Handler.runs = Path(directory)
        with ThreadingHTTPServer(("127.0.0.1", options.port), Handler) as server:
            print(f"Open http://127.0.0.1:{options.port}/examples/object_neighbors.html", flush=True)
            print(f"Or   http://127.0.0.1:{options.port}/examples/most_points.html", flush=True)
            server.serve_forever()


if __name__ == "__main__":
    main()

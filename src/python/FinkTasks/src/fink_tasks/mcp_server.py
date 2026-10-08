"""MCP adapter for FinkTasks. Backends are configured by the server, not callers."""
from __future__ import annotations

import argparse
import asyncio
import json
import math
import os
import selectors
import signal
import subprocess
import sys
import tempfile
import threading
from pathlib import Path
from dataclasses import dataclass
from typing import Any

from mcp.server.fastmcp import Context, FastMCP
from .most_points import DEFAULT_API_URL, DEFAULT_ES_URL, selected_object_types
from .object_neighbors import DEFAULT_GRAPH_URL


@dataclass(frozen=True)
class BackendConfig:
    graph_url: str = DEFAULT_GRAPH_URL
    es_url: str = DEFAULT_ES_URL
    api_url: str = DEFAULT_API_URL
    allow_insecure_graph: bool = False
    allow_insecure_es: bool = False
    timeout: float = 180.0
    worker_timeout: float = 600.0
    queue_timeout: float = 30.0
    max_workers: int = 4
    max_queue: int = 16
    max_output_bytes: int = 1048576


class Hooks:
    """Extension points; intentionally permissive until a deployment policy exists."""

    def authorize(self, tool: str, arguments: dict[str, Any], context: Any = None) -> None:
        """Later: inspect caller identity before work starts."""

    def check_query(self, tool: str, arguments: dict[str, Any]) -> None:
        """Later: enforce a deployment-specific query policy."""


class TaskRunner:
    def __init__(self, config: BackendConfig, hooks: Hooks | None = None):
        self.config = config
        self.hooks = hooks or Hooks()
        self.python = sys.executable
        self._slots = asyncio.Semaphore(config.max_workers)
        self._admission = asyncio.BoundedSemaphore(config.max_workers + config.max_queue)
        # The loop's default executor is joined by asyncio.run at shutdown;
        # slots bound submitted jobs to max_workers independently of pool size.

    async def _run(self, argv: list[str], *, ranking_types: list[str] | None = None) -> Any:
        """Bound admission and signal a self-cleaning worker on abort."""
        if self._admission.locked():
            raise RuntimeError("worker queue full")
        await self._admission.acquire()
        acquired = False
        handed_off = False
        try:
            try:
                await asyncio.wait_for(self._slots.acquire(), self.config.queue_timeout)
                acquired = True
            except asyncio.TimeoutError as exc:
                raise TimeoutError("worker queue timed out") from exc
            stop = threading.Event()
            loop = asyncio.get_running_loop()
            def owned_worker():
                if ranking_types is not None:
                    # The thread outlives a timed-out caller. Keep its
                    # directory until spawn, child cleanup, and reads end.
                    with tempfile.TemporaryDirectory(prefix="fink-mcp-") as directory:
                        self._execute(argv + ["--output-dir", directory], stop)
                        rankings = []
                        for kind in ranking_types:
                            path = Path(directory) / f"{kind}_most_points.json"
                            if path.stat().st_size > self.config.max_output_bytes:
                                raise RuntimeError("ranking output exceeds configured byte limit")
                            rankings.append(json.loads(path.read_bytes()))
                        return {"survey": "lsst", "rankings": rankings}
                return self._execute(argv, stop)

            result = loop.run_in_executor(None, owned_worker)
            # The executor Future completes only after the thread's function
            # returns. Do not release a slot from inside its finally block:
            # another process could start while the old thread still runs.
            def worker_finished(future):
                if future.cancelled():
                    return  # Loop shutdown cannot admit further work.
                self._release_slots()
                future.exception()  # Retrieve detached timeout/cancel errors.
            result.add_done_callback(worker_finished)
            handed_off = True
            acquired = False
            try:
                return await asyncio.wait_for(asyncio.shield(result), self.config.worker_timeout)
            except asyncio.TimeoutError as exc:
                stop.set()
                raise TimeoutError("worker timed out") from exc
            except asyncio.CancelledError:
                stop.set()
                # Normal cancellation waits for reaping. Further cancellation
                # can interrupt this await, but cannot interrupt the thread.
                try:
                    await asyncio.shield(result)
                except (asyncio.CancelledError, Exception):
                    pass
                raise
        finally:
            if acquired:
                self._slots.release()
            if not handed_off:
                self._admission.release()

    def _release_slots(self) -> None:
        self._slots.release()
        self._admission.release()

    def _execute(self, argv: list[str], stop: threading.Event) -> tuple[bytes, bytes]:
        """Own spawn, both nonblocking pipes, and group cleanup in one thread."""
        process = None
        completed = False
        try:
            # An already-aborted queued job need not spawn; if abort happens
            # during Popen, the returned process is still owned by this frame.
            if stop.is_set():
                raise TimeoutError("worker aborted")
            process = subprocess.Popen(argv, stdout=subprocess.PIPE,
                                       stderr=subprocess.PIPE, start_new_session=True)
            if stop.is_set():
                raise TimeoutError("worker aborted")
            output = [bytearray(), bytearray()]
            with selectors.DefaultSelector() as selector:
                for index, pipe in enumerate((process.stdout, process.stderr)):
                    assert pipe is not None
                    os.set_blocking(pipe.fileno(), False)
                    selector.register(pipe, selectors.EVENT_READ, index)
                while True:
                    if stop.is_set():
                        raise TimeoutError("worker aborted")
                    for key, _ in selector.select(timeout=.05):
                        chunk = os.read(key.fileobj.fileno(), 65536)
                        if not chunk:
                            selector.unregister(key.fileobj)
                            key.fileobj.close()
                            continue
                        data = output[key.data]
                        if len(data) + len(chunk) > self.config.max_output_bytes:
                            raise RuntimeError("worker output exceeds configured byte limit")
                        data.extend(chunk)
                    # Do not reap an exited leader while a descendant might
                    # still hold the pipes: its zombie reserves the PGID.
                    if not selector.get_map() and os.waitid(
                            os.P_PID, process.pid,
                            os.WEXITED | os.WNOHANG | os.WNOWAIT) is not None:
                        if stop.is_set():
                            raise TimeoutError("worker aborted")
                        process.wait()
                        completed = True
                        if process.returncode:
                            message = output[1].decode("utf-8", errors="replace").strip() or "no stderr"
                            raise RuntimeError(f"worker failed (exit {process.returncode}): {message}")
                        return bytes(output[0]), bytes(output[1])
        finally:
            if process is not None:
                if not completed:
                    # Leader is unreaped, so its numeric PGID cannot be reused.
                    try:
                        os.killpg(process.pid, signal.SIGKILL)
                    except ProcessLookupError:
                        pass
                for pipe in (process.stdout, process.stderr):
                    if pipe is not None:
                        pipe.close()
                if not completed:
                    process.wait()

    def _preflight(self, tool: str, arguments: dict[str, Any], context: Any = None) -> None:
        self.hooks.authorize(tool, arguments, context)
        self.hooks.check_query(tool, arguments)

    async def object_neighbors(
        self,
        object_id: str,
        classifier: str = "FINK",
        distance: str = "JensenShannon",
        results: float = 10.0,
        rest_columns: list[str] | None = None,
        context: Any = None,
    ) -> dict[str, Any]:
        """Rank LSST graph classifier neighbours with REST sky-coordinate ties."""
        arguments = dict(object_id=object_id, classifier=classifier, distance=distance,
                         results=results, rest_columns=rest_columns or [])
        self._preflight("object_neighbors", arguments, context)
        argv = [self.python, "-m", "fink_tasks.object_neighbors", object_id,
                "--classifier", classifier, "--distance", distance,
                "--results", str(results), "--json", "--graph-url", self.config.graph_url,
                "--api-url", self.config.api_url, "--timeout", str(self.config.timeout)]
        for column in rest_columns or []:
            argv.extend(["--rest-columns", column])
        if self.config.allow_insecure_graph:
            argv.append("--allow-insecure-graph")
        stdout, _ = await self._run(argv)
        return json.loads(stdout)

    async def most_points(self, object_type: str = "both", results: int = 10,
                    context: Any = None) -> dict[str, Any]:
        """Rank LSST SS/DIA objects by verified ES MJD cardinality."""
        arguments = dict(object_type=object_type, results=results)
        self._preflight("most_points", arguments, context)
        argv = [self.python, "-m", "fink_tasks.most_points", "--object-type", object_type,
                "--results", str(results), "--es-url", self.config.es_url,
                "--api-url", self.config.api_url, "--timeout", str(self.config.timeout)]
        if self.config.allow_insecure_es:
            argv.append("--allow-insecure-es")
        return await self._run(argv, ranking_types=selected_object_types(object_type))


def create_server(config: BackendConfig, hooks: Hooks | None = None,
                  host: str = "127.0.0.1", port: int = 8768):
    """Register FinkTasks operations; injectable hooks precede every task."""
    runner = TaskRunner(config, hooks)
    server = FastMCP("FinkTasks", host=host, port=port,
                     instructions="Read-only LSST FinkTasks combining Fink REST, graph and ES.")

    @server.tool()
    async def object_neighbors(object_id: str, ctx: Context, classifier: str = "FINK",
                               distance: str = "JensenShannon", results: float = 10.0,
                               rest_columns: list[str] | None = None) -> dict[str, Any]:
        """Rank LSST graph neighbors by classifier distance and sky separation."""
        return await runner.object_neighbors(object_id, classifier,
                                             distance, results, rest_columns, ctx)

    @server.tool()
    async def most_points(ctx: Context, object_type: str = "both",
                          results: int = 10) -> dict[str, Any]:
        """Rank LSST SS and/or DIA objects by Elasticsearch MJD point count."""
        return await runner.most_points(object_type, results, ctx)

    return server


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description="FinkTasks MCP adapter (local prototype)")
    parser.add_argument("--transport", choices=("stdio", "streamable-http"), default="stdio")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8768)
    parser.add_argument("--graph-url", default=DEFAULT_GRAPH_URL)
    parser.add_argument("--es-url", default=DEFAULT_ES_URL)
    parser.add_argument("--api-url", default=DEFAULT_API_URL)
    parser.add_argument("--allow-insecure-graph", action="store_true")
    parser.add_argument("--allow-insecure-es", action="store_true")
    parser.add_argument("--timeout", type=float, default=180.0)
    parser.add_argument("--worker-timeout", type=float, default=600.0)
    parser.add_argument("--queue-timeout", type=float, default=30.0)
    parser.add_argument("--max-workers", type=int, default=4)
    parser.add_argument("--max-queue", type=int, default=16)
    parser.add_argument("--max-output-bytes", type=int, default=1048576)
    options = parser.parse_args(argv)
    if options.transport == "streamable-http" and options.host not in ("127.0.0.1", "::1"):
        parser.error("unauthenticated HTTP prototype must bind to loopback only")
    if not 1 <= options.port <= 65535:
        parser.error("port must be between 1 and 65535")
    if not math.isfinite(options.timeout) or options.timeout <= 0:
        parser.error("timeout must be positive and finite")
    if not math.isfinite(options.worker_timeout) or options.worker_timeout <= 0:
        parser.error("worker-timeout must be positive and finite")
    if not math.isfinite(options.queue_timeout) or options.queue_timeout <= 0:
        parser.error("queue-timeout must be positive and finite")
    if not 1 <= options.max_workers <= 32:
        parser.error("max-workers must be between 1 and 32")
    if not 0 <= options.max_queue <= 1024:
        parser.error("max-queue must be between 0 and 1024")
    if not 1 <= options.max_output_bytes <= 16777216:
        parser.error("max-output-bytes must be between 1 and 16777216")
    config = BackendConfig(graph_url=options.graph_url, es_url=options.es_url,
                           api_url=options.api_url, allow_insecure_graph=options.allow_insecure_graph,
                           allow_insecure_es=options.allow_insecure_es, timeout=options.timeout,
                           worker_timeout=options.worker_timeout, queue_timeout=options.queue_timeout,
                           max_workers=options.max_workers, max_queue=options.max_queue,
                           max_output_bytes=options.max_output_bytes)
    create_server(config, host=options.host, port=options.port).run(transport=options.transport)


if __name__ == "__main__":
    main()

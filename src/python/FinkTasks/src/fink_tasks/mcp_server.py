"""MCP adapter for FinkTasks. Backends are configured by the server, not callers."""
from __future__ import annotations

import argparse
import asyncio
import json
import subprocess
import sys
import tempfile
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

    def _preflight(self, tool: str, arguments: dict[str, Any], context: Any = None) -> None:
        self.hooks.authorize(tool, arguments, context)
        self.hooks.check_query(tool, arguments)

    def object_neighbors(
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
        completed = subprocess.run(argv, shell=False, capture_output=True, text=True,
                                   timeout=self.config.worker_timeout, check=False)
        if completed.returncode:
            raise RuntimeError(completed.stderr.strip() or "object_neighbors failed")
        return json.loads(completed.stdout)

    def most_points(self, object_type: str = "both", results: int = 10,
                    context: Any = None) -> dict[str, Any]:
        """Rank LSST SS/DIA objects by verified ES MJD cardinality."""
        arguments = dict(object_type=object_type, results=results)
        self._preflight("most_points", arguments, context)
        with tempfile.TemporaryDirectory(prefix="fink-mcp-") as directory:
            output = Path(directory)
            argv = [self.python, "-m", "fink_tasks.most_points", "--object-type", object_type,
                    "--results", str(results), "--output-dir", str(output),
                    "--es-url", self.config.es_url, "--api-url", self.config.api_url,
                    "--timeout", str(self.config.timeout)]
            if self.config.allow_insecure_es:
                argv.append("--allow-insecure-es")
            completed = subprocess.run(argv, shell=False, capture_output=True, text=True,
                                       timeout=self.config.worker_timeout, check=False)
            if completed.returncode:
                raise RuntimeError(completed.stderr.strip() or "most_points failed")
            rankings = [json.loads((output / f"{kind}_most_points.json").read_text())
                        for kind in selected_object_types(object_type)]
            return {"survey": "lsst", "rankings": rankings}


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
        return await asyncio.to_thread(runner.object_neighbors, object_id, classifier,
                                       distance, results, rest_columns, ctx)

    @server.tool()
    async def most_points(ctx: Context, object_type: str = "both",
                          results: int = 10) -> dict[str, Any]:
        """Rank LSST SS and/or DIA objects by Elasticsearch MJD point count."""
        return await asyncio.to_thread(runner.most_points, object_type, results, ctx)

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
    options = parser.parse_args(argv)
    if options.transport == "streamable-http" and options.host not in ("127.0.0.1", "::1"):
        parser.error("unauthenticated HTTP prototype must bind to loopback only")
    if not 1 <= options.port <= 65535:
        parser.error("port must be between 1 and 65535")
    config = BackendConfig(graph_url=options.graph_url, es_url=options.es_url,
                           api_url=options.api_url, allow_insecure_graph=options.allow_insecure_graph,
                           allow_insecure_es=options.allow_insecure_es, timeout=options.timeout,
                           worker_timeout=options.worker_timeout)
    create_server(config, host=options.host, port=options.port).run(transport=options.transport)


if __name__ == "__main__":
    main()

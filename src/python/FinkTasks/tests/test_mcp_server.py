"""Focused tests for the FinkTasks MCP adapter."""
import json
import asyncio
import time
import subprocess
from pathlib import Path
from unittest.mock import patch

from fink_tasks.mcp_server import BackendConfig, Hooks, TaskRunner, create_server, main


def test_object_neighbors_reuses_existing_json_cli_without_shell():
    config = BackendConfig(graph_url="http://127.0.0.1:24444", api_url="https://api.example.test")
    runner = TaskRunner(config)
    response = {"object_id": "170028486134595648", "returned": 1, "results": [{"object_id": "2"}]}
    with patch("fink_tasks.mcp_server.subprocess.run", return_value=subprocess.CompletedProcess([], 0, json.dumps(response), "")) as run:
        result = runner.object_neighbors("170028486134595648", results=1, rest_columns=["r:g_psfFluxMax"])
    assert result == response
    argv = run.call_args.args[0]
    assert argv[:3] == [runner.python, "-m", "fink_tasks.object_neighbors"]
    assert argv[3] == "170028486134595648"
    assert "--json" in argv and "--graph-url" in argv and "--api-url" in argv
    assert "r:g_psfFluxMax" in argv
    assert run.call_args.kwargs["shell"] is False


def test_most_points_reads_existing_cli_ranking_documents():
    runner = TaskRunner(BackendConfig(es_url="http://127.0.0.1:24499"))
    def fake_run(argv, **kwargs):
        output = Path(argv[argv.index("--output-dir") + 1])
        for kind in ("ss", "dia"):
            (output / f"{kind}_most_points.json").write_text(json.dumps({
                "object_type": kind, "returned": 1, "objects": [{"object_id": f"{kind}-1"}],
            }))
        return subprocess.CompletedProcess(argv, 0, "", "")
    with patch("fink_tasks.mcp_server.subprocess.run", side_effect=fake_run) as run:
        data = runner.most_points("both", results=123)
    assert data["survey"] == "lsst"
    assert [x["object_type"] for x in data["rankings"]] == ["ss", "dia"]
    argv = run.call_args.args[0]
    assert "123" == argv[argv.index("--results") + 1]
    assert "--es-url" in argv and "--lightcurves" not in argv
    assert run.call_args.kwargs["shell"] is False


def test_mcp_discovery_exposes_two_named_tools():
    server = create_server(BackendConfig())
    tools = asyncio.run(server.list_tools())
    assert {tool.name for tool in tools} == {"object_neighbors", "most_points"}
    for tool in tools:
        assert tool.inputSchema["type"] == "object"


def test_mcp_tools_offload_slow_worker_from_event_loop():
    def slow(self, *args, **kwargs):
        time.sleep(0.25)
        return {"returned": 0, "results": []}
    server = create_server(BackendConfig())

    async def check():
        start = time.monotonic()
        task = asyncio.create_task(server.call_tool("object_neighbors", {"object_id": "123"}))
        await asyncio.sleep(0.04)
        elapsed = time.monotonic() - start
        await task
        return elapsed

    with patch.object(TaskRunner, "object_neighbors", slow):
        assert asyncio.run(check()) < 0.18, "slow tool blocked the MCP event loop"


def test_mcp_tools_inject_context_and_forward_it_to_auth_hook():
    server = create_server(BackendConfig())
    for name in ("object_neighbors", "most_points"):
        registered = server._tool_manager.get_tool(name)
        assert registered.context_kwarg == "ctx"
        assert registered.is_async

    marker = object()
    seen = []
    class RecordingHooks(Hooks):
        def authorize(self, tool, arguments, context=None):
            seen.append((tool, context))
    with patch("fink_tasks.mcp_server.subprocess.run", return_value=subprocess.CompletedProcess([], 0, json.dumps({"returned": 0}), "")):
        TaskRunner(BackendConfig(), RecordingHooks()).object_neighbors("123", context=marker)
    assert seen == [("object_neighbors", marker)]


def test_unauthenticated_http_refuses_non_loopback_host():
    import pytest
    with patch("fink_tasks.mcp_server.create_server") as factory:
        with pytest.raises(SystemExit) as exc:
            main(["--transport", "streamable-http", "--host", "0.0.0.0"])
    assert exc.value.code == 2
    factory.assert_not_called()


def test_package_advertises_optional_mcp_entry_point():
    import tomllib
    project = tomllib.loads((Path(__file__).parents[1] / "pyproject.toml").read_text())["project"]
    assert project["scripts"]["fink-mcp"] == "fink_tasks.mcp_server:main"
    assert any(dep.startswith("mcp") for dep in project["optional-dependencies"]["mcp"])


def test_hook_can_stop_call_before_backend_invocation():
    class Deny(Hooks):
        def authorize(self, tool, arguments, context=None):
            raise PermissionError("blocked by future auth hook")
    with patch("fink_tasks.mcp_server.subprocess.run") as run:
        try:
            TaskRunner(BackendConfig(), hooks=Deny()).object_neighbors("123")
        except PermissionError as exc:
            assert "blocked" in str(exc)
        else:
            raise AssertionError("the future auth hook was bypassed")
    run.assert_not_called()

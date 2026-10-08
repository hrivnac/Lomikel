"""Offline process and MCP adapter regression tests."""
import asyncio
import gc
import json
import os
import subprocess
import sys
import threading
import time
from pathlib import Path
from unittest.mock import patch

import pytest

from fink_tasks.mcp_server import BackendConfig, Hooks, TaskRunner, create_server, main


def run(coro):
    return asyncio.run(coro)


def test_object_neighbors_preserves_cli_argv_json_and_hook_context():
    seen = []
    class Recording(Hooks):
        def authorize(self, tool, arguments, context=None):
            seen.append((tool, context))
    runner = TaskRunner(BackendConfig(graph_url="http://127.0.0.1:24444", api_url="https://api.example.test"), Recording())
    marker = object()
    response = {"object_id": "170028486134595648", "returned": 1}
    async def fake(argv):
        assert argv[:4] == [runner.python, "-m", "fink_tasks.object_neighbors", "170028486134595648"]
        assert argv[argv.index("--results") + 1] == "0.2"
        assert "--json" in argv and "--graph-url" in argv and "--api-url" in argv
        assert argv[argv.index("--rest-columns") + 1] == "r:g_psfFluxMax"
        return json.dumps(response).encode(), b""
    with patch.object(runner, "_run", side_effect=fake):
        assert run(runner.object_neighbors("170028486134595648", results=0.2, rest_columns=["r:g_psfFluxMax"], context=marker)) == response
    assert seen == [("object_neighbors", marker)]


def test_most_points_preserves_cli_argv_and_reads_rankings():
    runner = TaskRunner(BackendConfig(es_url="http://127.0.0.1:24499"))
    def fake(argv, stop):
        assert argv[:3] == [runner.python, "-m", "fink_tasks.most_points"]
        assert argv[argv.index("--results") + 1] == "123"
        assert "--es-url" in argv and "--lightcurves" not in argv
        output = Path(argv[argv.index("--output-dir") + 1])
        for kind in ("ss", "dia"):
            (output / f"{kind}_most_points.json").write_text(json.dumps({"object_type": kind, "returned": 1}))
        return b"", b""
    with patch.object(runner, "_execute", side_effect=fake):
        data = run(runner.most_points("both", results=123))
    assert data == {"survey": "lsst", "rankings": [{"object_type": "ss", "returned": 1}, {"object_type": "dia", "returned": 1}]}


def test_worker_timeout_kills_child(tmp_path):
    runner = TaskRunner(BackendConfig(worker_timeout=0.15))
    pidfile = tmp_path / "pid"
    code = "import os,sys,time; open(sys.argv[1],'w').write(str(os.getpid())); time.sleep(60)"
    with pytest.raises(TimeoutError, match="worker timed out"):
        run(runner._run([sys.executable, "-c", code, str(pidfile)]))
    assert_process_gone(int(pidfile.read_text()))


def assert_process_gone(pid):
    with pytest.raises(ProcessLookupError):
        os.kill(pid, 0)


def test_cancel_kills_child_and_releases_slot(tmp_path):
    async def check():
        runner = TaskRunner(BackendConfig(max_workers=1, worker_timeout=5))
        pidfile = tmp_path / "pid"
        code = "import os,sys,time; open(sys.argv[1],'w').write(str(os.getpid())); time.sleep(60)"
        task = asyncio.create_task(runner._run([sys.executable, "-c", code, str(pidfile)]))
        for _ in range(200):
            if pidfile.exists():
                break
            await asyncio.sleep(0.01)
        assert pidfile.exists()
        task.cancel()
        with pytest.raises(asyncio.CancelledError):
            await task
        assert_process_gone(int(pidfile.read_text()))
        stdout, _ = await runner._run([sys.executable, "-c", "print('reused')"])
        assert stdout == b"reused\n"
    run(check())


@pytest.mark.parametrize("cancel", [False, True])
def test_exited_leader_descendant_holding_pipe_is_killed(tmp_path, cancel):
    async def check():
        runner = TaskRunner(BackendConfig(worker_timeout=.25 if not cancel else 5))
        pidfile = tmp_path / "descendant"
        # The leader exits without waiting; the child inherits stdout/stderr.
        code = ("import os,sys,time; pid=os.fork(); "
                "\nif pid: os._exit(0)"
                "\nopen(sys.argv[1], 'w').write(str(os.getpid()))"
                "\ntime.sleep(60)")
        task = asyncio.create_task(runner._run([sys.executable, "-c", code, str(pidfile)]))
        for _ in range(200):
            if pidfile.exists():
                break
            await asyncio.sleep(.01)
        assert pidfile.exists()
        await asyncio.sleep(.05)  # Direct child has exited before cleanup.
        if cancel:
            task.cancel()
            with pytest.raises(asyncio.CancelledError):
                await task
        else:
            with pytest.raises(TimeoutError, match="worker timed out"):
                await task
        pid = int(pidfile.read_text())
        # Orphans can remain as zombies if this container's init is slow to reap.
        for _ in range(200):
            status = Path(f"/proc/{pid}/stat")
            if not status.exists() or status.read_text().split()[2] == "Z":
                break
            await asyncio.sleep(.01)
        else:
            pytest.fail(f"descendant {pid} still running")
    run(check())


@pytest.mark.skipif(sys.platform != "linux", reason="waitid WNOWAIT requires Linux")
def test_leader_exits_before_spawn_returns_with_pipe_holding_descendant(tmp_path):
    from fink_tasks import mcp_server
    real_popen = subprocess.Popen
    child_pid = tmp_path / "descendant"
    code = ("import os,sys,time; pid=os.fork(); "
            "\nif pid: os._exit(0)"
            "\nopen(sys.argv[1], 'w').write(str(os.getpid()))"
            "\ntime.sleep(60)")
    def exited_before_return(*args, **kwargs):
        proc = real_popen(*args, **kwargs)
        # WNOWAIT observes exit without reaping: the leader remains a zombie.
        import time
        deadline = time.monotonic() + 5
        while os.waitid(os.P_PID, proc.pid, os.WEXITED | os.WNOHANG | os.WNOWAIT) is None:
            assert time.monotonic() < deadline
            time.sleep(.005)
        return proc
    with patch.object(mcp_server.subprocess, "Popen", side_effect=exited_before_return):
        with pytest.raises(TimeoutError, match="worker timed out"):
            run(TaskRunner(BackendConfig(worker_timeout=.2))._run(
                [sys.executable, "-c", code, str(child_pid)]))
    assert child_pid.exists()
    assert_descendant_stopped(int(child_pid.read_text()))


def assert_descendant_stopped(pid):
    import time
    status = Path(f"/proc/{pid}/stat")
    deadline = time.monotonic() + 2
    while True:
        try:
            state = status.read_text().split()[2]
        except FileNotFoundError:
            break  # The orphan was reaped between the existence check and read.
        if state == "Z":
            break
        assert time.monotonic() < deadline, f"descendant {pid} still running"
        time.sleep(.01)


def test_asyncio_run_shutdown_after_timeout_reaps_delayed_spawn(tmp_path):
    from fink_tasks import mcp_server
    real_popen = subprocess.Popen
    entered = threading.Event()
    release = threading.Event()
    pidfile = tmp_path / "spawned_pid"

    def delayed(*args, **kwargs):
        entered.set()
        assert release.wait(5)
        proc = real_popen(*args, **kwargs)
        pidfile.write_text(str(proc.pid))
        return proc

    async def check():
        runner = TaskRunner(BackendConfig(worker_timeout=.05))
        with pytest.raises(TimeoutError, match="worker timed out"):
            await runner._run([sys.executable, "-c", "import time; time.sleep(60)"])
        assert entered.is_set()

    timer = threading.Timer(.2, release.set)
    timer.start()
    try:
        with patch.object(mcp_server.subprocess, "Popen", side_effect=delayed):
            asyncio.run(check())  # Shutdown cancels pending asyncio tasks.
        assert pidfile.exists()
        assert_process_gone(int(pidfile.read_text()))
    finally:
        release.set()
        timer.join()


@pytest.mark.parametrize("abort", ["timeout", "repeated_cancel"])
def test_ranking_cleans_late_artifact_and_consumes_detached_error(tmp_path, abort):
    from fink_tasks import mcp_server
    real_popen = subprocess.Popen
    real_temporary_directory = mcp_server.tempfile.TemporaryDirectory
    entered = threading.Event()
    release = threading.Event()
    gate = tmp_path / "gate"
    marker = tmp_path / "late_written"
    diagnostics = []
    code = ("import pathlib,sys,time; out=pathlib.Path(sys.argv[1]); "
            "gate=pathlib.Path(sys.argv[2]); marker=pathlib.Path(sys.argv[3]);\n"
            "while not gate.exists(): time.sleep(.005)\n"
            "out.mkdir(parents=True, exist_ok=True); "
            "(out/'late.json').write_text('late'); marker.touch()")

    def delayed(argv, **kwargs):
        output = argv[argv.index("--output-dir") + 1]
        process = real_popen([sys.executable, "-c", code, output, str(gate), str(marker)], **kwargs)
        entered.set()
        assert release.wait(5)
        return process

    async def check():
        loop = asyncio.get_running_loop()
        loop.set_exception_handler(lambda _loop, context: diagnostics.append(context))
        runner = TaskRunner(BackendConfig(worker_timeout=.05 if abort == "timeout" else 5))
        if abort == "timeout":
            with pytest.raises(TimeoutError, match="worker timed out"):
                await runner.most_points("ss")
        else:
            task = asyncio.create_task(runner.most_points("ss"))
            for _ in range(200):
                if entered.is_set():
                    break
                await asyncio.sleep(.005)
            assert entered.is_set()
            task.cancel()
            await asyncio.sleep(.01)
            task.cancel()
            with pytest.raises(asyncio.CancelledError):
                await task
        assert entered.is_set()
        gate.touch()
        for _ in range(200):
            if marker.exists():
                break
            await asyncio.sleep(.005)
        assert marker.exists()
        release.set()
        await asyncio.sleep(.05)
        gc.collect()

    try:
        with patch.object(mcp_server.tempfile, "TemporaryDirectory",
                          side_effect=lambda **kw: real_temporary_directory(dir=tmp_path, **kw)), \
             patch.object(mcp_server.subprocess, "Popen", side_effect=delayed):
            asyncio.run(check())
        assert not list(tmp_path.glob("fink-mcp-*")), "ranking artifact survived worker shutdown"
        assert not diagnostics, diagnostics
    finally:
        gate.touch()
        release.set()


def test_asyncio_run_shutdown_after_repeated_cancellation_reaps_delayed_spawn(tmp_path):
    from fink_tasks import mcp_server
    real_popen = subprocess.Popen
    entered = threading.Event()
    release = threading.Event()
    pidfile = tmp_path / "spawned_pid"

    def delayed(*args, **kwargs):
        entered.set()
        assert release.wait(5)
        proc = real_popen(*args, **kwargs)
        pidfile.write_text(str(proc.pid))
        return proc

    async def check():
        runner = TaskRunner(BackendConfig())
        task = asyncio.create_task(runner._run([
            sys.executable, "-c", "import time; time.sleep(60)"]))
        for _ in range(200):
            if entered.is_set():
                break
            await asyncio.sleep(.005)
        assert entered.is_set()
        task.cancel()
        await asyncio.sleep(.01)
        task.cancel()  # Interrupt cancellation's own cleanup await.
        with pytest.raises(asyncio.CancelledError):
            await task

    timer = threading.Timer(.2, release.set)
    timer.start()
    try:
        with patch.object(mcp_server.subprocess, "Popen", side_effect=delayed):
            asyncio.run(check())
        assert pidfile.exists()
        assert_process_gone(int(pidfile.read_text()))
    finally:
        release.set()
        timer.join()


def test_delayed_spawn_does_not_block_event_loop():
    from fink_tasks import mcp_server
    real_popen = subprocess.Popen
    def delayed(*args, **kwargs):
        time.sleep(.3)
        return real_popen(*args, **kwargs)
    async def check():
        runner = TaskRunner(BackendConfig(worker_timeout=2))
        fired = []
        loop = asyncio.get_running_loop()
        loop.call_later(.01, lambda: fired.append(loop.time()))
        start = loop.time()
        assert (await runner._run([sys.executable, "-c", "print('ok')"]))[0] == b"ok\n"
        assert fired and fired[0] - start < .15
    with patch.object(mcp_server.subprocess, "Popen", side_effect=delayed):
        run(check())


def test_worker_slot_is_held_until_executor_function_returns():
    entered = threading.Event()
    release = threading.Event()
    previous = threading.getprofile()

    def pause_at_return(frame, event, arg):
        if event == "return" and frame.f_code.co_name == "owned_worker":
            entered.set()
            release.wait(3)

    async def check():
        runner = TaskRunner(BackendConfig(max_workers=1, max_queue=0, worker_timeout=.05))
        with patch.object(runner, "_execute", return_value=(b"ok", b"")):
            first = asyncio.create_task(runner._run(["unused"]))
            for _ in range(200):
                if entered.is_set():
                    break
                await asyncio.sleep(.005)
            assert entered.is_set(), "worker did not reach its return boundary"
            with pytest.raises(TimeoutError, match="worker timed out"):
                await first
            with pytest.raises(RuntimeError, match="worker queue full"):
                await runner._run(["must not start while first thread is running"])
            release.set()
            for _ in range(200):
                if not runner._admission.locked():
                    break
                await asyncio.sleep(.005)
            assert not runner._admission.locked()

    threading.setprofile(pause_at_return)
    try:
        run(check())
    finally:
        release.set()
        threading.setprofile(previous)


@pytest.mark.parametrize("abort", ["cancel", "timeout"])
def test_abort_during_blocked_spawn_retains_slot_until_child_reaped(tmp_path, abort):
    from fink_tasks import mcp_server
    real_popen = subprocess.Popen
    entered = threading.Event()
    release = threading.Event()
    pidfile = tmp_path / "pid"
    def blocked(*args, **kwargs):
        entered.set()
        assert release.wait(5)
        proc = real_popen(*args, **kwargs)
        pidfile.write_text(str(proc.pid))
        return proc
    async def check():
        runner = TaskRunner(BackendConfig(max_workers=1, max_queue=0, worker_timeout=.05 if abort == "timeout" else 5))
        task = asyncio.create_task(runner._run([sys.executable, "-c", "import time; time.sleep(60)"]))
        for _ in range(200):
            if entered.is_set():
                break
            await asyncio.sleep(.005)
        assert entered.is_set()
        if abort == "cancel":
            task.cancel()
            await asyncio.sleep(.02)
            assert not task.done()  # Cancellation drains a still-blocked spawn.
        else:
            with pytest.raises(TimeoutError, match="worker timed out"):
                await asyncio.wait_for(task, 1)
        with pytest.raises(RuntimeError, match="worker queue full"):
            await runner._run([sys.executable, "-c", "print('never')"])
        release.set()
        if abort == "cancel":
            with pytest.raises(asyncio.CancelledError):
                await asyncio.wait_for(task, 2)
        for _ in range(200):
            if pidfile.exists() and not runner._admission.locked():
                break
            await asyncio.sleep(.01)
        assert pidfile.exists() and not runner._admission.locked()
        assert_process_gone(int(pidfile.read_text()))
    try:
        with patch.object(mcp_server.subprocess, "Popen", side_effect=blocked):
            run(check())
    finally:
        release.set()


def test_cancellation_during_spawn_cleans_up_and_releases_slot(tmp_path):
    from fink_tasks import mcp_server
    real_popen = subprocess.Popen
    marker = tmp_path / "spawned"
    def cancel_after_spawn(loop, current, *args, **kwargs):
        proc = real_popen(*args, **kwargs)
        marker.write_text(str(proc.pid))
        loop.call_soon_threadsafe(current.cancel)
        return proc
    async def check():
        runner = TaskRunner(BackendConfig(max_workers=1, max_queue=0))
        loop = asyncio.get_running_loop()
        current = asyncio.current_task()
        assert current is not None
        with patch.object(mcp_server.subprocess, "Popen", side_effect=lambda *a, **kw: cancel_after_spawn(loop, current, *a, **kw)):
            with pytest.raises(asyncio.CancelledError):
                await runner._run([sys.executable, "-c", "import time; time.sleep(60)"])
        assert_process_gone(int(marker.read_text()))
        assert (await runner._run([sys.executable, "-c", "print('recovered')"]))[0] == b"recovered\n"
    run(check())


def test_concurrency_one_worker_queues_second(tmp_path):
    async def check():
        runner = TaskRunner(BackendConfig(max_workers=1, worker_timeout=5))
        gate = tmp_path / "gate"
        started = tmp_path / "started"
        code = "import pathlib,sys,time; pathlib.Path(sys.argv[1]).touch(); gate=pathlib.Path(sys.argv[2]);\nwhile not gate.exists(): time.sleep(.01)"
        first = asyncio.create_task(runner._run([sys.executable, "-c", code, str(started), str(gate)]))
        for _ in range(200):
            if started.exists(): break
            await asyncio.sleep(.01)
        assert started.exists()
        second = asyncio.create_task(runner._run([sys.executable, "-c", "print('second')"]))
        await asyncio.sleep(.1)
        assert not second.done()
        gate.touch()
        await first
        assert (await second)[0] == b"second\n"
    run(check())


def test_queue_timeout_does_not_spawn_second_worker(tmp_path):
    async def check():
        runner = TaskRunner(BackendConfig(max_workers=1, worker_timeout=5, queue_timeout=.15))
        marker = tmp_path / "started"
        first = asyncio.create_task(runner._run([sys.executable, "-c", "import pathlib,time,sys; pathlib.Path(sys.argv[1]).touch(); time.sleep(60)", str(marker)]))
        for _ in range(200):
            if marker.exists(): break
            await asyncio.sleep(.01)
        assert marker.exists()
        with pytest.raises(TimeoutError, match="queue timed out"):
            await runner._run([sys.executable, "-c", "print('never starts')"])
        first.cancel()
        with pytest.raises(asyncio.CancelledError):
            await first
    run(check())


def test_finite_queue_rejects_excess_and_recovers_after_cancel(tmp_path):
    async def check():
        runner = TaskRunner(BackendConfig(max_workers=1, max_queue=1, queue_timeout=5, worker_timeout=5))
        gate = tmp_path / "gate"
        started = tmp_path / "started"
        code = "import pathlib,sys,time; pathlib.Path(sys.argv[1]).touch(); gate=pathlib.Path(sys.argv[2]);\nwhile not gate.exists(): time.sleep(.01)"
        first = asyncio.create_task(runner._run([sys.executable, "-c", code, str(started), str(gate)]))
        for _ in range(200):
            if started.exists(): break
            await asyncio.sleep(.01)
        assert started.exists()
        queued = asyncio.create_task(runner._run([sys.executable, "-c", "print('queued')"]))
        await asyncio.sleep(.02)
        with pytest.raises(RuntimeError, match="worker queue full"):
            await asyncio.wait_for(runner._run([sys.executable, "-c", "print('never')"]), .2)
        queued.cancel()
        with pytest.raises(asyncio.CancelledError):
            await queued
        replacement = asyncio.create_task(runner._run([sys.executable, "-c", "print('replacement')"]))
        gate.touch()
        await first
        assert (await replacement)[0] == b"replacement\n"
    run(check())


def test_failed_spawn_releases_worker_and_admission():
    async def check():
        runner = TaskRunner(BackendConfig(max_workers=1, max_queue=0))
        with pytest.raises(FileNotFoundError):
            await runner._run(["/no/such/fink-worker"])
        assert (await runner._run([sys.executable, "-c", "print('recovered')"]))[0] == b"recovered\n"
    run(check())


def test_ranking_file_limit_rejects_large_artifacts():
    runner = TaskRunner(BackendConfig(max_output_bytes=64))
    def fake(argv, stop):
        output = Path(argv[argv.index("--output-dir") + 1])
        (output / "ss_most_points.json").write_text(json.dumps({"payload": "x" * 128}))
        return b"", b""
    with patch.object(runner, "_execute", side_effect=fake), pytest.raises(RuntimeError, match="ranking output exceeds"):
        run(runner.most_points("ss"))


def test_nonzero_exit_reports_bounded_stderr_without_stdout():
    runner = TaskRunner(BackendConfig(max_output_bytes=128))
    with pytest.raises(RuntimeError, match="failed.*bad news") as exc:
        run(runner._run([sys.executable, "-c", "import sys; print('secret payload'); sys.stderr.write('bad news'); sys.exit(3)"]))
    assert "secret payload" not in str(exc.value)


def test_output_overflow_kills_worker():
    runner = TaskRunner(BackendConfig(max_output_bytes=128))
    with pytest.raises(RuntimeError, match="output exceeds"):
        run(runner._run([sys.executable, "-c", "print('a'*1000000)"]))


def test_mcp_tools_are_async_with_context():
    server = create_server(BackendConfig())
    tools = run(server.list_tools())
    assert {tool.name for tool in tools} == {"object_neighbors", "most_points"}
    for name in ("object_neighbors", "most_points"):
        registered = server._tool_manager.get_tool(name)
        assert registered.context_kwarg == "ctx" and registered.is_async


def test_hook_can_stop_call_before_worker():
    class Deny(Hooks):
        def authorize(self, tool, arguments, context=None):
            raise PermissionError("blocked")
    runner = TaskRunner(BackendConfig(), Deny())
    with patch.object(runner, "_run") as worker, pytest.raises(PermissionError, match="blocked"):
        run(runner.object_neighbors("123"))
    worker.assert_not_called()


def test_http_refuses_non_loopback_and_invalid_limits():
    with patch("fink_tasks.mcp_server.create_server") as factory:
        for args in (["--transport", "streamable-http", "--host", "0.0.0.0"], ["--max-workers", "0"], ["--max-queue", "-1"], ["--max-queue", "1025"], ["--worker-timeout", "0"]):
            with pytest.raises(SystemExit) as exc:
                main(args)
            assert exc.value.code == 2
    factory.assert_not_called()


def test_optional_mcp_entry_point():
    import tomllib
    project = tomllib.loads((Path(__file__).parents[1] / "pyproject.toml").read_text())["project"]
    assert project["scripts"]["fink-mcp"] == "fink_tasks.mcp_server:main"
    assert any(dep.startswith("mcp") for dep in project["optional-dependencies"]["mcp"])

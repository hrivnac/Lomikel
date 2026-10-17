"""Loopback-only GraphSON integration contract for the read-only label exporter."""
import contextlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import subprocess
import sys
import threading
from urllib import request

import pytest

SCRIPT = Path(__file__).resolve().parents[2] / 'work/Clusters/LSST/export_graph_labels.py'


def exporter():
    import importlib.util
    spec = importlib.util.spec_from_file_location('graph_label_export', SCRIPT)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def typed(value):
    if isinstance(value, dict):
        return {'@type': 'g:Map', '@value': [x for k, v in value.items() for x in (typed(k), typed(v))]}
    if isinstance(value, list):
        return {'@type': 'g:List', '@value': [typed(v) for v in value]}
    if isinstance(value, float):
        return {'@type': 'g:Double', '@value': value}
    if isinstance(value, int):
        return {'@type': 'g:Int64', '@value': value}
    return value


def membership(cls='rubin.tag_A', weight=0.8, edge_id=10, ocol_id=20, **changes):
    row = dict(edge_id=edge_id, edge_label='deepcontains', edge_lbl='deepcontains',
               weight=weight, instances='[60001.0, 60002.0]', weights='[0.5, 1.0]',
               ocol_id=ocol_id, ocol_label='OCol', ocol_lbl='OCol',
               cls=cls, classifier='FINK', survey='LSST', flavor='')
    row.update(changes)
    return row


def obj(oid, memberships=None, vertex_id=1):
    return dict(object_id=oid, object_vertex_id=vertex_id, object_label='object',
                object_lbl='object', memberships=memberships if memberships is not None else [])


@contextlib.contextmanager
def server(respond):
    requests = []
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            requests.append({'method': 'GET'})
            self.send_response(200)
            self.end_headers()
        def do_POST(self):
            length = int(self.headers['Content-Length'])
            request = json.loads(self.rfile.read(length))
            requests.append(request)
            result = respond(request, len(requests))
            code, payload = result[:2]
            headers = result[2] if len(result) > 2 else {}
            data = json.dumps(payload).encode()
            self.send_response(code)
            for key, value in headers.items():
                self.send_header(key, value)
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            self.wfile.write(data)
        def log_message(self, *args):
            pass
    httpd = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    thread.start()
    try:
        yield f'http://127.0.0.1:{httpd.server_port}', requests
    finally:
        httpd.shutdown()
        httpd.server_close()
        thread.join()


def response(rows, status=200):
    return {'requestId': 'test', 'status': {'code': status},
            'result': {'data': typed(rows)}}


def run_cli(tmp_path, endpoint, ids, *args):
    source = tmp_path / 'ids.txt'
    source.write_text(ids)
    target = tmp_path / 'labels.jsonl'
    proc = subprocess.run([sys.executable, str(SCRIPT), '--input', str(source),
                           '--output', str(target), '--endpoint', endpoint,
                           '--classifier', 'FINK', '--survey', 'LSST', '--flavor', '',
                           '--max-objects', '4', '--batch-size', '1', '--retries', '0', *args],
                          capture_output=True, text=True, timeout=20)
    return proc, target


def test_janusgraph_relation_identifier_decodes_without_losing_identity():
    import importlib.util
    spec = importlib.util.spec_from_file_location('graph_label_export', SCRIPT)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    identifier = {'@type': 'janusgraph:RelationIdentifier',
                  '@value': {'relationId': '4r44db-5f3i0o-5jp-s0mxs'}}
    assert module.graphson(identifier) == '4r44db-5f3i0o-5jp-s0mxs'


def test_typed_graphson_multilabel_and_unlabeled_object(tmp_path):
    def respond(req, count):
        assert req.keys() == {'gremlin'}
        assert 'addV' not in req['gremlin'] and 'drop(' not in req['gremlin']
        assert ".inE('deepcontains').as('e').outV()" in req['gremlin']
        assert "has('objectId',within(" in req['gremlin']
        return 200, response([obj('000123', [membership(), membership('rubin.tag_B', 0.2, 11, 21)])] if count == 1 else [])
    with server(respond) as (endpoint, calls):
        proc, target = run_cli(tmp_path, endpoint, '000123\n456\n')
    assert proc.returncode == 0, proc.stderr
    rows = [json.loads(s) for s in target.read_text().splitlines()]
    assert [r['object_id'] for r in rows] == ['000123', '456']
    assert [(x['cls'], x['weight']) for x in rows[0]['classes']] == [('rubin.tag_A', 0.8), ('rubin.tag_B', 0.2)]
    assert rows[1]['classes'] == []
    assert rows[0]['object_vertex_id'] == 1
    assert rows[1]['object_vertex_id'] is None
    assert rows[0]['classes'][0]['instances'] == [60001.0, 60002.0]
    assert rows[0]['classes'][0]['weights'] == [0.5, 1.0]
    assert rows[0]['provenance']['scope'] == {'classifier': 'FINK', 'survey': 'LSST', 'flavor': ''}
    assert rows[0]['provenance']['coverage'] == 'known-object-ids-only'
    assert len(calls) == 2


@pytest.mark.parametrize('rows', [
    [obj('1'), obj('1', vertex_id=2)],
    [obj('1', [membership(), membership(edge_id=11)])],
    [obj('1', [membership(ocol_id=20), membership('rubin.tag_A', 0.4, 11, 21)])],
    [obj('1', [membership(weight=float('nan'))])],
    [obj('1', [membership(edge_label='wrong')])],
    [obj('1', [membership(ocol_label='wrong')])],
    [obj('1', [membership(instances='[60001, nope]')])],
    [obj('1', [membership(weights='[NaN]')])],
    [obj('1', [membership(instances=[60001, 60002], weights=[0.5])])],
    [obj('2')],
    [obj('1', [dict(membership(), classifier='OTHER')])],
])
def test_invalid_or_conflicting_graph_data_never_replaces_output(tmp_path, rows):
    with server(lambda *_: (200, response(rows))) as (endpoint, _):
        source = tmp_path / 'labels.jsonl'
        source.write_text('previous\n')
        proc, target = run_cli(tmp_path, endpoint, '1\n')
    assert proc.returncode != 0
    assert target.read_text() == 'previous\n'


@pytest.mark.parametrize('payload', [response([], status=500), {'status': {'code': 200}, 'result': {'data': 'bad'}},
                                     {'status': {'code': 200}, 'result': {'data': typed([{'bad': 1}])}}])
def test_bad_graphson_fails_closed(tmp_path, payload):
    with server(lambda *_: (200, payload)) as (endpoint, _):
        proc, target = run_cli(tmp_path, endpoint, '1\n')
    assert proc.returncode != 0
    assert not target.exists()


def test_missing_optional_edge_metadata_is_explicit(tmp_path):
    m = membership()
    m['edge_lbl'] = m['ocol_lbl'] = m['instances'] = m['weights'] = None
    with server(lambda *_: (200, response([obj('1', [m])]))) as (endpoint, _):
        proc, target = run_cli(tmp_path, endpoint, '1\n')
    assert proc.returncode == 0, proc.stderr
    label = json.loads(target.read_text())['classes'][0]
    assert label['edge_lbl'] is None and label['ocol_lbl'] is None
    assert label['instances'] is None and label['weights'] is None


def test_membership_cap_fails_closed(tmp_path):
    rows = [obj('1', [membership(), membership('rubin.tag_B', 0.2, 11, 21)])]
    with server(lambda *_: (200, response(rows))) as (endpoint, calls):
        proc, target = run_cli(tmp_path, endpoint, '1\n', '--max-memberships', '1')
    assert proc.returncode != 0 and not target.exists()
    assert '.limit(2)' in calls[0]['gremlin']


def test_partial_batch_failure_does_not_publish(tmp_path):
    with server(lambda _, n: (200, response([obj('1')])) if n == 1 else (503, {})) as (endpoint, calls):
        proc, target = run_cli(tmp_path, endpoint, '1\n2\n')
    assert len(calls) == 2
    assert proc.returncode != 0 and not target.exists()


def test_retry_transient_failure(tmp_path):
    with server(lambda _, n: (503, {}) if n == 1 else (200, response([obj('1')]))) as (endpoint, calls):
        proc, target = run_cli(tmp_path, endpoint, '1\n', '--retries', '1')
    assert proc.returncode == 0, proc.stderr
    assert len(calls) == 2 and target.exists()


def test_jsonl_string_id_and_numeric_list_metadata(tmp_path):
    m = membership(instances=[60001.5], weights=[0.75])
    with server(lambda *_: (200, response([obj('9007199254740993', [m])]))) as (endpoint, _):
        proc, target = run_cli(tmp_path, endpoint, '{"object_id":"9007199254740993"}\n')
    assert proc.returncode == 0, proc.stderr
    row = json.loads(target.read_text())
    assert row['object_id'] == '9007199254740993'
    assert row['classes'][0]['instances'] == [60001.5]


@pytest.mark.parametrize('ids', ['1\n1\n', '1.0\n', '1e3\n', '-1\n', '9007199254740993.0\n',
                                  '{"object_id":1}\n', '{"object_id":"1","object_id":"2"}\n', '1\n2\n3\n4\n5\n'])
def test_invalid_input_cannot_make_requests(tmp_path, ids):
    with server(lambda *_: (200, response([]))) as (endpoint, calls):
        proc, target = run_cli(tmp_path, endpoint, ids)
    assert proc.returncode != 0 and not target.exists()
    assert calls == []


def test_remote_http_requires_explicit_opt_in_before_fetch(tmp_path):
    proc, target = run_cli(tmp_path, 'http://192.0.2.1:24444', '1\n', '--timeout', '0.1')
    assert proc.returncode != 0 and not target.exists()
    assert '--allow-insecure-http' in proc.stderr


@pytest.mark.parametrize('status', [301, 302, 303, 307, 308])
def test_redirect_never_reaches_second_server_or_replaces_output(tmp_path, status):
    with server(lambda *_: (200, response([obj('1')]))) as (destination, destination_calls):
        with server(lambda *_: (status, {}, {'Location': destination})) as (endpoint, source_calls):
            proc, target = run_cli(tmp_path, endpoint, '1\n')
    assert len(source_calls) == 1
    assert destination_calls == []
    assert proc.returncode != 0 and not target.exists()
    assert 'redirect' in proc.stderr.lower()


def test_remote_plaintext_opt_in_and_https_are_accepted_by_policy():
    module = exporter()
    module.validate_endpoint('http://134.158.243.144:24444', True)
    module.validate_endpoint('https://134.158.243.144:24444')
    module.validate_endpoint('http://[::1]:24444')
    module.validate_endpoint('http://localhost:24444')


@pytest.mark.parametrize('endpoint', [
    'http://127.0.0.1.evil.example:24444',
    'http://127.0.0.1@evil.example:24444',
    'http://localhost.evil.example:24444',
    'ftp://127.0.0.1:24444',
    'http://127.0.0.1:bad',
])
def test_untrusted_or_invalid_endpoints_are_rejected(endpoint):
    with pytest.raises(ValueError):
        exporter().validate_endpoint(endpoint)


def test_https_to_http_redirect_is_rejected():
    with pytest.raises(ValueError, match='redirect'):
        exporter().NoRedirect().redirect_request(
            request.Request('https://example.org/gremlin', data=b'{}'), None,
            302, 'Found', {'Location': 'http://evil.example/gremlin'},
            'http://evil.example/gremlin')

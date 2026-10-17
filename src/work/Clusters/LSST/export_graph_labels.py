#!/usr/bin/env python3
"""Read-only bounded OCol memberships for supplied LSST object IDs.

This exports a known-ID subset, NOT all graph labels or a transactional graph
snapshot. Each row describes graph membership of an object, not labels for all
its HBase sources; instances are edge MJDs and may cover only some sources.
No graph mutations and no training-feature files are produced.
"""
import argparse
import ipaddress
import json
import math
import os
from pathlib import Path
import re
import sys
import tempfile
import time
from urllib import error, parse, request

FORMAT = 'lomikel-lsst-ocol-known-ids-v1'
MAX_LINE = 1024 * 1024
MAX_RESPONSE = 16 * 1024 * 1024
ID = re.compile(r'[0-9]+\Z')


def unique_pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('duplicate JSON key')
        result[key] = value
    return result


def read_ids(path, maximum):
    ids, seen = [], set()
    with path.open('rb') as stream:
        for line_number, line in enumerate(stream, 1):
            if len(ids) >= maximum:
                raise ValueError('input exceeds --max-objects')
            if len(line) > MAX_LINE:
                raise ValueError(f'line {line_number}: too long')
            raw = line.decode('utf-8').strip()
            if raw.startswith('{'):
                item = json.loads(raw, object_pairs_hook=unique_pairs)
                if not isinstance(item, dict) or set(item) != {'object_id'}:
                    raise ValueError(f'line {line_number}: expected object_id only')
                raw = item['object_id']
            if not isinstance(raw, str) or not ID.fullmatch(raw):
                raise ValueError(f'line {line_number}: expected string decimal object ID')
            if raw in seen:
                raise ValueError(f'line {line_number}: duplicate object ID')
            seen.add(raw)
            ids.append(raw)
    if not ids:
        raise ValueError('empty input')
    return ids


def graphson(value):
    if isinstance(value, dict) and '@type' in value:
        if set(value) != {'@type', '@value'}:
            raise ValueError('malformed GraphSON wrapper')
        kind, content = value['@type'], value['@value']
        if kind == 'g:Map':
            if not isinstance(content, list) or len(content) % 2:
                raise ValueError('malformed GraphSON map')
            pairs = [(graphson(content[i]), graphson(content[i + 1]))
                     for i in range(0, len(content), 2)]
            if any(not isinstance(k, str) for k, _ in pairs):
                raise ValueError('non-string GraphSON map key')
            return unique_pairs(pairs)
        if kind in ('g:List', 'g:Set'):
            if not isinstance(content, list):
                raise ValueError('malformed GraphSON list')
            return [graphson(x) for x in content]
        if kind == 'janusgraph:RelationIdentifier':
            if (not isinstance(content, dict) or set(content) != {'relationId'}
                    or not isinstance(content['relationId'], str) or not content['relationId']):
                raise ValueError('malformed JanusGraph relation identifier')
            return content['relationId']
        if kind in ('g:Int32', 'g:Int64', 'g:Double', 'g:Float', 'g:String', 'g:UUID'):
            return content
        raise ValueError('unsupported GraphSON type: ' + str(kind))
    if isinstance(value, list):
        return [graphson(v) for v in value]
    if isinstance(value, dict):
        return {k: graphson(v) for k, v in value.items()}
    return value


def query(ids, scope, max_memberships):
    # JSON quoting is valid Groovy string literal quoting for decimal string IDs.
    quoted = ','.join(json.dumps(x) for x in ids)
    def literal(x):
        return json.dumps(x)
    c, s, f = (literal(scope[x]) for x in ('classifier', 'survey', 'flavor'))
    return ("g.V().has('lbl','object').has('objectId',within(" + quoted + "))"
            ".limit(" + str(len(ids) + 1) + ")"
            ".project('object_id','object_vertex_id','object_label','object_lbl','memberships')"
            ".by(values('objectId')).by(id()).by(label()).by(values('lbl'))"
            ".by(__.inE('deepcontains').as('e').outV().has('lbl','OCol')"
            ".has('classifier'," + c + ").has('survey'," + s + ").has('flavor'," + f + ")"
            ".as('c').limit(" + str(max_memberships + 1) + ")"
            ".project('edge_id','edge_label','edge_lbl','weight','instances','weights',"
            "'ocol_id','ocol_label','ocol_lbl','cls','classifier','survey','flavor')"
            ".by(select('e').id()).by(select('e').label())"
            ".by(select('e').coalesce(values('lbl'),constant(null)))"
            ".by(select('e').coalesce(values('weight'),constant(null)))"
            ".by(select('e').coalesce(values('instances'),constant(null)))"
            ".by(select('e').coalesce(values('weights'),constant(null)))"
            ".by(select('c').id()).by(select('c').label())"
            ".by(select('c').coalesce(values('lbl'),constant(null)))"
            ".by(select('c').values('cls')).by(select('c').values('classifier'))"
            ".by(select('c').values('survey')).by(select('c').values('flavor')).fold())")


def validate_endpoint(endpoint, allow_insecure_http=False):
    if any(ord(char) < 32 or ord(char) == 127 for char in endpoint):
        raise ValueError('invalid graph endpoint')
    url = parse.urlsplit(endpoint)
    if (url.scheme not in ('http', 'https') or not url.hostname or url.username is not None
            or url.password is not None or url.fragment or url.port == 0):
        raise ValueError('endpoint must be a valid HTTP(S) URL without userinfo or fragment')
    if url.scheme == 'http' and not allow_insecure_http:
        try:
            loopback = ipaddress.ip_address(url.hostname).is_loopback
        except ValueError:
            loopback = url.hostname == 'localhost'
        if not loopback:
            raise ValueError('remote HTTP requires --allow-insecure-http; use HTTPS or loopback')


class NoRedirect(request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError('graph HTTP redirect rejected')


def fetch(endpoint, script, timeout, retries, allow_insecure_http=False):
    validate_endpoint(endpoint, allow_insecure_http)
    opener = request.build_opener(NoRedirect)
    body = json.dumps({'gremlin': script}).encode('utf-8')
    for attempt in range(retries + 1):
        try:
            req = request.Request(endpoint, body, {'Content-Type': 'application/json'}, method='POST')
            with opener.open(req, timeout=timeout) as resp:
                if resp.status != 200:
                    raise ValueError('unexpected HTTP status')
                raw = resp.read(MAX_RESPONSE + 1)
            if len(raw) > MAX_RESPONSE:
                raise ValueError('GraphSON response too large')
            payload = json.loads(raw, object_pairs_hook=unique_pairs)
            if not isinstance(payload, dict) or not isinstance(payload.get('status'), dict):
                raise ValueError('missing GraphSON status')
            if payload['status'].get('code') != 200:
                raise ValueError('Gremlin server returned non-success status')
            data = graphson(payload['result']['data'])
            if not isinstance(data, list):
                raise ValueError('GraphSON result is not a list')
            return data
        except error.HTTPError as exc:
            if 300 <= exc.code < 400:
                raise ValueError('graph HTTP redirect rejected') from exc
            if 500 <= exc.code < 600 and attempt < retries:
                time.sleep(min(0.2 * 2 ** attempt, 2))
                continue
            raise ValueError(f'graph HTTP status {exc.code}') from exc
        except (error.URLError, TimeoutError, OSError) as exc:
            if attempt == retries:
                raise ValueError('graph transport failed after retries') from exc
            time.sleep(min(0.2 * 2 ** attempt, 2))
        except (ValueError, KeyError, TypeError):
            # Malformed GraphSON and Gremlin errors are not retryable.
            raise


def finite_number(value):
    if isinstance(value, bool) or not isinstance(value, (float, int)) or not math.isfinite(value):
        raise ValueError('expected finite numeric edge value')
    return value


def numeric_array(value):
    if value is None:
        return None
    if isinstance(value, str):
        text = value.strip()
        if not (text.startswith('[') and text.endswith(']')):
            raise ValueError('malformed serialized edge array')
        inside = text[1:-1].strip()
        if not inside:
            value = []
        else:
            try:
                value = [float(part.strip()) for part in inside.split(',')]
            except ValueError as exc:
                raise ValueError('malformed serialized edge array') from exc
    if not isinstance(value, list):
        raise ValueError('edge array must be list or bracketed string')
    return [finite_number(x) for x in value]


def parse_rows(rows, ids, scope, maximum):
    found, requested = {}, set(ids)
    if len(rows) > len(ids):
        raise ValueError('unexpected number of object vertices')
    for row in rows:
        if not isinstance(row, dict) or set(row) != {'object_id', 'object_vertex_id', 'object_label', 'object_lbl', 'memberships'}:
            raise ValueError('malformed object vertex')
        oid = row['object_id']
        if not isinstance(oid, str) or oid not in requested or oid in found:
            raise ValueError('unexpected or duplicate object vertex')
        if row['object_label'] != 'object' or row['object_lbl'] != 'object':
            raise ValueError('incorrect object label')
        if row['object_vertex_id'] is None or not isinstance(row['memberships'], list):
            raise ValueError('missing object identity or memberships')
        if len(row['memberships']) > maximum:
            raise ValueError('membership limit exceeded')
        classes, edges, ocols, names = [], set(), set(), set()
        for m in row['memberships']:
            required = {'edge_id', 'edge_label', 'edge_lbl', 'weight', 'instances', 'weights',
                        'ocol_id', 'ocol_label', 'ocol_lbl', 'cls', 'classifier', 'survey', 'flavor'}
            if not isinstance(m, dict) or set(m) != required:
                raise ValueError('malformed membership')
            if (m['edge_label'] != 'deepcontains' or m['ocol_label'] != 'OCol'
                    or m['edge_lbl'] not in ('deepcontains', None)
                    or m['ocol_lbl'] not in ('OCol', None)
                    or any(m[k] != v for k, v in scope.items())):
                raise ValueError('wrong membership direction, label or scope')
            cls = m['cls']
            if not isinstance(cls, str) or not cls or m['edge_id'] is None or m['ocol_id'] is None:
                raise ValueError('missing class or graph identity')
            edge, ocol = repr(m['edge_id']), repr(m['ocol_id'])
            if edge in edges or ocol in ocols or cls in names:
                raise ValueError('duplicate or conflicting membership')
            edges.add(edge); ocols.add(ocol); names.add(cls)
            instances, weights = numeric_array(m['instances']), numeric_array(m['weights'])
            if instances is not None and weights is not None and len(instances) != len(weights):
                raise ValueError('instance/weight length mismatch')
            classes.append({'cls': cls, 'weight': finite_number(m['weight']),
                            'instances': instances, 'weights': weights,
                            'edge_id': m['edge_id'], 'ocol_id': m['ocol_id'],
                            'edge_lbl': m['edge_lbl'], 'ocol_lbl': m['ocol_lbl']})
        found[oid] = {'object_vertex_id': row['object_vertex_id'],
                      'classes': sorted(classes, key=lambda x: x['cls'])}
    return found


def positive(value):
    number = int(value)
    if number < 1:
        raise argparse.ArgumentTypeError('must be positive')
    return number


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--endpoint', default='http://127.0.0.1:24444')
    parser.add_argument('--allow-insecure-http', action='store_true',
                        help='explicitly allow remote plaintext HTTP (responses can be tampered with)')
    parser.add_argument('--classifier', required=True)
    parser.add_argument('--survey', required=True)
    parser.add_argument('--flavor', required=True)
    parser.add_argument('--max-objects', type=positive, default=10000)
    parser.add_argument('--batch-size', type=positive, default=50)
    parser.add_argument('--max-memberships', type=positive, default=1000)
    parser.add_argument('--timeout', type=float, default=15)
    parser.add_argument('--retries', type=int, default=2)
    args = parser.parse_args(argv)
    temp = None
    try:
        if (args.batch_size > args.max_objects or args.batch_size > 100
                or args.max_memberships > 1000 or args.retries < 0 or args.retries > 5
                or not 0 < args.timeout <= 120):
            raise ValueError('invalid request bounds')
        if not args.classifier or not args.survey or args.input.resolve() == args.output.resolve():
            raise ValueError('invalid scope or output path')
        validate_endpoint(args.endpoint, args.allow_insecure_http)
        ids = read_ids(args.input, args.max_objects)  # validate entire input before any request
        scope = {'classifier': args.classifier, 'survey': args.survey, 'flavor': args.flavor}
        # Cross-batch partial failure leaves the old output untouched.
        with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', dir=args.output.parent,
                                         prefix='.' + args.output.name + '.', delete=False) as out:
            temp = Path(out.name)
            for start in range(0, len(ids), args.batch_size):
                batch = ids[start:start + args.batch_size]
                rows = fetch(args.endpoint, query(batch, scope, args.max_memberships), args.timeout,
                             args.retries, args.allow_insecure_http)
                found = parse_rows(rows, batch, scope, args.max_memberships)
                for oid in batch:
                    item = {'format': FORMAT, 'object_id': oid,
                            'object_vertex_id': found[oid]['object_vertex_id'] if oid in found else None,
                            'classes': found[oid]['classes'] if oid in found else [],
                            'graph_object_found': oid in found,
                            'provenance': {'scope': scope, 'endpoint': args.endpoint,
                                           'coverage': 'known-object-ids-only',
                                           'consistency': 'non-transactional per-batch reads',
                                           'unit': 'object memberships; instance MJDs do not label every HBase source'}}
                    out.write(json.dumps(item, allow_nan=False, separators=(',', ':')) + '\n')
            out.flush()
            os.fsync(out.fileno())
        os.replace(temp, args.output)
        print(f'Exported {len(ids)} known object IDs to {args.output}')
        return 0
    except (ValueError, OSError, KeyError, TypeError, UnicodeError) as exc:
        print(f'label export failed: {exc}', file=sys.stderr)
        return 1
    finally:
        if temp is not None:
            temp.unlink(missing_ok=True)


if __name__ == '__main__':
    sys.exit(main())

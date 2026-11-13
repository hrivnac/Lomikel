"""Offline tests for the LSST light-curve tag evaluation (synthetic data only)."""
import copy
import importlib.util
import json
from pathlib import Path

import numpy as np
import pytest

LC = Path(__file__).resolve().parents[2] / 'work/LightCurves/LSST'
spec = importlib.util.spec_from_file_location('lc_evaluate_tags', LC / 'evaluate_tags.py')
ev = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ev)

CLASSES = ['rubin.tag_rise', 'rubin.tag_flare', 'rubin.tag_flat']


def config(**over):
    cfg = json.loads((LC / 'tag_classes.json').read_text())
    cfg['classes'] = CLASSES
    cfg['curve'] = {'windows': ['full', 'until_first_tag'], 'bins': [8]}
    cfg['model'] = {'embedding': [4], 'clusters': [3, 6], 'epochs': 20, 'seed': 1}
    cfg['evaluation'] = dict(cfg['evaluation'], supervised_reference=False)
    cfg.update(over)
    return cfg


def curve(oid, shape, rng, n=24):
    sources = []
    for i in range(n):
        t = i / (n - 1)
        flux = {'rise': 100 + 900 * t, 'flare': 100 + 2000 * np.exp(-((t - 0.3) / 0.05) ** 2), 'flat': 300.0}[shape]
        sources.append({'row_key': f'{int(oid) % 1000:03d}_{oid}_{61000 + 30 * t:.6f}', 'source_id': f'{oid}{i:03d}',
                        'mjd': f'{61000 + 30 * t:.6f}', 'band': 'gri'[i % 3],
                        'flux': str(flux * (1 + 0.03 * rng.normal())), 'flux_err': '10.0'})
    return {'kind': 'dia', 'object_id': oid, 'object_key': f'{int(oid) % 1000:03d}_{oid}_',
            'table': 'rubin.diaSource_static', 'schema': 's', 'complete': True, 'sources': sources}


def export(tmp_path, per_class=40, name='export', tag_at=0.5):
    """Three separable curve shapes; every 8th rise object also carries the flat tag."""
    rng = np.random.default_rng(0)
    out = tmp_path / name
    out.mkdir()
    curves, labels, n = [], [], 0
    for c, shape in zip(CLASSES, ('rise', 'flare', 'flat')):
        for i in range(per_class):
            n += 1
            oid = str(170000000 + n)
            rec = curve(oid, shape, rng)
            classes = [c] + (['rubin.tag_flat'] if shape == 'rise' and i % 8 == 0 else [])
            first = 61000 + 30 * tag_at
            labels.append({'object_id': oid, 'classes': classes, 'first_tag_mjd': first, 'sampled_for': [c],
                           'tags': {k: {'alerts': 1, 'first_mjd': first, 'last_mjd': first} for k in classes}})
            curves.append(rec)
    (out / 'curves.jsonl').write_text(''.join(json.dumps(r) + '\n' for r in curves))
    (out / 'labels.jsonl').write_text(''.join(json.dumps(r) + '\n' for r in labels))
    summary = {'format': 'lomikel-lsst-tag-lightcurves-v1', 'sampling': config()['sampling'],
               'classes': {c: {'status': 'ok', 'table_objects': 100 * (i + 1)} for i, c in enumerate(CLASSES)}}
    (out / 'summary.json').write_text(json.dumps(summary))
    return out


def run(tmp_path, cfg, inp, name='eval'):
    path = tmp_path / f'{name}.json'
    path.write_text(json.dumps(cfg))
    return ev.main(['train', '--config', str(path), '--input', str(inp), '--output', str(tmp_path / name)])


def test_separable_shapes_are_recovered_for_both_windows(tmp_path):
    assert run(tmp_path, config(), export(tmp_path)) == 0
    m = json.loads((tmp_path / 'eval/metrics.json').read_text())
    assert set(m['windows']) == {'full', 'until_first_tag'}
    for w in m['windows'].values():
        assert w['test']['macro_f1'] > 0.8
        assert w['test']['hit_rate'] > w['test_prior_baseline']['hit_rate']
    for f in ('report.md', 'model_full.json', 'model_until_first_tag.json', 'test_per_class_full.csv',
              'predictions_test_full.csv', 'grid_until_first_tag.csv', 'tag_overlap.csv'):
        assert (tmp_path / 'eval' / f).exists()


def test_until_first_tag_uses_only_observations_before_the_first_tag():
    rng = np.random.default_rng(1)
    rec = curve('170000123', 'rise', rng)
    label = {'tags': {'rubin.tag_rise': {'first_mjd': 61010.0}, 'rubin.tag_other': {'first_mjd': 61001.0}}}
    w = ev.window_curve(rec, label, 'until_first_tag', ['rubin.tag_rise'], 3)
    assert w and all(float(s['mjd']) <= 61010.0 for s in w['sources']) and len(w['sources']) < len(rec['sources'])
    # Tags of classes outside the configuration do not move the cutoff.
    assert ev.window_curve(rec, label, 'until_first_tag', ['rubin.tag_rise', 'rubin.tag_other'], 3) is None
    assert ev.window_curve(rec, label, 'full', ['rubin.tag_rise'], 3) is rec
    assert rec['sources'][-1]['mjd'] > '61010'   # input untouched


def test_short_windows_drop_rare_classes_and_report_them(tmp_path):
    # Tag at the very start: every curve is too short before its first tag.
    inp = export(tmp_path, tag_at=0.0)
    cfg = config()
    assert run(tmp_path, dict(cfg, curve=dict(cfg['curve'], windows=['until_first_tag'])), inp) == 1
    assert run(tmp_path, dict(cfg, curve=dict(cfg['curve'], windows=['full'])), inp, 'ok') == 0


def test_split_is_object_disjoint_and_covers_every_class(tmp_path):
    assert run(tmp_path, config(), export(tmp_path)) == 0
    m = json.loads((tmp_path / 'eval/metrics.json').read_text())
    w = m['windows']['full']
    assert sum(w['split'].values()) == m['objects'] == 120
    assert all(min(v.values()) >= 3 for v in w['objects_per_class_and_split'].values())
    test_ids = [line.split(',')[0] for line in (tmp_path / 'eval/predictions_test_full.csv').read_text().splitlines()[1:]]
    assert len(test_ids) == len(set(test_ids)) == w['split']['test']


def test_predict_matches_model_and_rejects_tampering(tmp_path):
    inp = export(tmp_path)
    assert run(tmp_path, config(), inp) == 0
    model = tmp_path / 'eval/model_full.json'
    out = tmp_path / 'pred.jsonl'
    assert ev.main(['predict', '--model', str(model), '--input', str(inp / 'curves.jsonl'), '--output', str(out)]) == 0
    pred = {r['object_id']: r for r in map(json.loads, out.read_text().splitlines())}
    rows = [l.split(',') for l in (tmp_path / 'eval/predictions_test_full.csv').read_text().splitlines()[1:]]
    assert rows and all(pred[r[0]]['cluster'] == int(r[1]) and abs(pred[r[0]]['distance'] - float(r[2])) < 1e-9 for r in rows)
    m = json.loads(model.read_text())
    m['centers'][0][0] += 1.0
    model.write_text(json.dumps(m))
    assert ev.main(['predict', '--model', str(model), '--input', str(inp / 'curves.jsonl'), '--output', str(tmp_path / 'p2')]) == 1


def test_encoder_normalisation_uses_training_rows_only():
    rng = np.random.default_rng(2)
    F = rng.normal(size=(30, 6 * 4 * 3 + 2))
    F[:, 2 * 24:3 * 24] = 1.0
    G = F.copy()
    G[20:] += 1000.0   # non-training rows
    a = ev.Encoder().fit(F, 4, np.arange(20), 3, 5, 1)
    b = ev.Encoder().fit(G, 4, np.arange(20), 3, 5, 1)
    assert np.allclose(a.mean, b.mean) and np.allclose(a.w1, b.w1)


def test_class_not_scanned_by_the_export_is_rejected(tmp_path, capsys):
    cfg = config(classes=CLASSES + ['rubin.tag_never'])
    assert run(tmp_path, cfg, export(tmp_path)) == 1
    assert 'not scanned' in capsys.readouterr().err


def test_empty_table_is_reported_not_fatal(tmp_path, capsys):
    inp = export(tmp_path)
    s = json.loads((inp / 'summary.json').read_text())
    s['classes']['rubin.tag_empty'] = {'status': 'empty table', 'table_objects': 0}
    (inp / 'summary.json').write_text(json.dumps(s))
    assert run(tmp_path, config(classes=CLASSES + ['rubin.tag_empty']), inp) == 0
    assert 'no exported object for rubin.tag_empty' in capsys.readouterr().err


def test_inconsistent_export_and_existing_output_fail_closed(tmp_path):
    inp = export(tmp_path)
    assert run(tmp_path, config(), inp) == 0
    assert run(tmp_path, config(), inp) == 1               # output exists
    lines = (inp / 'labels.jsonl').read_text().splitlines()
    (inp / 'labels.jsonl').write_text('\n'.join(lines[:-1]) + '\n')
    assert run(tmp_path, config(), inp, 'other') == 1      # curve without label
    bad = export(tmp_path, name='bad')
    recs = (bad / 'curves.jsonl').read_text().splitlines()
    r = json.loads(recs[0])
    r['sources'][0]['flux_err'] = '0'
    (bad / 'curves.jsonl').write_text('\n'.join([json.dumps(r)] + recs[1:]) + '\n')
    assert run(tmp_path, config(), bad, 'bad-eval') == 1   # train_lightcurves contract enforced


def test_label_without_first_tag_mjd_is_rejected(tmp_path, capsys):
    inp = export(tmp_path)
    lines = [json.loads(l) for l in (inp / 'labels.jsonl').read_text().splitlines()]
    lines[0]['tags'][lines[0]['classes'][0]]['first_mjd'] = None
    (inp / 'labels.jsonl').write_text(''.join(json.dumps(r) + '\n' for r in lines))
    assert run(tmp_path, config(), inp) == 1
    assert 'first_mjd' in capsys.readouterr().err


@pytest.mark.parametrize('change', [
    {'format': 'x'}, {'classes': ['rubin.tag_rise']}, {'curve': {'windows': ['later'], 'bins': [8]}},
    {'curve': {'windows': ['full'], 'bins': [0]}}, {'model': {'embedding': [4], 'clusters': [1], 'epochs': 5}},
    {'model': {'embedding': [4], 'clusters': [3], 'epochs': 0}},
    {'evaluation': {'validation_fraction': 0.5, 'test_fraction': 0.45}},
])
def test_invalid_configs_are_rejected(tmp_path, change):
    cfg = copy.deepcopy(config())
    cfg.update(change)
    assert run(tmp_path, cfg, export(tmp_path)) == 1


def test_shipped_config_is_valid():
    cfg = ev.load_config(LC / 'tag_classes.json')
    assert len(cfg['classes']) == 7 and set(cfg['curve']['windows']) == {'full', 'until_first_tag'}

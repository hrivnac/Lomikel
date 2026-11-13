"""Offline tests for the LSST PCA+K-means tag evaluation (synthetic data only)."""
import importlib.util
import json
import sys
from pathlib import Path

import numpy as np
import pytest

LSST = Path(__file__).resolve().parents[2] / 'work/Clusters/LSST'
spec = importlib.util.spec_from_file_location('evaluate_tags', LSST / 'evaluate_tags.py')
ev = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ev)

CLASSES = ['rubin.tag_a', 'rubin.tag_b', 'rubin.tag_c']


def config(**over):
    cfg = json.loads((LSST / 'tag_classes.json').read_text())
    cfg.update({'classes': CLASSES, 'features': ['psfFlux', 'snr', 'extendedness'],
                'model': {'clusters': [3, 6], 'components': 2, 'seed': 1, 'scores': 'fraction'}})
    cfg['evaluation'] = dict(cfg['evaluation'], supervised_reference=False)
    cfg.update(over)
    return cfg


def export(tmp_path, per_class=60, overlap=True):
    """Three separable synthetic classes; some class-a alerts also carry class c."""
    rng = np.random.default_rng(0)
    alerts, labels = [], []
    centres = {'rubin.tag_a': (100, 5, 0.1), 'rubin.tag_b': (5000, 50, 0.9), 'rubin.tag_c': (-800, 10, 0.5)}
    n = 0
    for cls, (flux, snr, ext) in centres.items():
        for i in range(per_class):
            n += 1
            oid = f'{170000 + n // 2}{CLASSES.index(cls)}'   # two alerts per object
            key = f'{int(oid) % 1000:03d}_{oid}_{61000 + n / 100}'
            cells = {'r:psfFlux': str(flux * (1 + 0.05 * rng.normal())), 'r:snr': str(snr + rng.normal()),
                     'r:extendedness': str(ext), 'r:band': 'gri'[n % 3]}
            if i % 10 == 0:
                cells.pop('r:snr')            # missing values are imputed
            alerts.append({'kind': 'dia', 'row_key': key, 'table': 'rubin.diaSource_static', 'cells': cells})
            member = [cls] + (['rubin.tag_c'] if overlap and cls == 'rubin.tag_a' and i % 4 == 0 else [])
            labels.append({'row_key': key, 'object_id': oid, 'classes': member, 'sampled_for': cls})
    d = tmp_path / 'export'
    d.mkdir()
    (d / 'alerts.jsonl').write_text(''.join(json.dumps(a) + '\n' for a in alerts))
    (d / 'labels.jsonl').write_text(''.join(json.dumps(l) + '\n' for l in labels))
    (d / 'summary.json').write_text(json.dumps({'alert_table': 'rubin.diaSource_static', 'created': 'x',
        'classes': {c: {'status': 'ok', 'table_alerts': 100 + i, 'table_objects': 50} for i, c in enumerate(CLASSES)}}))
    return d


def run(tmp_path, cfg, name='eval'):
    path = tmp_path / f'{name}.json'
    path.write_text(json.dumps(cfg))
    return ev.main(['train', '--config', str(path), '--input', str(tmp_path / 'export'), '--output', str(tmp_path / name)])


def test_separable_classes_are_recovered_and_outputs_written(tmp_path):
    export(tmp_path)
    assert run(tmp_path, config()) == 0
    out = tmp_path / 'eval'
    m = json.loads((out / 'metrics.json').read_text())
    assert m['test']['hit_rate'] > 0.9 and m['test']['macro_roc_auc'] > 0.9
    assert m['test_prior_baseline']['macro_roc_auc'] == pytest.approx(0.5)
    for f in ('report.md', 'model.json', 'test_per_class.csv', 'cluster_profiles.csv', 'tag_overlap.csv'):
        assert (out / f).is_file()
    # The overlap matrix sees the multi-labels: a quarter of class a also carries c.
    assert m['tag_overlap']['rubin.tag_a']['rubin.tag_c'] == pytest.approx(0.25, abs=0.02)


def test_split_has_no_shared_objects_and_every_class_in_test(tmp_path):
    export(tmp_path)
    cfg = config()
    keys, cells, labels, Y = ev.load_export(tmp_path / 'export', CLASSES)
    objects = [l['object_id'] for l in labels]
    strata = list(ev.primary_labels(Y, [100, 101, 102]))
    split = ev.split_objects(objects, strata, (0.2, 0.2), 42)
    seen = {}
    for o, s in zip(objects, split):
        assert seen.setdefault(o, s) == s
    for i in range(len(CLASSES)):
        assert Y[split == 'test', i].any() and Y[split == 'val', i].any()
    assert list(split) == list(ev.split_objects(objects, strata, (0.2, 0.2), 42))


def test_model_predict_matches_training_scores(tmp_path):
    export(tmp_path)
    assert run(tmp_path, config(band_onehot=False,
                                model={'clusters': [6], 'components': 2, 'seed': 1, 'scores': 'lift'})) == 0
    out = tmp_path / 'eval'
    model = json.loads((out / 'model.json').read_text())
    assert model['format'] == ev.MODEL_FORMAT and model['scores'] == 'lift'
    alerts = tmp_path / 'export/alerts.jsonl'
    assert ev.main(['predict', '--model', str(out / 'model.json'), '--input', str(alerts),
                    '--output', str(tmp_path / 'pred.jsonl')]) == 0
    preds = [json.loads(l) for l in (tmp_path / 'pred.jsonl').open()]
    assert len(preds) == 180 and all(p['predicted'] in CLASSES for p in preds)
    keys, _, labels, Y = ev.load_export(tmp_path / 'export', CLASSES)
    by_key = dict(zip(keys, Y))
    hits = np.mean([by_key[p['row_key']][CLASSES.index(p['predicted'])] for p in preds])
    assert hits > 0.9


def test_class_selection_is_configurable_and_unknown_tags_ignored(tmp_path):
    export(tmp_path)
    assert run(tmp_path, config(classes=['rubin.tag_a', 'rubin.tag_b'])) == 0
    m = json.loads((tmp_path / 'eval/metrics.json').read_text())
    assert m['classes'] == ['rubin.tag_a', 'rubin.tag_b']
    assert m['alerts'] == 120     # class-c-only alerts are dropped


def test_empty_table_is_reported_not_fatal(tmp_path, capsys):
    d = export(tmp_path)
    s = json.loads((d / 'summary.json').read_text())
    s['classes']['rubin.tag_empty'] = {'status': 'empty table', 'table_alerts': 0}
    (d / 'summary.json').write_text(json.dumps(s))
    assert run(tmp_path, config(classes=CLASSES + ['rubin.tag_empty'])) == 0
    assert 'rubin.tag_empty' in capsys.readouterr().err
    assert json.loads((tmp_path / 'eval/metrics.json').read_text())['absent_classes'] == ['rubin.tag_empty']


def test_class_not_scanned_by_the_export_is_rejected(tmp_path, capsys):
    export(tmp_path)
    assert run(tmp_path, config(classes=CLASSES + ['rubin.tag_never_scanned'])) == 1
    assert 'not scanned by this export' in capsys.readouterr().err
    assert not (tmp_path / 'eval').exists()


def test_class_too_rare_for_every_split_fails_closed(tmp_path, capsys):
    d = export(tmp_path)
    labels = [json.loads(l) for l in (d / 'labels.jsonl').open()]
    labels[0]['classes'].append('rubin.tag_rare')       # one object only
    (d / 'labels.jsonl').write_text(''.join(json.dumps(l) + '\n' for l in labels))
    s = json.loads((d / 'summary.json').read_text())
    s['classes']['rubin.tag_rare'] = {'status': 'ok', 'table_alerts': 1}
    (d / 'summary.json').write_text(json.dumps(s))
    assert run(tmp_path, config(classes=CLASSES + ['rubin.tag_rare'])) == 1
    assert 'rubin.tag_rare' in capsys.readouterr().err
    assert not (tmp_path / 'eval').exists()
    m_ok = run(tmp_path, config(classes=CLASSES), 'ok')
    assert m_ok == 0
    cov = json.loads((tmp_path / 'ok/metrics.json').read_text())['objects_per_class_and_split']
    assert all(min(v.values()) >= 3 for v in cov.values())


@pytest.mark.parametrize('change', [
    {'classes': ['rubin.tag_a']},
    {'classes': ['rubin.tag_a', 'rubin.tag_a']},
    {'classes': ['rubin.tag_a', 'other.table']},
    {'features': ['psfFlux', 'diaObjectId']},
    {'features': ['ra']},
    {'transform': 'log'},
    {'min_presence': 0},
    {'model': {'clusters': [1], 'components': 2}},
    {'model': {'clusters': [3], 'components': 1.5}},
    {'model': {'clusters': [3], 'components': 2, 'scores': 'max'}},
    {'format': 'other'},
    {'evaluation': {'min_objects_per_split': 0}},
])
def test_invalid_configs_are_rejected(tmp_path, change):
    export(tmp_path)
    assert run(tmp_path, config(**change)) == 1
    assert not (tmp_path / 'eval').exists()


def test_existing_output_and_inconsistent_export_fail_closed(tmp_path):
    export(tmp_path)
    (tmp_path / 'eval').mkdir()
    assert run(tmp_path, config()) == 1
    labels = tmp_path / 'export/labels.jsonl'
    labels.write_text(''.join(labels.read_text().splitlines(True)[:-1]))
    assert run(tmp_path, config(), 'eval2') == 1
    assert not (tmp_path / 'eval2').exists()


def test_preprocessor_uses_training_statistics_only(tmp_path):
    cfg = config(transform='asinh', band_onehot=True)
    train = [{'r:psfFlux': '10', 'r:snr': '1', 'r:extendedness': '0', 'r:band': 'g'},
             {'r:psfFlux': '30', 'r:snr': '3', 'r:extendedness': '1', 'r:band': 'r'}]
    pre = ev.Preprocessor().fit(train, cfg)
    assert pre.columns()[-6:] == [f'band={b}' for b in 'ugrizy']
    Z = pre.apply(train)
    assert np.allclose(Z[:, :3].mean(axis=0), 0)
    far = pre.apply([{'r:psfFlux': '1e9', 'r:band': 'y'}])
    assert np.isfinite(far).all()
    again = ev.Preprocessor.from_json(json.loads(json.dumps(pre.to_json())))
    assert np.allclose(again.apply(train), Z)

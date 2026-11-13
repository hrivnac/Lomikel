#!/usr/bin/env python3
"""Train and evaluate the LSST DIA light-curve classifier against Fink tag classes.

Input is a directory written by export_tag_lightcurves.groovy (curves.jsonl,
labels.jsonl, summary.json) and the same JSON config (tag_classes.json beside
this script). The unit is one OBJECT light curve; its labels are all configured
rubin.tag_* classes carried by any of its alerts (an object may carry several).

Procedure (all choices come from the config, so re-running with more data or
other classes needs no code change):
  * objects are split once into train / validation / test, stratified by the
    rarest tag (no object in two splits);
  * for every curve window (curve.windows):
      full             all exported observations,
      until_first_tag  observations up to the first configured tag of the
                       object (what was known when Fink first tagged it);
    curves with fewer than sampling.min_points observations or no time span in
    the window are left out and counted;
  * features: train_lightcurves.encode_features (six bands x bins of
    asinh flux, log error and presence, plus duration and flux scale);
    normalisation fitted on train only;
  * model: the masked autoencoder of train_lightcurves.py (tanh encoder,
    epochs chosen on validation) and K-means on the training embeddings; each
    cluster gets the class profile (fraction of its training objects carrying
    each class) and an object's class scores are its nearest cluster's profile;
  * bins x embedding x clusters are chosen on validation by the selection
    metric, then train+validation are refitted (same number of epochs) and
    evaluated once on the untouched test objects;
  * references on the same split: class priors (no features), K-means on the
    binned features without the encoder and, optionally, a supervised random
    forest on the binned features (how much the features allow).

Metrics (top-1 counted correct when it is one of the object's tags): hit rate,
per-class precision/recall/F1, macro F1, ROC AUC and average precision,
cluster purity, NMI and ARI. Outputs go to a fresh directory: report.md,
metrics.json and per window CSV tables, PNG figures and model_<window>.json.

  python3 evaluate_tags.py train --config tag_classes.json \\
      --input ../run/lsst-lc-tag-eval/export --output ../run/lsst-lc-tag-eval/eval
  python3 evaluate_tags.py predict --model ../run/lsst-lc-tag-eval/eval/model_full.json \\
      --input curves.jsonl --output predictions.jsonl
"""
import argparse
import hashlib
import importlib.util
import json
import math
import os
import shutil
import sys
import tempfile
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import train_lightcurves as tl  # noqa: E402  (encode_features, validate, model_digest)


def _load_pca_eval():
    """Shared split/metric helpers of the alert PCA+K-means tag evaluation."""
    path = HERE.parent.parent / 'Clusters' / 'LSST' / 'evaluate_tags.py'
    spec = importlib.util.spec_from_file_location('lsst_pca_evaluate_tags', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


pe = _load_pca_eval()
EvalError, strict_json, split_objects, check_coverage = pe.EvalError, pe.strict_json, pe.split_objects, pe.check_coverage
metrics, cluster_quality, primary_labels = pe.metrics, pe.cluster_quality, pe.primary_labels
write_csv, fmt, short, software = pe.write_csv, pe.fmt, pe.short, pe.software

CONFIG_FORMAT = 'lomikel-lsst-lc-tag-eval-config-v1'
MODEL_FORMAT = 'lomikel-lsst-lc-nn-kmeans-tags-v1'
WINDOWS = ('full', 'until_first_tag')


# ---------------------------------------------------------------- input --
def load_config(path):
    cfg = strict_json(Path(path).read_text(encoding='utf-8'), path)
    if not isinstance(cfg, dict) or cfg.get('format') != CONFIG_FORMAT:
        raise EvalError('unsupported config format')
    classes = cfg.get('classes')
    if (not isinstance(classes, list) or len(classes) < 2 or len(set(classes)) != len(classes)
            or not all(isinstance(c, str) and c.startswith('rubin.tag_') for c in classes)):
        raise EvalError('config needs at least two unique rubin.tag_* classes')
    s = cfg.get('sampling', {})
    mp = s.get('min_points', 3)
    if not isinstance(mp, int) or mp < 3:
        raise EvalError('sampling.min_points must be an integer >= 3')
    curve = cfg.get('curve', {})
    windows = curve.get('windows', ['full'])
    if not isinstance(windows, list) or not windows or len(set(windows)) != len(windows) or not set(windows) <= set(WINDOWS):
        raise EvalError(f'curve.windows must be a non-empty list from {WINDOWS}')
    ints = lambda v, lo, hi: isinstance(v, list) and v and all(isinstance(x, int) and lo <= x <= hi for x in v)
    if not ints(curve.get('bins', [16]), 1, 512):
        raise EvalError('curve.bins must be a list of integers in [1, 512]')
    model = cfg.get('model', {})
    if not ints(model.get('embedding', [8]), 1, 512):
        raise EvalError('model.embedding must be a list of integers in [1, 512]')
    if not ints(model.get('clusters'), 2, 100000):
        raise EvalError('model.clusters must be a list of integers >= 2')
    ep = model.get('epochs', 100)
    if not isinstance(ep, int) or not 1 <= ep <= 10000:
        raise EvalError('model.epochs must be an integer in [1, 10000]')
    ev = cfg.get('evaluation', {})
    vf, tf = ev.get('validation_fraction', 0.2), ev.get('test_fraction', 0.2)
    if not (0 < vf < 1 and 0 < tf < 1 and vf + tf < 0.9):
        raise EvalError('validation/test fractions must be positive and leave >= 10% for training')
    mo = ev.get('min_objects_per_split', 3)
    if not isinstance(mo, int) or mo < 1:
        raise EvalError('evaluation.min_objects_per_split must be a positive integer')
    if ev.get('selection_metric', 'macro_f1') not in ('macro_f1', 'hit_rate', 'macro_ap'):
        raise EvalError('selection_metric must be macro_f1, hit_rate or macro_ap')
    return cfg


def load_export(directory, classes):
    """Validated curves and labels keyed by object ID (sorted)."""
    directory = Path(directory)
    curves = {}
    for n, line in enumerate((directory / 'curves.jsonl').open(encoding='utf-8'), 1):
        rec = strict_json(line, f'curves.jsonl:{n}')
        try:
            tl.validate(rec)
        except ValueError as exc:
            raise EvalError(f'curves.jsonl:{n}: {exc}') from exc
        if rec['kind'] != 'dia' or rec['object_id'] in curves:
            raise EvalError(f'non-DIA or duplicate object at curves.jsonl:{n}')
        curves[rec['object_id']] = rec
    labels = {}
    for n, line in enumerate((directory / 'labels.jsonl').open(encoding='utf-8'), 1):
        rec = strict_json(line, f'labels.jsonl:{n}')
        oid = rec.get('object_id')
        if oid not in curves or oid in labels or not isinstance(rec.get('classes'), list) or not isinstance(rec.get('tags'), dict):
            raise EvalError(f'label without curve or duplicate at labels.jsonl:{n}')
        for c in rec['classes']:
            first = rec['tags'].get(c, {}).get('first_mjd') if isinstance(rec['tags'].get(c), dict) else None
            if not isinstance(first, (int, float)) or isinstance(first, bool) or not math.isfinite(first):
                raise EvalError(f'labels.jsonl:{n}: class {c} lacks a finite tags.first_mjd')
        labels[oid] = rec
    if set(labels) != set(curves):
        raise EvalError('curves.jsonl and labels.jsonl do not describe the same objects')
    objects = sorted(curves)
    Y = np.array([[c in labels[o]['classes'] for c in classes] for o in objects], dtype=bool).reshape(len(objects), len(classes))
    keep = Y.any(axis=1)
    objects = [o for o, k in zip(objects, keep) if k]
    return objects, [curves[o] for o in objects], [labels[o] for o in objects], Y[keep]


def window_curve(record, label, window, classes, min_points):
    """Record restricted to the window, or None when too short / without time span."""
    if window == 'full':
        return record
    first = min(label['tags'][c]['first_mjd'] for c in classes if c in label['tags'])
    sources = [s for s in record['sources'] if float(s['mjd']) <= first + 1e-6]
    if len(sources) < min_points or not float(sources[-1]['mjd']) > float(sources[0]['mjd']):
        return None
    return dict(record, sources=sources)


# ---------------------------------------------------------------- model --
class Encoder:
    """Masked autoencoder of train_lightcurves.fit_kind (same initialisation and Adam)."""

    def fit(self, F, bins, rows, embedding, epochs, seed, val_rows=None):
        cells = 6 * bins
        rng = np.random.default_rng(seed)
        self.bins = bins
        self.mean = F[rows].mean(axis=0)
        scale = F[rows].std(axis=0)
        self.scale = np.where(scale > 0, scale, 1.0)
        x = (F - self.mean) / self.scale
        target, mask = F[:, :cells], F[:, 2 * cells:3 * cells]
        d = x.shape[1]
        w1 = rng.normal(0, 1 / math.sqrt(d), (d, embedding))
        b1 = np.zeros(embedding)
        w2 = rng.normal(0, 1 / math.sqrt(embedding), (embedding, cells))
        b2 = np.zeros(cells)
        params = [w1, b1, w2, b2]
        moments = [np.zeros_like(p) for p in params]
        variances = [np.zeros_like(p) for p in params]

        def loss(idx):
            z = np.tanh(x[idx] @ params[0] + params[1])
            r = (z @ params[2] + params[3] - target[idx]) * mask[idx]
            return float(np.sum(r ** 2) / max(1, np.sum(mask[idx])))

        best, step = None, 0
        for epoch in range(epochs):
            for batch in np.array_split(rng.permutation(rows), max(1, math.ceil(len(rows) / 128))):
                z = np.tanh(x[batch] @ params[0] + params[1])
                residual = (z @ params[2] + params[3] - target[batch]) * mask[batch]
                dy = 2 * residual / max(1, np.sum(mask[batch]))
                back = (dy @ params[2].T) * (1 - z * z)
                grads = [x[batch].T @ back, back.sum(axis=0), z.T @ dy, dy.sum(axis=0)]
                step += 1
                for p, g, m, v in zip(params, grads, moments, variances):
                    m *= .9
                    m += .1 * g
                    v *= .999
                    v += .001 * g * g
                    p -= .01 * (m / (1 - .9 ** step)) / (np.sqrt(v / (1 - .999 ** step)) + 1e-8)
            if val_rows is not None:
                score = loss(val_rows)
                if not math.isfinite(score):
                    raise EvalError('non-finite autoencoder loss')
                if best is None or score < best[0]:
                    best = (score, epoch + 1, [p.copy() for p in params])
        if best is not None:
            params[:] = best[2]
            self.best_epoch, self.val_loss = best[1], best[0]
        else:
            self.best_epoch, self.val_loss = epochs, None
        self.w1, self.b1 = params[0], params[1]
        self.train_loss = loss(rows)
        return self

    def embed(self, F):
        return np.tanh(((F - self.mean) / self.scale) @ self.w1 + self.b1)


class Clusters:
    def fit(self, E, Y, k, seed):
        from sklearn.cluster import KMeans
        if len(E) <= k:
            raise EvalError(f'not enough training objects for K={k}')
        self.km = KMeans(n_clusters=k, n_init=10, random_state=seed).fit(E)
        labels = self.km.labels_
        self.counts = np.bincount(labels, minlength=k)
        prior = Y.mean(axis=0)
        self.profile = np.array([(Y[labels == j].sum(axis=0) + prior) / (self.counts[j] + 1) for j in range(k)])
        d = np.linalg.norm(E - self.km.cluster_centers_[labels], axis=1)
        self.radii = np.array([np.quantile(d[labels == j], 0.99) if self.counts[j] else 0.0 for j in range(k)])
        return self

    def assign(self, E):
        dist = np.linalg.norm(E[:, None, :] - self.km.cluster_centers_[None, :, :], axis=2)
        j = dist.argmin(axis=1)
        return j, dist[np.arange(len(j)), j]

    def scores(self, E):
        return self.profile[self.assign(E)[0]]


def scaled(F, rows):
    mean, sd = F[rows].mean(axis=0), F[rows].std(axis=0)
    return (F - mean) / np.where(sd > 0, sd, 1.0)


# -------------------------------------------------------------- evaluate --
def evaluate_window(window, cfg, classes, records, labels, Y, objects, split, sizes):
    """Grid search on validation, refit on train+validation, test once."""
    from sklearn.ensemble import RandomForestClassifier
    s, m, ev = cfg.get('sampling', {}), cfg['model'], cfg.get('evaluation', {})
    seed, metric, epochs = m.get('seed', 42), ev.get('selection_metric', 'macro_f1'), m.get('epochs', 100)
    curves = [window_curve(r, l, window, classes, s.get('min_points', 3)) for r, l in zip(records, labels)]
    keep = np.array([c is not None for c in curves])
    dropped = {c: int(np.sum(~keep & Y[:, i])) for i, c in enumerate(classes)}
    curves = [c for c in curves if c is not None]
    Yw, sw, ow = Y[keep], split[keep], [o for o, k in zip(objects, keep) if k]
    minimum = ev.get('min_objects_per_split', 3)
    coverage = {c: {x: int(np.sum((sw == x) & Yw[:, i])) for x in ('train', 'val', 'test')} for i, c in enumerate(classes)}
    active = [c for c in classes if min(coverage[c].values()) >= minimum]
    skipped = [c for c in classes if c not in active]
    if len(active) < 2:
        raise EvalError(f'{window}: fewer than two classes with {minimum} objects in every split: {coverage}')
    idx = [classes.index(c) for c in active]
    Yw = Yw[:, idx]
    has = Yw.any(axis=1)
    curves, Yw, sw, ow = [c for c, h in zip(curves, has) if h], Yw[has], sw[has], [o for o, h in zip(ow, has) if h]
    sizes_w = [sizes[i] for i in idx]
    tr, va, te = np.where(sw == 'train')[0], np.where(sw == 'val')[0], np.where(sw == 'test')[0]
    features = {}

    def feats(bins):
        if bins not in features:
            features[bins] = np.stack([tl.encode_features(c, bins) for c in curves])
        return features[bins]

    grid = []
    for bins in sorted(set(cfg['curve'].get('bins', [16]))):
        F = feats(bins)
        for emb in sorted(set(m.get('embedding', [8]))):
            enc = Encoder().fit(F, bins, tr, emb, epochs, seed, val_rows=va)
            Etr, Eva = enc.embed(F[tr]), enc.embed(F[va])
            for k in sorted(set(m['clusters'])):
                if k >= len(tr) or len(np.unique(Etr, axis=0)) < k:
                    continue
                res = metrics(Clusters().fit(Etr, Yw[tr], k, seed).scores(Eva), Yw[va], active)
                res.pop('per_class')
                grid.append({'bins': bins, 'embedding': emb, 'k': k, 'best_epoch': enc.best_epoch,
                             'val_loss': enc.val_loss, 'validation': res})
                print(f'{window} bins={bins} emb={emb} K={k}: validation {metric}={res[metric]:.3f} '
                      f'hit={res["hit_rate"]:.3f}', file=sys.stderr)
    if not grid:
        raise EvalError(f'{window}: no grid point fits the training data')
    best = max(grid, key=lambda g: (g['validation'][metric], -g['k'], -g['embedding'], -g['bins']))
    fit = np.concatenate([tr, va])
    F = feats(best['bins'])
    enc = Encoder().fit(F, best['bins'], fit, best['embedding'], best['best_epoch'], seed)
    Efit, Ete = enc.embed(F[fit]), enc.embed(F[te])
    model = Clusters().fit(Efit, Yw[fit], best['k'], seed)
    S_te = model.scores(Ete)
    clusters_te, dist_te = model.assign(Ete)
    test = metrics(S_te, Yw[te], active)
    prior = Yw[fit].mean(axis=0)
    base = metrics(np.tile(prior, (len(te), 1)) + 1e-9 * np.arange(len(active))[::-1], Yw[te], active)
    prim_te = primary_labels(Yw[te], sizes_w)
    quality = cluster_quality(clusters_te, Yw[te], prim_te)
    Fs = scaled(F, fit)
    raw_km = Clusters().fit(Fs[fit], Yw[fit], best['k'], seed)
    no_encoder = metrics(raw_km.scores(Fs[te]), Yw[te], active)
    reference = None
    if ev.get('supervised_reference', True):
        rf = RandomForestClassifier(n_estimators=300, min_samples_leaf=2, class_weight='balanced',
                                    n_jobs=-1, random_state=seed).fit(Fs[fit], Yw[fit])
        proba = rf.predict_proba(Fs[te])
        reference = metrics(np.column_stack([p[:, 1] if p.shape[1] == 2 else np.zeros(len(te)) for p in proba]),
                            Yw[te], active)
    model_json = {'format': MODEL_FORMAT, 'kind': 'dia', 'window': window, 'classes': active,
                  'bands': list(tl.BANDS), 'bins': best['bins'], 'feature_mean': enc.mean.tolist(),
                  'feature_scale': enc.scale.tolist(), 'encoder_weights': enc.w1.tolist(),
                  'encoder_bias': enc.b1.tolist(), 'centers': model.km.cluster_centers_.tolist(),
                  'radii': model.radii.tolist(), 'cluster_counts': model.counts.tolist(),
                  'cluster_profiles': model.profile.tolist(), 'epochs': best['best_epoch'],
                  'provenance': {'fitted_on': 'train+validation objects', 'selected_by': metric,
                                 'radius_quantile': 0.99, 'feature': 'train_lightcurves.encode_features'}}
    model_json['model_id'] = tl.model_digest(model_json)
    counts = {x: int(np.sum(sw == x)) for x in ('train', 'val', 'test')}
    result = {'window': window, 'classes': active, 'skipped_classes': skipped, 'objects': len(ow),
              'split': counts, 'objects_per_class_and_split': {c: coverage[c] for c in classes},
              'dropped_short_in_window': dropped, 'selected': {k: best[k] for k in ('bins', 'embedding', 'k', 'best_epoch')},
              'selection_metric': metric, 'grid': grid, 'test': test, 'test_prior_baseline': base,
              'test_kmeans_without_encoder': no_encoder, 'supervised_reference': reference,
              'test_cluster_quality': quality, 'test_outliers': int(np.sum(dist_te > model.radii[clusters_te])),
              'autoencoder': {'train_loss': enc.train_loss, 'epochs': best['best_epoch']},
              'model_id': model_json['model_id']}
    plot = {'E': Ete, 'primary': prim_te, 'pred': S_te.argmax(axis=1), 'clusters': clusters_te, 'Y': Yw[te],
            'test_objects': [ow[i] for i in te], 'S': S_te, 'dist': dist_te}
    return result, model_json, plot


def figures(out, window, result, plot):
    try:
        import matplotlib
        matplotlib.use('Agg')
        import matplotlib.pyplot as plt
    except ImportError:
        return ['matplotlib not installed: no figures']
    names = [short(c) for c in result['classes']]
    n = len(names)
    conf = np.zeros((n, n))
    for p, y in zip(plot['pred'], plot['primary']):
        conf[y, p] += 1
    conf = conf / np.maximum(conf.sum(axis=1, keepdims=True), 1)
    fig, ax = plt.subplots(figsize=(7, 6))
    im = ax.imshow(conf, cmap='Blues', vmin=0, vmax=1)
    ax.set_xticks(range(n), names, rotation=60, ha='right', fontsize=8)
    ax.set_yticks(range(n), names, fontsize=8)
    for i in range(n):
        for j in range(n):
            ax.text(j, i, f'{conf[i, j]:.2f}', ha='center', va='center', fontsize=7, color='k' if conf[i, j] < 0.6 else 'w')
    ax.set_xlabel('predicted (cluster profile)')
    ax.set_ylabel('true (rarest tag)')
    ax.set_title(f'Test objects, row-normalised ({window})')
    fig.colorbar(im, ax=ax)
    fig.tight_layout()
    fig.savefig(out / f'confusion_{window}.png', dpi=120)
    plt.close(fig)

    sel = result['selected']
    pts = [g for g in result['grid'] if g['bins'] == sel['bins'] and g['embedding'] == sel['embedding']]
    fig, ax = plt.subplots(figsize=(6, 4))
    for key in ('macro_f1', 'hit_rate', 'macro_ap', 'macro_roc_auc'):
        ax.plot([g['k'] for g in pts], [g['validation'][key] for g in pts], marker='o', label=key)
    ax.set_xscale('log', base=2)
    ax.set_xlabel('K (clusters)')
    ax.set_ylabel('validation')
    ax.legend(fontsize=8)
    ax.set_title(f"Model selection ({window}, bins {sel['bins']}, embedding {sel['embedding']})")
    fig.tight_layout()
    fig.savefig(out / f'k_selection_{window}.png', dpi=120)
    plt.close(fig)

    E = plot['E']
    if E.shape[1] >= 2:
        E = E - E.mean(axis=0)
        _, _, vt = np.linalg.svd(E, full_matrices=False)
        P = E @ vt[:2].T
        fig, ax = plt.subplots(figsize=(7, 6))
        cmap = plt.get_cmap('tab10')
        for i, name in enumerate(names):
            mask = plot['primary'] == i
            ax.scatter(P[mask, 0], P[mask, 1], s=6, alpha=0.6, color=cmap(i % 10), label=name)
        ax.set_xlabel('embedding PC1')
        ax.set_ylabel('embedding PC2')
        ax.legend(fontsize=7, markerscale=3)
        ax.set_title(f'Test objects in encoder space ({window}; colour = rarest tag)')
        fig.tight_layout()
        fig.savefig(out / f'embedding_{window}.png', dpi=120)
        plt.close(fig)
    return []


def train(args):
    cfg = load_config(args.config)
    classes = cfg['classes']
    summary = strict_json((Path(args.input) / 'summary.json').read_text(encoding='utf-8'), 'summary.json')
    if summary.get('format') != 'lomikel-lsst-tag-lightcurves-v1':
        raise EvalError('input is not an export_tag_lightcurves.groovy directory')
    scanned = summary.get('classes', {})
    unscanned = [c for c in classes if c not in scanned]
    if unscanned:
        raise EvalError('classes not scanned by this export (re-export with this config): ' + ', '.join(unscanned))
    failed = [c for c in classes if scanned[c].get('status') not in ('ok', 'empty table')]
    if failed:
        raise EvalError('classes whose table could not be read: ' + ', '.join(failed))
    if summary.get('sampling') != cfg.get('sampling'):
        print('warning: export sampling settings differ from this config', file=sys.stderr)
    objects, records, labels, Y = load_export(args.input, classes)
    absent = [c for c in classes if not Y[:, classes.index(c)].any()]
    unexpected = [c for c in absent if scanned[c].get('status') != 'empty table']
    if unexpected:
        raise EvalError('scanned classes without exported objects: ' + ', '.join(unexpected))
    if absent:
        print('warning: no exported object for ' + ', '.join(absent), file=sys.stderr)
    active = [c for c in classes if c not in absent]
    Y = Y[:, [classes.index(c) for c in active]]
    classes = active
    if len(classes) < 2:
        raise EvalError('fewer than two classes have objects')
    keep = Y.any(axis=1)
    objects, records, labels, Y = ([o for o, k in zip(objects, keep) if k], [r for r, k in zip(records, keep) if k],
                                   [l for l, k in zip(labels, keep) if k], Y[keep])
    ev = cfg.get('evaluation', {})
    sizes = [scanned[c].get('table_objects', 0) for c in classes]
    rank = {i: r for r, i in enumerate(np.argsort(sizes, kind='stable'))}
    strata = [rank[i] for i in primary_labels(Y, sizes)]
    split = split_objects(objects, strata, (ev.get('validation_fraction', 0.2), ev.get('test_fraction', 0.2)),
                          ev.get('split_seed', 42))
    # Full curves must cover every class; shorter windows may lose rare classes (reported).
    check_coverage(objects, split, Y, classes, ev.get('min_objects_per_split', 3))

    out = Path(args.output)
    if out.exists():
        raise EvalError(f'output {out} exists; choose a fresh directory')
    out.parent.mkdir(parents=True, exist_ok=True)
    stage = Path(tempfile.mkdtemp(prefix='.lsst-lc-tag-eval-', dir=out.parent))
    try:
        results, notes = {}, []
        for window in cfg['curve'].get('windows', ['full']):
            res, model_json, plot = evaluate_window(window, cfg, classes, records, labels, Y, objects, split, sizes)
            results[window] = res
            notes += figures(stage, window, res, plot)
            (stage / f'model_{window}.json').write_text(json.dumps(model_json) + '\n', encoding='utf-8')
            rows = []
            for c in res['classes']:
                a, b = res['test']['per_class'][c], res['test_prior_baseline']['per_class'][c]
                nk = res['test_kmeans_without_encoder']['per_class'][c]
                r = res['supervised_reference']['per_class'][c] if res['supervised_reference'] else {}
                rows.append([c, a['support'], a['base_rate'], a['precision'], a['recall'], a['f1'], a['roc_auc'],
                             a['average_precision'], b['f1'], nk['f1'], r.get('f1'), r.get('roc_auc')])
            write_csv(stage / f'test_per_class_{window}.csv',
                      ['class', 'objects', 'base_rate', 'precision', 'recall', 'f1', 'roc_auc', 'average_precision',
                       'prior_f1', 'kmeans_no_encoder_f1', 'rf_f1', 'rf_roc_auc'], rows)
            write_csv(stage / f'predictions_test_{window}.csv', ['object_id', 'cluster', 'distance', 'predicted', 'tags'] + res['classes'],
                      [[o, int(j), float(d), res['classes'][int(p)], ';'.join(c for c, y in zip(res['classes'], yy) if y)]
                       + list(map(float, sc)) for o, j, d, p, yy, sc in
                       zip(plot['test_objects'], plot['clusters'], plot['dist'], plot['pred'], plot['Y'], plot['S'])])
            write_csv(stage / f'grid_{window}.csv', ['bins', 'embedding', 'k', 'best_epoch', 'val_loss', 'macro_f1', 'hit_rate', 'macro_roc_auc', 'macro_ap'],
                      [[g['bins'], g['embedding'], g['k'], g['best_epoch'], g['val_loss']] +
                       [g['validation'][k] for k in ('macro_f1', 'hit_rate', 'macro_roc_auc', 'macro_ap')] for g in res['grid']])
        cooc = np.array([[Y[Y[:, i], j].mean() if Y[:, i].any() else 0 for j in range(len(classes))] for i in range(len(classes))])
        write_csv(stage / 'tag_overlap.csv', ['class'] + classes, [[c] + list(map(float, r)) for c, r in zip(classes, cooc)])
        result = {'format': 'lomikel-lsst-lc-tag-eval-v1', 'classes': classes, 'absent_classes': absent,
                  'objects': len(objects), 'sources': int(sum(len(r['sources']) for r in records)),
                  'split': {x: int(np.sum(split == x)) for x in ('train', 'val', 'test')},
                  'windows': results, 'software': software(), 'export_summary': summary,
                  'tag_overlap': {c: {d: float(cooc[i, j]) for j, d in enumerate(classes)} for i, c in enumerate(classes)},
                  'notes': notes}
        (stage / 'metrics.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
        (stage / 'report.md').write_text(report(result), encoding='utf-8')
        os.replace(stage, out)
    finally:
        if stage.exists():
            shutil.rmtree(stage)
    for window, r in results.items():
        t, b, ref = r['test'], r['test_prior_baseline'], r['supervised_reference']
        print(f"{window}: {r['objects']} objects, bins={r['selected']['bins']} emb={r['selected']['embedding']} "
              f"K={r['selected']['k']} test hit={t['hit_rate']:.3f} macro_f1={t['macro_f1']:.3f} "
              f"macro_auc={t['macro_roc_auc']:.3f} (prior hit={b['hit_rate']:.3f}"
              + (f", RF macro_f1={ref['macro_f1']:.3f}" if ref else '') + ')')
    print(f'-> {out}')


def report(r):
    s = r['export_summary']
    lines = ['# LSST light-curve (autoencoder + K-means) tag evaluation', '',
             f"{r['objects']} DIA objects ({r['sources']} observations), split by object: "
             + ', '.join(f'{k} {v}' for k, v in r['split'].items()) + '.',
             'An object carries every configured tag that any of its alerts carries; a top-1 prediction counts as correct when it is any tag of the object.', '']
    for window, w in r['windows'].items():
        t, b, nk, ref, q = w['test'], w['test_prior_baseline'], w['test_kmeans_without_encoder'], w['supervised_reference'], w['test_cluster_quality']
        sel = w['selected']
        lines += [f'## Window: {window}', '',
                  f"{w['objects']} objects (train/val/test {w['split']['train']}/{w['split']['val']}/{w['split']['test']}); "
                  f"selected bins {sel['bins']}, embedding {sel['embedding']}, K={sel['k']}, {sel['best_epoch']} epochs (validation {w['selection_metric']}).",
                  'Objects per class (train/val/test): ' + '; '.join(
                      f"{short(c)} {v['train']}/{v['val']}/{v['test']}" for c, v in w['objects_per_class_and_split'].items()) + '.']
        if w['skipped_classes']:
            lines.append('Not evaluable in this window (too few objects): ' + ', '.join(map(short, w['skipped_classes'])) + '.')
        if any(w['dropped_short_in_window'].values()):
            lines.append('Curves too short in this window, per class: ' + ', '.join(
                f'{short(c)} {n}' for c, n in w['dropped_short_in_window'].items() if n) + '.')
        lines += ['', '| model (test objects) | hit rate | macro F1 | macro ROC AUC | macro AP |', '|---|---|---|---|---|',
                  f"| class prior (no features) | {fmt(b['hit_rate'])} | {fmt(b['macro_f1'])} | {fmt(b['macro_roc_auc'])} | {fmt(b['macro_ap'])} |",
                  f"| K-means on binned curves (no encoder) | {fmt(nk['hit_rate'])} | {fmt(nk['macro_f1'])} | {fmt(nk['macro_roc_auc'])} | {fmt(nk['macro_ap'])} |",
                  f"| autoencoder + K-means | {fmt(t['hit_rate'])} | {fmt(t['macro_f1'])} | {fmt(t['macro_roc_auc'])} | {fmt(t['macro_ap'])} |"]
        if ref:
            lines.append(f"| random forest (reference) | {fmt(ref['hit_rate'])} | {fmt(ref['macro_f1'])} | {fmt(ref['macro_roc_auc'])} | {fmt(ref['macro_ap'])} |")
        lines += ['', f"Cluster purity {fmt(q['purity'])}, NMI {fmt(q['nmi_primary'])}, ARI {fmt(q['ari_primary'])} (against the rarest tag); "
                  f"{w['test_outliers']} test objects beyond their cluster's 99% radius.", '',
                  '| class | objects | base rate | precision | recall | F1 | ROC AUC | AP | RF F1 |', '|---|---|---|---|---|---|---|---|---|']
        for c in w['classes']:
            a = t['per_class'][c]
            rr = ref['per_class'][c] if ref else {}
            lines.append(f"| {short(c)} | {a['support']} | {fmt(a['base_rate'])} | {fmt(a['precision'])} | {fmt(a['recall'])} | "
                         f"{fmt(a['f1'])} | {fmt(a['roc_auc'])} | {fmt(a['average_precision'])} | {fmt(rr.get('f1'))} |")
        lines += ['', 'Validation grid (best per bins/embedding):', '', '| bins | embedding | best K | macro F1 | hit |', '|---|---|---|---|---|']
        best = {}
        for g in w['grid']:
            key = (g['bins'], g['embedding'])
            if key not in best or g['validation'][w['selection_metric']] > best[key]['validation'][w['selection_metric']]:
                best[key] = g
        for (bins, emb), g in sorted(best.items()):
            lines.append(f"| {bins} | {emb} | {g['k']} | {fmt(g['validation']['macro_f1'])} | {fmt(g['validation']['hit_rate'])} |")
        lines.append('')
    lines += ['## Data', '', '| class | table objects | sampled | exported | capped | short | tagged alerts | missing | from tag rows |',
              '|---|---|---|---|---|---|---|---|---|']
    for c, v in s['classes'].items():
        lines.append(f"| {short(c)} | {v.get('table_objects', 0)} | {v.get('sampled_objects', 0)} | {v.get('exported', 0)} | "
                     f"{v.get('capped', 0)} | {v.get('short', 0)} | {v.get('tagged_alerts', 0)} | {v.get('missing_tagged', 0)} | {v.get('tag_row_sources', 0)} |")
    lines += ['', f"Excluded objects: {s.get('excluded_objects')}; dropped invalid observations: {s.get('invalid_sources')}; "
              f"observations read from tag rows: {s.get('tag_row_sources')}.",
              'Software: ' + ', '.join(f'{k} {v}' for k, v in r['software'].items()) + '.', '',
              'Tags are Fink filter memberships of single alerts, not independent physical truth; the sample is capped per class, so scores describe this design on unseen objects, not live-stream performance.',
              'Files: metrics.json, test_per_class_<window>.csv, predictions_test_<window>.csv, grid_<window>.csv, tag_overlap.csv, model_<window>.json and PNG figures.', '']
    return '\n'.join(lines)


def predict(args):
    model = strict_json(Path(args.model).read_text(encoding='utf-8'), args.model)
    if model.get('format') != MODEL_FORMAT:
        raise EvalError('unsupported model format')
    if tl.model_digest(model) != model.get('model_id'):
        raise EvalError('model_id does not match the model content')
    if model.get('window') != 'full':
        print(f"note: model window {model.get('window')!r}: input curves must already be cut at the same point "
              "(e.g. at the first tag); predict does not cut them", file=sys.stderr)
    mean, scale = np.array(model['feature_mean']), np.array(model['feature_scale'])
    w1, b1 = np.array(model['encoder_weights']), np.array(model['encoder_bias'])
    centers, radii, profiles = np.array(model['centers']), np.array(model['radii']), np.array(model['cluster_profiles'])
    out = Path(args.output)
    fd, tmp = tempfile.mkstemp(prefix='.predict-', dir=out.absolute().parent)
    total = 0
    try:
        with os.fdopen(fd, 'w', encoding='utf-8') as fh:
            for n, line in enumerate(Path(args.input).open(encoding='utf-8'), 1):
                if not line.strip():
                    continue
                rec = strict_json(line, f'{args.input}:{n}')
                try:
                    f = tl.encode_features(rec, model['bins'])
                except ValueError as exc:
                    raise EvalError(f'{args.input}:{n}: {exc}') from exc
                e = np.tanh(((f - mean) / scale) @ w1 + b1)
                d = np.linalg.norm(centers - e, axis=1)
                j = int(d.argmin())
                fh.write(json.dumps({'object_id': rec['object_id'], 'cluster': j, 'distance': float(d[j]),
                                     'outlier': bool(d[j] > radii[j]), 'predicted': model['classes'][int(profiles[j].argmax())],
                                     'scores': {c: float(v) for c, v in zip(model['classes'], profiles[j])},
                                     'model_id': model['model_id'], 'window': model['window']}) + '\n')
                total += 1
        if not total:
            raise EvalError('input has no light curves')
        os.replace(tmp, out)
    finally:
        if os.path.exists(tmp):
            os.unlink(tmp)
    print(f'Classified {total} light curves -> {out}')


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest='command', required=True)
    t = sub.add_parser('train')
    t.add_argument('--config', required=True)
    t.add_argument('--input', required=True, help='export_tag_lightcurves.groovy output directory')
    t.add_argument('--output', required=True, help='fresh output directory')
    q = sub.add_parser('predict')
    q.add_argument('--model', required=True)
    q.add_argument('--input', required=True, help='curves.jsonl (train_lightcurves.py contract)')
    q.add_argument('--output', required=True)
    args = p.parse_args(argv)
    try:
        (train if args.command == 'train' else predict)(args)
    except (EvalError, OSError) as exc:
        print(f'error: {exc}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())

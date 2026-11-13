#!/usr/bin/env python3
"""Train and evaluate the LSST alert PCA+K-means classifier against Fink tag classes.

Input is a directory written by export_tag_alerts.groovy (alerts.jsonl,
labels.jsonl, summary.json) and the same JSON config (tag_classes.json).
Labels are rubin.tag_* memberships; an alert may carry several.

Procedure (all choices come from the config, so re-running with more data or
other classes needs no code change):
  * objects are split into train / validation / test (no object in two splits);
  * features: configured measured columns present in >= min_presence of the
    training alerts, optional asinh compression, optional band one-hot;
    median imputation, standardisation and PCA fitted on train only;
  * K-means for every K of the grid; each cluster gets the class profile
    (fraction of its training alerts carrying each class); an alert's class
    scores are the profile of its nearest cluster;
  * K is chosen on validation by the selection metric, then train+validation
    are refitted with that K and evaluated once on the untouched test split;
  * references: class priors (no features) and, optionally, a supervised
    random forest on the same features (how much the features allow).

Metrics, top-1 predicted class counted correct when it is one of the alert's
tags: hit rate, per-class precision/recall/F1, macro F1; ranking per class:
ROC AUC and average precision; cluster purity and NMI; the same at object
level (mean of alert scores). Outputs go to a fresh directory: metrics.json,
report.md, CSV tables, PNG figures and model.json.

  python3 evaluate_tags.py train --config tag_classes.json \
      --input ../run/lsst-tag-eval/export --output ../run/lsst-tag-eval/eval
  python3 evaluate_tags.py predict --model ../run/lsst-tag-eval/eval/model.json \
      --input alerts.jsonl --output predictions.jsonl
"""
import argparse
import csv
import hashlib
import json
import math
import os
import shutil
import sys
import tempfile
from pathlib import Path

import numpy as np

MODEL_FORMAT = 'lomikel-lsst-pca-kmeans-tags-v1'
CONFIG_FORMAT = 'lomikel-lsst-tag-eval-config-v1'
BANDS = ['u', 'g', 'r', 'i', 'z', 'y']
# Measured alert columns the evaluator may use (subset exported by export_tag_alerts.groovy).
ALLOWED = {'psfFlux', 'psfFluxErr', 'psfChi2', 'psfLnL', 'psfNdata', 'apFlux', 'apFluxErr',
           'scienceFlux', 'scienceFluxErr', 'templateFlux', 'templateFluxErr', 'snr',
           'extendedness', 'reliability', 'ixx', 'ixy', 'iyy', 'ixxPSF', 'ixyPSF', 'iyyPSF',
           'bboxSize', 'dipoleMeanFlux', 'dipoleMeanFluxErr', 'dipoleFluxDiff',
           'dipoleFluxDiffErr', 'dipoleLength', 'dipoleAngle', 'dipoleChi2', 'dipoleNdata',
           'trailFlux', 'trailFluxErr', 'trailLength', 'trailLengthErr', 'trailAngle',
           'trailAngleErr', 'trailChi2', 'trailNdata'}


class EvalError(Exception):
    pass


# ---------------------------------------------------------------- input --
def strict_json(text, where):
    def pairs(items):
        keys = [k for k, _ in items]
        if len(keys) != len(set(keys)):
            raise EvalError(f'duplicate JSON key in {where}')
        return dict(items)
    try:
        return json.loads(text, object_pairs_hook=pairs,
                          parse_constant=lambda c: (_ for _ in ()).throw(EvalError(f'{c} in {where}')))
    except json.JSONDecodeError as exc:
        raise EvalError(f'invalid JSON in {where}: {exc}') from exc


def load_config(path):
    cfg = strict_json(Path(path).read_text(encoding='utf-8'), path)
    if not isinstance(cfg, dict) or cfg.get('format') != CONFIG_FORMAT:
        raise EvalError('unsupported config format')
    classes = cfg.get('classes')
    if (not isinstance(classes, list) or len(classes) < 2 or len(set(classes)) != len(classes)
            or not all(isinstance(c, str) and c.startswith('rubin.tag_') for c in classes)):
        raise EvalError('config needs at least two unique rubin.tag_* classes')
    feats = cfg.get('features')
    if not isinstance(feats, list) or not feats or len(set(feats)) != len(feats) or not set(feats) <= ALLOWED:
        raise EvalError(f'features must be unique names from {sorted(ALLOWED)}')
    if cfg.get('transform', 'none') not in ('none', 'asinh'):
        raise EvalError('transform must be none or asinh')
    mp = cfg.get('min_presence', 0.8)
    if not isinstance(mp, (int, float)) or not 0 < mp <= 1:
        raise EvalError('min_presence must be in (0, 1]')
    model = cfg.get('model', {})
    ks = model.get('clusters')
    if not isinstance(ks, list) or not ks or not all(isinstance(k, int) and k >= 2 for k in ks):
        raise EvalError('model.clusters must be a list of integers >= 2')
    comp = model.get('components', 0.95)
    if not ((isinstance(comp, int) and comp >= 1) or (isinstance(comp, float) and 0 < comp < 1)):
        raise EvalError('model.components must be an integer >= 1 or a fraction in (0, 1)')
    ev = cfg.get('evaluation', {})
    vf, tf = ev.get('validation_fraction', 0.2), ev.get('test_fraction', 0.2)
    if not (0 < vf < 1 and 0 < tf < 1 and vf + tf < 0.9):
        raise EvalError('validation/test fractions must be positive and leave >= 10% for training')
    if model.get('scores', 'fraction') not in ('fraction', 'lift'):
        raise EvalError('model.scores must be fraction or lift')
    mo = ev.get('min_objects_per_split', 3)
    if not isinstance(mo, int) or mo < 1:
        raise EvalError('evaluation.min_objects_per_split must be a positive integer')
    if ev.get('selection_metric', 'macro_f1') not in ('macro_f1', 'hit_rate', 'macro_ap'):
        raise EvalError('selection_metric must be macro_f1, hit_rate or macro_ap')
    return cfg


def load_export(directory, classes):
    directory = Path(directory)
    alerts = {}
    for n, line in enumerate((directory / 'alerts.jsonl').open(encoding='utf-8'), 1):
        rec = strict_json(line, f'alerts.jsonl:{n}')
        key = rec.get('row_key')
        if rec.get('kind') != 'dia' or not isinstance(key, str) or not isinstance(rec.get('cells'), dict) or key in alerts:
            raise EvalError(f'invalid or duplicate alert at alerts.jsonl:{n}')
        alerts[key] = rec['cells']
    labels = {}
    for n, line in enumerate((directory / 'labels.jsonl').open(encoding='utf-8'), 1):
        rec = strict_json(line, f'labels.jsonl:{n}')
        key = rec.get('row_key')
        if key not in alerts or key in labels or not isinstance(rec.get('classes'), list):
            raise EvalError(f'label without alert or duplicate at labels.jsonl:{n}')
        labels[key] = rec
    if set(labels) != set(alerts):
        raise EvalError('alerts.jsonl and labels.jsonl do not describe the same alerts')
    keys = sorted(alerts)
    Y = np.zeros((len(keys), len(classes)), dtype=bool)
    index = {c: i for i, c in enumerate(classes)}
    for r, key in enumerate(keys):
        for c in labels[key]['classes']:
            if c in index:
                Y[r, index[c]] = True
    keep = Y.any(axis=1)
    keys = [k for k, ok in zip(keys, keep) if ok]
    return keys, [alerts[k] for k in keys], [labels[k] for k in keys], Y[keep]


# ------------------------------------------------------------- features --
def number(value):
    try:
        x = float(value)
    except (TypeError, ValueError):
        return math.nan
    return x if math.isfinite(x) else math.nan


def raw_matrix(cells, features):
    return np.array([[number(c.get(f'r:{f}')) for f in features] for c in cells], dtype=float)


def band_matrix(cells):
    return np.array([[1.0 if c.get('r:band') == b else 0.0 for b in BANDS] for c in cells])


class Preprocessor:
    """Presence selection, asinh, median imputation, scaling (fit on train only)."""

    def fit(self, cells, cfg):
        features = cfg['features']
        X = raw_matrix(cells, features)
        presence = np.mean(np.isfinite(X), axis=0)
        self.features = [f for f, p in zip(features, presence) if p >= cfg.get('min_presence', 0.8)]
        self.presence = {f: float(p) for f, p in zip(features, presence)}
        if not self.features:
            raise EvalError('no configured feature reaches min_presence in the training alerts')
        X = X[:, [features.index(f) for f in self.features]]
        self.transform = cfg.get('transform', 'none')
        if self.transform == 'asinh':
            # Per-feature softening scale: median absolute value (1 if zero).
            med = np.nanmedian(np.abs(X), axis=0)
            self.soft = np.where(np.isfinite(med) & (med > 0), med, 1.0)
        else:
            self.soft = np.ones(len(self.features))
        X = self._compress(X)
        self.impute = np.nanmedian(X, axis=0)
        self.impute = np.where(np.isfinite(self.impute), self.impute, 0.0)
        self.band = bool(cfg.get('band_onehot', False))
        Z = self._assemble(X, cells)
        self.mean = Z.mean(axis=0)
        sd = Z.std(axis=0)
        self.scale = np.where(sd > 0, sd, 1.0)
        return self

    def _compress(self, X):
        return np.arcsinh(X / self.soft) if self.transform == 'asinh' else X

    def _assemble(self, X, cells):
        X = np.where(np.isfinite(X), X, self.impute)
        return np.hstack([X, band_matrix(cells)]) if self.band else X

    def apply(self, cells):
        X = self._compress(raw_matrix(cells, self.features))
        return (self._assemble(X, cells) - self.mean) / self.scale

    def columns(self):
        return self.features + ([f'band={b}' for b in BANDS] if self.band else [])

    def to_json(self):
        return {'features': self.features, 'transform': self.transform, 'soft': self.soft.tolist(),
                'impute': self.impute.tolist(), 'band_onehot': self.band, 'bands': BANDS,
                'mean': self.mean.tolist(), 'scale': self.scale.tolist()}

    @classmethod
    def from_json(cls, data):
        p = cls()
        p.features, p.transform, p.band = data['features'], data['transform'], data['band_onehot']
        for name in ('soft', 'impute', 'mean', 'scale'):
            setattr(p, name, np.array(data[name], dtype=float))
        if data.get('bands', BANDS) != BANDS:
            raise EvalError('model band encoding differs')
        return p


# ---------------------------------------------------------------- split --
def split_objects(objects, strata, fractions, seed):
    """Deterministic object split stratified by each object's rarest tag.

    Objects are ordered by sha256('split:<seed>:<object>') inside each stratum
    (a salt distinct from the exporter's sampling order, which would otherwise
    correlate with the split). Strata with at least three objects get at least
    one validation and one test object; coverage of each class is then checked
    by check_coverage().
    """
    stratum = {}
    for o, s in zip(objects, strata):
        stratum[o] = min(stratum.get(o, s), s)
    groups = {}
    for o, s in stratum.items():
        groups.setdefault(s, []).append(o)
    test, val = set(), set()
    for members in groups.values():
        members.sort(key=lambda o: hashlib.sha256(f'split:{seed}:{o}'.encode()).hexdigest())
        n = len(members)
        n_test = max(1, int(round(n * fractions[1]))) if n >= 3 else 0
        n_val = max(1, int(round(n * fractions[0]))) if n >= 3 else 0
        test.update(members[:n_test])
        val.update(members[n_test:n_test + n_val])
    if not test or not val or len(stratum) - len(test) - len(val) < 1:
        raise EvalError('too few objects to split')
    return np.array(['test' if o in test else 'val' if o in val else 'train' for o in objects])


def check_coverage(objects, split, Y, classes, minimum):
    """Objects per class and split; fail when a class cannot be evaluated."""
    coverage, short_ = {}, []
    for i, c in enumerate(classes):
        coverage[c] = {s: len({o for o, x, y in zip(objects, split, Y[:, i]) if x == s and y})
                       for s in ('train', 'val', 'test')}
        if min(coverage[c].values()) < minimum:
            short_.append(f"{c} {coverage[c]}")
    if short_:
        raise EvalError(f'classes with fewer than {minimum} objects in some split (remove them from the config, '
                        'export more data or lower evaluation.min_objects_per_split): ' + '; '.join(short_))
    return coverage


# ---------------------------------------------------------------- model --
class ClusterModel:
    def fit(self, Z, Y, k, components, seed, scores='fraction'):
        from sklearn.cluster import KMeans
        from sklearn.decomposition import PCA
        if Z.shape[0] <= k:
            raise EvalError(f'not enough training alerts for K={k}')
        self.pca = PCA(n_components=components, svd_solver='full', random_state=seed).fit(Z)
        P = self.pca.transform(Z)
        self.km = KMeans(n_clusters=k, n_init=10, random_state=seed).fit(P)
        labels = self.km.labels_
        self.counts = np.bincount(labels, minlength=k)
        prior = Y.mean(axis=0)
        # Class profile per cluster with a light pull to the prior (empty-safe).
        self.profile = np.array([(Y[labels == j].sum(axis=0) + prior) / (self.counts[j] + 1) for j in range(k)])
        # 'lift' divides by the class prior so rare classes can win their clusters.
        self.weights = 1.0 / np.maximum(prior, 1e-12) if scores == 'lift' else np.ones(len(prior))
        d = np.linalg.norm(P - self.km.cluster_centers_[labels], axis=1)
        self.radii = np.array([np.quantile(d[labels == j], 0.95) if self.counts[j] else 0.0 for j in range(k)])
        return self

    def assign(self, Z):
        P = self.pca.transform(Z)
        dist = np.linalg.norm(P[:, None, :] - self.km.cluster_centers_[None, :, :], axis=2)
        j = dist.argmin(axis=1)
        return j, dist[np.arange(len(j)), j], P

    def scores(self, Z):
        return self.profile[self.assign(Z)[0]] * self.weights


# -------------------------------------------------------------- metrics --
def metrics(S, Y, classes):
    """Top-1 (correct if the predicted class is one of the alert's tags) and ranking metrics."""
    from sklearn.metrics import average_precision_score, roc_auc_score
    pred = S.argmax(axis=1)
    hit = Y[np.arange(len(pred)), pred]
    per = {}
    for i, c in enumerate(classes):
        tp = int(np.sum((pred == i) & Y[:, i]))
        fp = int(np.sum((pred == i) & ~Y[:, i]))
        fn = int(np.sum((pred != i) & Y[:, i]))
        prec = tp / (tp + fp) if tp + fp else 0.0
        rec = tp / (tp + fn) if tp + fn else 0.0
        f1 = 2 * prec * rec / (prec + rec) if prec + rec else 0.0
        positives = int(Y[:, i].sum())
        both = 0 < positives < len(Y)
        auc = float(roc_auc_score(Y[:, i], S[:, i])) if both and np.ptp(S[:, i]) > 0 else (0.5 if both else None)
        ap = float(average_precision_score(Y[:, i], S[:, i])) if positives else None
        per[c] = {'support': positives, 'predicted': int(np.sum(pred == i)), 'precision': prec,
                  'recall': rec, 'f1': f1, 'roc_auc': auc, 'average_precision': ap,
                  'base_rate': positives / len(Y) if len(Y) else 0.0}
    present = [c for c in classes if per[c]['support']]
    mean = lambda key: float(np.mean([per[c][key] for c in present if per[c][key] is not None])) if present else 0.0
    return {'n': int(len(Y)), 'hit_rate': float(hit.mean()) if len(hit) else 0.0,
            'macro_f1': mean('f1'), 'macro_roc_auc': mean('roc_auc'), 'macro_ap': mean('average_precision'),
            'macro_base_rate': mean('base_rate'), 'per_class': per}


def object_level(S, Y, objects):
    groups = {}
    for i, o in enumerate(objects):
        groups.setdefault(o, []).append(i)
    order = sorted(groups)
    So = np.array([S[groups[o]].mean(axis=0) for o in order])
    Yo = np.array([Y[groups[o]].any(axis=0) for o in order])
    return So, Yo


def cluster_quality(clusters, Y, primary):
    from sklearn.metrics import adjusted_rand_score, normalized_mutual_info_score
    purity = 0
    for j in np.unique(clusters):
        purity += Y[clusters == j].sum(axis=0).max()
    return {'purity': float(purity / len(Y)), 'nmi_primary': float(normalized_mutual_info_score(primary, clusters)),
            'ari_primary': float(adjusted_rand_score(primary, clusters))}


def primary_labels(Y, class_sizes):
    """Rarest tag of each alert (smallest table) as a single label for NMI/ARI."""
    order = np.argsort(class_sizes)
    out = np.empty(len(Y), dtype=int)
    for r in range(len(Y)):
        out[r] = next(i for i in order if Y[r, i])
    return out


# --------------------------------------------------------------- output --
def write_csv(path, header, rows):
    with path.open('w', newline='', encoding='utf-8') as fh:
        w = csv.writer(fh)
        w.writerow(header)
        w.writerows(rows)


def fmt(x, digits=3):
    return '–' if x is None else f'{x:.{digits}f}'


def short(c):
    return c.replace('rubin.tag_', '')


def figures(out, classes, cooc, contingency, grid, P2, Yp, pred_test, Y_test):
    try:
        import matplotlib
        matplotlib.use('Agg')
        import matplotlib.pyplot as plt
    except ImportError:
        return ['matplotlib not installed: no figures']
    names = [short(c) for c in classes]
    fig, ax = plt.subplots(figsize=(7, 6))
    im = ax.imshow(cooc, cmap='viridis', vmin=0, vmax=1)
    ax.set_xticks(range(len(names)), names, rotation=60, ha='right', fontsize=8)
    ax.set_yticks(range(len(names)), names, fontsize=8)
    for i in range(len(names)):
        for j in range(len(names)):
            ax.text(j, i, f'{cooc[i, j]:.2f}', ha='center', va='center', fontsize=7, color='w' if cooc[i, j] < 0.6 else 'k')
    ax.set_title('Share of row-class alerts also carrying column class')
    fig.colorbar(im, ax=ax)
    fig.tight_layout()
    fig.savefig(out / 'tag_overlap.png', dpi=120)
    plt.close(fig)

    fig, ax = plt.subplots(figsize=(max(6, 0.35 * contingency.shape[0]), 5))
    frac = contingency / np.maximum(contingency.sum(axis=1, keepdims=True), 1)
    im = ax.imshow(frac.T, aspect='auto', cmap='magma', vmin=0, vmax=1)
    ax.set_yticks(range(len(names)), names, fontsize=8)
    ax.set_xlabel('cluster (train+validation fit)')
    ax.set_title('Class share per cluster (test alerts)')
    fig.colorbar(im, ax=ax)
    fig.tight_layout()
    fig.savefig(out / 'cluster_class_share.png', dpi=120)
    plt.close(fig)

    fig, ax = plt.subplots(figsize=(6, 4))
    ks = [g['k'] for g in grid]
    for key in ('macro_f1', 'hit_rate', 'macro_ap', 'macro_roc_auc'):
        ax.plot(ks, [g['validation'][key] for g in grid], marker='o', label=key)
    ax.set_xlabel('K (clusters)')
    ax.set_ylabel('validation')
    ax.legend(fontsize=8)
    ax.set_title('Model selection')
    fig.tight_layout()
    fig.savefig(out / 'k_selection.png', dpi=120)
    plt.close(fig)

    fig, ax = plt.subplots(figsize=(7, 6))
    cmap = plt.get_cmap('tab10')
    for i, n in enumerate(names):
        m = Yp == i
        ax.scatter(P2[m, 0], P2[m, 1], s=4, alpha=0.5, color=cmap(i % 10), label=n)
    ax.set_xlabel('PC1')
    ax.set_ylabel('PC2')
    ax.legend(fontsize=7, markerscale=3)
    ax.set_title('Test alerts in PCA space (colour = rarest tag)')
    fig.tight_layout()
    fig.savefig(out / 'pca_test.png', dpi=120)
    plt.close(fig)

    conf = np.zeros((len(classes), len(classes)))
    for p, y in zip(pred_test, Y_test):
        conf[y, p] += 1
    conf = conf / np.maximum(conf.sum(axis=1, keepdims=True), 1)
    fig, ax = plt.subplots(figsize=(7, 6))
    im = ax.imshow(conf, cmap='Blues', vmin=0, vmax=1)
    ax.set_xticks(range(len(names)), names, rotation=60, ha='right', fontsize=8)
    ax.set_yticks(range(len(names)), names, fontsize=8)
    for i in range(len(names)):
        for j in range(len(names)):
            ax.text(j, i, f'{conf[i, j]:.2f}', ha='center', va='center', fontsize=7, color='k' if conf[i, j] < 0.6 else 'w')
    ax.set_xlabel('predicted (cluster majority)')
    ax.set_ylabel('true (rarest tag)')
    ax.set_title('Test confusion, row-normalised')
    fig.colorbar(im, ax=ax)
    fig.tight_layout()
    fig.savefig(out / 'confusion_test.png', dpi=120)
    plt.close(fig)
    return []


def train(args):
    from sklearn.ensemble import RandomForestClassifier
    cfg = load_config(args.config)
    classes = cfg['classes']
    keys, cells, labels, Y = load_export(args.input, classes)
    summary = strict_json((Path(args.input) / 'summary.json').read_text(encoding='utf-8'), 'summary.json')
    scanned = summary.get('classes', {})
    unscanned = [c for c in classes if c not in scanned]
    if unscanned:
        raise EvalError('classes not scanned by this export (re-export with this config): ' + ', '.join(unscanned))
    failed = [c for c in classes if scanned[c].get('status') not in ('ok', 'empty table')]
    if failed:
        raise EvalError('classes whose table could not be read: ' + ', '.join(failed))
    absent = [c for c in classes if not Y[:, classes.index(c)].any()]
    unexpected = [c for c in absent if scanned[c].get('status') != 'empty table']
    if unexpected:
        raise EvalError('scanned classes without exported alerts: ' + ', '.join(unexpected))
    if absent:
        print('warning: no exported alert for ' + ', '.join(absent), file=sys.stderr)
    active = [c for c in classes if c not in absent]
    Y = Y[:, [classes.index(c) for c in active]]
    classes = active
    if len(classes) < 2:
        raise EvalError('fewer than two classes have alerts')
    objects = [l['object_id'] for l in labels]
    ev = cfg.get('evaluation', {})
    sizes = [summary['classes'].get(c, {}).get('table_alerts', 0) for c in classes]
    rank = {i: r for r, i in enumerate(np.argsort(sizes, kind='stable'))}
    strata = [rank[i] for i in primary_labels(Y, sizes)]
    split = split_objects(objects, strata, (ev.get('validation_fraction', 0.2), ev.get('test_fraction', 0.2)),
                          ev.get('split_seed', 42))
    coverage = check_coverage(objects, split, Y, classes, ev.get('min_objects_per_split', 3))
    tr, va, te = (split == 'train'), (split == 'val'), (split == 'test')
    cells_of = lambda mask: [c for c, m in zip(cells, mask) if m]
    m = cfg['model']
    seed, comp, metric = m.get('seed', 42), m.get('components', 0.95), ev.get('selection_metric', 'macro_f1')
    score_mode = m.get('scores', 'fraction')

    pre = Preprocessor().fit(cells_of(tr), cfg)
    Ztr, Zva = pre.apply(cells_of(tr)), pre.apply(cells_of(va))
    grid = []
    for k in sorted(set(m['clusters'])):
        if k >= tr.sum():
            continue
        model = ClusterModel().fit(Ztr, Y[tr], k, comp, seed, score_mode)
        res = metrics(model.scores(Zva), Y[va], classes)
        res.pop('per_class')
        grid.append({'k': k, 'pca_components': int(model.pca.n_components_), 'validation': res})
        print(f'K={k}: validation {metric}={res[metric]:.3f} hit={res["hit_rate"]:.3f}', file=sys.stderr)
    if not grid:
        raise EvalError('no K of the grid fits the training data')
    best = max(grid, key=lambda g: (g['validation'][metric], -g['k']))['k']

    fit = tr | va
    pre = Preprocessor().fit(cells_of(fit), cfg)
    Zfit, Zte = pre.apply(cells_of(fit)), pre.apply(cells_of(te))
    model = ClusterModel().fit(Zfit, Y[fit], best, comp, seed, score_mode)
    S_te = model.scores(Zte)
    clusters_te, _, P_te = model.assign(Zte)
    test = metrics(S_te, Y[te], classes)
    obj_te = [o for o, t in zip(objects, te) if t]
    So, Yo = object_level(S_te, Y[te], obj_te)
    test_obj = metrics(So, Yo, classes)
    prior = Y[fit].mean(axis=0)
    base = metrics(np.tile(prior, (int(te.sum()), 1)) + 1e-9 * np.arange(len(classes))[::-1], Y[te], classes)
    prim_te = primary_labels(Y[te], sizes)
    quality = cluster_quality(clusters_te, Y[te], prim_te)

    reference, importances = None, []
    if ev.get('supervised_reference', True):
        rf = RandomForestClassifier(n_estimators=300, min_samples_leaf=2, class_weight='balanced',
                                    n_jobs=-1, random_state=seed).fit(Zfit, Y[fit])
        proba = rf.predict_proba(Zte)
        S_rf = np.column_stack([p[:, 1] if p.shape[1] == 2 else np.zeros(len(Zte)) for p in proba])
        reference = metrics(S_rf, Y[te], classes)
        reference['object_level'] = metrics(*object_level(S_rf, Y[te], obj_te), classes)
        importances = sorted(zip(pre.columns(), rf.feature_importances_), key=lambda x: -x[1])

    out = Path(args.output)
    if out.exists():
        raise EvalError(f'output {out} exists; choose a fresh directory')
    out.parent.mkdir(parents=True, exist_ok=True)
    stage = Path(tempfile.mkdtemp(prefix='.lsst-tag-eval-', dir=out.parent))
    try:
        cooc = np.array([[Y[Y[:, i], j].mean() if Y[:, i].any() else 0 for j in range(len(classes))] for i in range(len(classes))])
        contingency = np.array([[np.sum((clusters_te == j) & Y[te][:, i]) for i in range(len(classes))] for j in range(best)])
        notes = figures(stage, classes, cooc, contingency, grid, P_te[:, :2] if P_te.shape[1] > 1 else np.hstack([P_te, P_te]),
                        prim_te, S_te.argmax(axis=1), prim_te)
        write_csv(stage / 'tag_overlap.csv', ['class'] + classes, [[c] + list(map(float, r)) for c, r in zip(classes, cooc)])
        write_csv(stage / 'cluster_profiles.csv', ['cluster', 'train_alerts', 'radius95'] + classes,
                  [[j, int(model.counts[j]), float(model.radii[j])] + list(map(float, model.profile[j])) for j in range(best)])
        write_csv(stage / 'test_contingency.csv', ['cluster'] + classes, [[j] + list(map(int, r)) for j, r in enumerate(contingency)])
        rows = []
        for c in classes:
            a, o, b = test['per_class'][c], test_obj['per_class'][c], base['per_class'][c]
            r = reference['per_class'][c] if reference else {}
            rows.append([c, a['support'], a['base_rate'], a['precision'], a['recall'], a['f1'], a['roc_auc'],
                         a['average_precision'], o['support'], o['f1'], o['roc_auc'], b['f1'],
                         r.get('f1'), r.get('roc_auc'), r.get('average_precision')])
        write_csv(stage / 'test_per_class.csv',
                  ['class', 'alerts', 'base_rate', 'precision', 'recall', 'f1', 'roc_auc', 'average_precision',
                   'objects', 'object_f1', 'object_roc_auc', 'prior_f1', 'rf_f1', 'rf_roc_auc', 'rf_average_precision'], rows)
        model_json = {'format': MODEL_FORMAT, 'kind': 'dia', 'classes': classes, 'preprocessing': pre.to_json(),
                      'pca': {'mean': model.pca.mean_.tolist(), 'components': model.pca.components_.tolist(),
                              'explained_variance_ratio': model.pca.explained_variance_ratio_.tolist()},
                      'centers': model.km.cluster_centers_.tolist(), 'radii': model.radii.tolist(),
                      'cluster_counts': model.counts.tolist(), 'cluster_profiles': model.profile.tolist(),
                      'score_weights': model.weights.tolist(), 'scores': score_mode,
                      'provenance': {'alert_table': summary.get('alert_table'), 'alert_schemas': summary.get('alert_schemas'),
                                     'export_created': summary.get('created'), 'config_sha256': summary.get('config_sha256'),
                                     'fitted_on': 'train+validation objects', 'k_selected_by': metric}}
        (stage / 'model.json').write_text(json.dumps(model_json) + '\n', encoding='utf-8')
        result = {'format': 'lomikel-lsst-tag-eval-v1', 'classes': classes, 'absent_classes': absent,
                  'alerts': int(len(Y)), 'objects': len(set(objects)),
                  'split': {s: {'alerts': int(np.sum(split == s)), 'objects': len({o for o, x in zip(objects, split) if x == s})}
                            for s in ('train', 'val', 'test')},
                  'objects_per_class_and_split': coverage, 'software': software(),
                  'features_used': pre.columns(), 'feature_presence': pre.presence,
                  'pca_components': int(model.pca.n_components_),
                  'pca_explained_variance': float(model.pca.explained_variance_ratio_.sum()),
                  'selected_k': best, 'selection_metric': metric, 'grid': grid,
                  'test': test, 'test_object_level': test_obj, 'test_prior_baseline': base,
                  'test_cluster_quality': quality, 'supervised_reference': reference,
                  'tag_overlap': {c: {d: float(cooc[i, j]) for j, d in enumerate(classes)} for i, c in enumerate(classes)},
                  'export_summary': summary, 'notes': notes}
        if reference:
            result['rf_feature_importance'] = [[n, float(v)] for n, v in importances]
        (stage / 'metrics.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
        (stage / 'report.md').write_text(report(result, cfg), encoding='utf-8')
        os.replace(stage, out)
    finally:
        if stage.exists():
            shutil.rmtree(stage)
    print(f'K={best} PCA={result["pca_components"]} test hit={test["hit_rate"]:.3f} macro_f1={test["macro_f1"]:.3f} '
          f'macro_auc={test["macro_roc_auc"]:.3f} (prior hit={base["hit_rate"]:.3f}'
          + (f', RF macro_f1={reference["macro_f1"]:.3f} macro_auc={reference["macro_roc_auc"]:.3f}' if reference else '')
          + f') -> {out}')


def software():
    import platform
    import sklearn
    versions = {'python': platform.python_version(), 'numpy': np.__version__, 'scikit-learn': sklearn.__version__}
    try:
        import matplotlib
        versions['matplotlib'] = matplotlib.__version__
    except ImportError:
        pass
    return versions


def report(r, cfg):
    t, o, b, ref, q = r['test'], r['test_object_level'], r['test_prior_baseline'], r['supervised_reference'], r['test_cluster_quality']
    lines = ['# LSST PCA+K-means tag evaluation', '',
             f"Alerts {r['alerts']} of {r['objects']} objects; split by object "
             + ', '.join(f"{s} {v['alerts']} alerts/{v['objects']} objects" for s, v in r['split'].items()) + '.',
             'Objects per class (train/val/test): ' + '; '.join(
                 f"{short(c)} {v['train']}/{v['val']}/{v['test']}" for c, v in r['objects_per_class_and_split'].items()) + '.',
             f"Features ({len(r['features_used'])}): {', '.join(r['features_used'])}; transform {cfg.get('transform', 'none')}.",
             f"PCA {r['pca_components']} components ({r['pca_explained_variance']:.1%} variance); K={r['selected_k']} chosen by validation {r['selection_metric']}; cluster scores {cfg['model'].get('scores', 'fraction')}.",
             '', '## Test summary (alert level)', '',
             '| model | hit rate | macro F1 | macro ROC AUC | macro AP |', '|---|---|---|---|---|',
             f"| class prior (no features) | {fmt(b['hit_rate'])} | {fmt(b['macro_f1'])} | {fmt(b['macro_roc_auc'])} | {fmt(b['macro_ap'])} |",
             f"| PCA+K-means | {fmt(t['hit_rate'])} | {fmt(t['macro_f1'])} | {fmt(t['macro_roc_auc'])} | {fmt(t['macro_ap'])} |"]
    if ref:
        lines.append(f"| random forest (reference) | {fmt(ref['hit_rate'])} | {fmt(ref['macro_f1'])} | {fmt(ref['macro_roc_auc'])} | {fmt(ref['macro_ap'])} |")
    lines += ['', f"Object level (mean alert scores): hit {fmt(o['hit_rate'])}, macro F1 {fmt(o['macro_f1'])}, macro ROC AUC {fmt(o['macro_roc_auc'])}.",
              f"Cluster purity {fmt(q['purity'])}, NMI {fmt(q['nmi_primary'])}, ARI {fmt(q['ari_primary'])} (against the rarest tag).",
              '', '## Per class (test alerts)', '',
              '| class | alerts | base rate | precision | recall | F1 | ROC AUC | AP | RF F1 | RF AUC |', '|---|---|---|---|---|---|---|---|---|---|']
    for c in r['classes']:
        a = t['per_class'][c]
        rr = ref['per_class'][c] if ref else {}
        lines.append(f"| {short(c)} | {a['support']} | {fmt(a['base_rate'])} | {fmt(a['precision'])} | {fmt(a['recall'])} | {fmt(a['f1'])} | "
                     f"{fmt(a['roc_auc'])} | {fmt(a['average_precision'])} | {fmt(rr.get('f1'))} | {fmt(rr.get('roc_auc'))} |")
    lines += ['', '## Model selection (validation)', '', '| K | PCA | hit | macro F1 | macro AUC | macro AP |', '|---|---|---|---|---|---|']
    for g in r['grid']:
        v = g['validation']
        lines.append(f"| {g['k']} | {g['pca_components']} | {fmt(v['hit_rate'])} | {fmt(v['macro_f1'])} | {fmt(v['macro_roc_auc'])} | {fmt(v['macro_ap'])} |")
    lines += ['', '## Data', '', '| class | table alerts | table objects | sampled alerts | missing alert rows | MJD range |',
              '|---|---|---|---|---|---|']
    for c, s in r['export_summary']['classes'].items():
        lines.append(f"| {short(c)} | {s.get('table_alerts', 0)} | {s.get('table_objects', 0)} | {s.get('sampled_alerts', 0)} | "
                     f"{s.get('missing_static_rows', '–')} | {fmt(s.get('min_mjd'), 2)}–{fmt(s.get('max_mjd'), 2)} |")
    lines.append('')
    lines.append('Software: ' + ', '.join(f'{k} {v}' for k, v in r['software'].items()) + '.')
    lines += ['', 'Tags are Fink filter memberships of single alerts and overlap (see tag_overlap.png); a top-1 prediction counts as correct when it is any tag of the alert.',
              'Files: metrics.json, test_per_class.csv, cluster_profiles.csv, test_contingency.csv, tag_overlap.csv, model.json and PNG figures.', '']
    return '\n'.join(lines)


def predict(args):
    model = strict_json(Path(args.model).read_text(encoding='utf-8'), args.model)
    if model.get('format') != MODEL_FORMAT:
        raise EvalError('unsupported model format')
    pre = Preprocessor.from_json(model['preprocessing'])
    comps, mean = np.array(model['pca']['components']), np.array(model['pca']['mean'])
    centers, profiles, radii = np.array(model['centers']), np.array(model['cluster_profiles']), np.array(model['radii'])
    weights = np.array(model.get('score_weights', [1.0] * len(model['classes'])))
    out = Path(args.output)
    fd, tmp = tempfile.mkstemp(prefix='.predict-', dir=out.absolute().parent)
    total = 0

    def flush(rows, fh):
        P = (pre.apply([r['cells'] for r in rows]) - mean) @ comps.T
        dist = np.linalg.norm(P[:, None, :] - centers[None, :, :], axis=2)
        j = dist.argmin(axis=1)
        for r, c, d in zip(rows, j, dist[np.arange(len(j)), j]):
            scores = profiles[c] * weights
            fh.write(json.dumps({'row_key': r.get('row_key'), 'cluster': int(c), 'distance': float(d),
                                 'outlier': bool(d > radii[c]), 'predicted': model['classes'][int(scores.argmax())],
                                 'scores': {k: float(s) for k, s in zip(model['classes'], scores)}}) + '\n')

    try:
        with os.fdopen(fd, 'w', encoding='utf-8') as fh:
            batch = []
            for n, line in enumerate(Path(args.input).open(encoding='utf-8'), 1):
                if not line.strip():
                    continue
                r = strict_json(line, f'{args.input}:{n}')
                if r.get('kind') != 'dia' or not isinstance(r.get('cells'), dict):
                    raise EvalError(f'{args.input}:{n} is not a DIA alerts.jsonl record')
                batch.append(r)
                total += 1
                if len(batch) == 10000:
                    flush(batch, fh)
                    batch = []
            if batch:
                flush(batch, fh)
        if not total:
            raise EvalError('input has no alerts')
        os.replace(tmp, out)
    finally:
        if os.path.exists(tmp):
            os.unlink(tmp)
    print(f'Classified {total} alerts -> {out}')


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest='command', required=True)
    t = sub.add_parser('train')
    t.add_argument('--config', required=True)
    t.add_argument('--input', required=True, help='export_tag_alerts.groovy output directory')
    t.add_argument('--output', required=True, help='fresh output directory')
    q = sub.add_parser('predict')
    q.add_argument('--model', required=True)
    q.add_argument('--input', required=True)
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

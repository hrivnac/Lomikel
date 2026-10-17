#!/usr/bin/env python3
"""Train independent, label-free LSST DIA/SS light-curve embeddings and KMeans.

Standalone inference: validate an object with the JSONL contract, call
encode_features(record, model['bins']), normalize by feature_mean/feature_scale,
then tanh(x @ encoder_weights + encoder_bias). Choose nearest Euclidean center;
mark it an outlier if its distance exceeds that center's radius. Cluster IDs
are not astrophysical classes, nor calibrated probabilities.
"""
import argparse
import hashlib
import json
import math
import re
import struct
from pathlib import Path

import numpy as np
from sklearn.cluster import KMeans

BANDS = ("u", "g", "r", "i", "z", "y")
TABLES = {"dia": "rubin.diaSource_static", "ss": "rubin.diaSource_sso"}
FORMAT = "lomikel-lsst-lc-nn-kmeans-v1"
MAX_LINE_BYTES = 1024 * 1024


def model_digest(model):
    """SHA-256 of typed, length-framed values (IEEE-754 binary64 for floats).

    Exclude only model_id; every other field, including provenance and metrics,
    is bound to the identifier. Maps are ordered by key, not JSON formatting.
    """
    digest = hashlib.sha256()

    def emit(value):
        if value is None:
            digest.update(b"n")
        elif isinstance(value, bool):
            digest.update(b"t" if value else b"f")
        elif isinstance(value, int):
            digest.update(b"i" + str(value).encode("ascii") + b":")
        elif isinstance(value, float):
            if not math.isfinite(value):
                raise ValueError("non-finite model number")
            digest.update(b"d" + struct.pack(">d", value))
        elif isinstance(value, str):
            data = value.encode("utf-8")
            digest.update(b"s" + str(len(data)).encode("ascii") + b":" + data)
        elif isinstance(value, list):
            digest.update(b"l" + str(len(value)).encode("ascii") + b":")
            for item in value:
                emit(item)
        elif isinstance(value, dict):
            digest.update(b"m" + str(len(value)).encode("ascii") + b":")
            for key in sorted(value):
                if not isinstance(key, str):
                    raise ValueError("non-string model key")
                emit(key)
                emit(value[key])
        else:
            raise ValueError("unsupported model value")

    emit({key: value for key, value in model.items() if key != "model_id"})
    return digest.hexdigest()


def positive_int(value):
    try:
        result = int(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("expected positive integer") from exc
    if result < 1:
        raise argparse.ArgumentTypeError("expected positive integer")
    return result


def unique_pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def numeric(source, field):
    value = source.get(field)
    if not isinstance(value, str):
        raise ValueError(f"{field} must be a finite numeric string")
    try:
        number = float(value)
    except ValueError as exc:
        raise ValueError(f"{field} must be a finite numeric string") from exc
    if not math.isfinite(number):
        raise ValueError(f"{field} must be finite")
    return number


def validate(record):
    if not isinstance(record, dict) or record.get("kind") not in TABLES:
        raise ValueError("invalid kind: expected dia or ss")
    kind = record["kind"]
    if not isinstance(record.get("object_id"), str) or not re.fullmatch(r"[0-9]+", record["object_id"]):
        raise ValueError("object_id must be a numeric string")
    key = record.get("object_key")
    pattern = r"[0-9]{3}_[0-9]+_" if kind == "dia" else r"[0-9]{2}_[A-Za-z0-9]+_"
    if not isinstance(key, str) or not re.fullmatch(pattern, key):
        raise ValueError("object_key must be a complete kind-specific prefix ending _")
    if kind == "dia" and key.split("_")[1] != record["object_id"]:
        raise ValueError("DIA object_key must contain object_id")
    if record.get("table") != TABLES[kind]:
        raise ValueError("unexpected table for kind")
    if not isinstance(record.get("schema"), str) or not record["schema"]:
        raise ValueError("schema must be a nonempty string")
    if record.get("complete") is not True:
        raise ValueError("complete must be true")
    sources = record.get("sources")
    if not isinstance(sources, list):
        raise ValueError("sources must be an array")
    if len(sources) > 512:
        raise ValueError("exceeds 512 sources per object")
    if len(sources) < 3:
        raise ValueError("need at least 3 observations")
    seen_rows, seen_ids = set(), set()
    for source in sources:
        if not isinstance(source, dict):
            raise ValueError("source must be an object")
        row_key, source_id = source.get("row_key"), source.get("source_id")
        if not isinstance(row_key, str) or not row_key.startswith(key) or len(row_key) == len(key):
            raise ValueError("source row_key must start with object_key")
        if not isinstance(source_id, str) or not re.fullmatch(r"[0-9]+", source_id):
            raise ValueError("source_id must be a numeric string")
        if row_key in seen_rows or source_id in seen_ids:
            raise ValueError("duplicate source row_key or source_id")
        seen_rows.add(row_key)
        seen_ids.add(source_id)
        if source.get("band") not in BANDS:
            raise ValueError("invalid band")
        numeric(source, "mjd")
        numeric(source, "flux")
        if numeric(source, "flux_err") <= 0:
            raise ValueError("flux_err must be positive")
    times = [float(source["mjd"]) for source in sources]
    if not max(times) > min(times) or not math.isfinite(max(times) - min(times)):
        raise ValueError("observations must span >0 finite MJD")
    return record


def encode_features(record, bins):
    """[flux band-major, error band-major, mask band-major, duration, scale]."""
    validate(record)
    if not isinstance(bins, int) or bins < 1:
        raise ValueError("bins must be positive")
    sources = sorted(record["sources"], key=lambda s: (float(s["mjd"]), s["source_id"]))
    start, end = float(sources[0]["mjd"]), float(sources[-1]["mjd"])
    duration = end - start
    magnitudes = sorted(abs(float(s["flux"])) for s in sources)
    middle = len(magnitudes) // 2
    median = (magnitudes[middle] if len(magnitudes) % 2 else
              magnitudes[middle - 1] / 2 + magnitudes[middle] / 2)
    scale = max(1.0, median)
    flux = np.zeros((6, bins), dtype=float)
    error = np.zeros((6, bins), dtype=float)
    count = np.zeros((6, bins), dtype=float)
    for source in sources:
        band = BANDS.index(source["band"])
        position = min(bins - 1, math.floor((float(source["mjd"]) - start) / duration * bins))
        flux[band, position] += np.arcsinh(float(source["flux"]) / scale)
        error[band, position] += np.log1p(float(source["flux_err"]) / scale)
        count[band, position] += 1
    present = count > 0
    flux[present] /= count[present]
    error[present] /= count[present]
    feature = np.concatenate((flux.ravel(), error.ravel(), present.astype(float).ravel(),
                              [np.log1p(duration) / 5, np.log1p(scale) / 10]))
    if not np.isfinite(feature).all():
        raise ValueError("non-finite encoded feature")
    return feature


def read_objects(path, limit):
    grouped = {"dia": [], "ss": []}
    origins = {}
    identities = set()
    with path.open("rb") as stream:
        for number, line in enumerate(stream, 1):
            if number > limit:
                raise ValueError(f"input exceeds --max-objects={limit}")
            if len(line) > MAX_LINE_BYTES:
                raise ValueError(f"line {number}: exceeds {MAX_LINE_BYTES} bytes")
            try:
                record = validate(json.loads(line, object_pairs_hook=unique_pairs))
                kind = record["kind"]
                identity = (kind, record["object_id"])
                if identity in identities:
                    raise ValueError("duplicate kind/object_id")
                identities.add(identity)
                origin = (record["table"], record["schema"])
                if kind in origins and origins[kind] != origin:
                    raise ValueError("mixed table/schema within kind")
                origins[kind] = origin
                grouped[kind].append(record)
            except (ValueError, TypeError) as exc:
                raise ValueError(f"line {number}: {exc}") from exc
    return grouped, origins


def fit_kind(kind, rows, origin, clusters, embedding, bins, epochs, seed):
    if len(rows) < max(3, clusters + 1):
        raise ValueError(f"{kind}: need at least --clusters+1 objects for train/validation")
    features = np.stack([encode_features(row, bins) for row in rows])
    cells = 6 * bins
    rng = np.random.default_rng(seed)
    order = rng.permutation(len(rows))
    n_val = max(1, int(round(len(rows) * .2)))
    train, val = order[n_val:], order[:n_val]
    if len(train) < clusters:
        raise ValueError(f"{kind}: need at least --clusters train objects")
    mean, scale = features[train].mean(axis=0), features[train].std(axis=0)
    scale[scale == 0] = 1
    x = (features - mean) / scale
    if not np.isfinite(x).all():
        raise ValueError("non-finite normalized features")
    d = x.shape[1]
    # Decoder predicts raw asinh flux; mask prevents absent cells from contributing.
    target = features[:, :cells]
    mask = features[:, 2 * cells:3 * cells]
    w1 = rng.normal(0, 1 / math.sqrt(d), (d, embedding))
    b1 = np.zeros(embedding)
    w2 = rng.normal(0, 1 / math.sqrt(embedding), (embedding, cells))
    b2 = np.zeros(cells)
    params = [w1, b1, w2, b2]
    moments = [np.zeros_like(p) for p in params]
    variances = [np.zeros_like(p) for p in params]
    best = None
    step = 0

    def loss(indices):
        z = np.tanh(x[indices] @ w1 + b1)
        residual = (z @ w2 + b2 - target[indices]) * mask[indices]
        return float(np.sum(residual ** 2) / max(1, np.sum(mask[indices])))

    for epoch in range(epochs):
        for batch in np.array_split(rng.permutation(train), max(1, math.ceil(len(train) / 128))):
            z = np.tanh(x[batch] @ w1 + b1)
            residual = (z @ w2 + b2 - target[batch]) * mask[batch]
            dy = 2 * residual / max(1, np.sum(mask[batch]))
            grads = [x[batch].T @ ((dy @ w2.T) * (1 - z * z)),
                     np.sum((dy @ w2.T) * (1 - z * z), axis=0),
                     z.T @ dy, np.sum(dy, axis=0)]
            step += 1
            for p, g, m, v in zip(params, grads, moments, variances):
                m *= .9
                m += .1 * g
                v *= .999
                v += .001 * g * g
                p -= .01 * (m / (1 - .9 ** step)) / (np.sqrt(v / (1 - .999 ** step)) + 1e-8)
        score = loss(val)
        if not math.isfinite(score):
            raise ValueError("non-finite training loss")
        if best is None or score < best[0]:
            best = (score, epoch + 1, [p.copy() for p in params])
    w1, b1, w2, b2 = best[2]
    params = [w1, b1, w2, b2]
    projected = np.tanh(x[train] @ w1 + b1)
    if len(np.unique(projected, axis=0)) < clusters:
        raise ValueError(f"{kind}: need at least --clusters distinct training embeddings")
    fitted = KMeans(n_clusters=clusters, random_state=seed, n_init=10).fit(projected)
    if len(np.unique(fitted.labels_)) < clusters:
        raise ValueError(f"{kind}: need at least --clusters distinct training embeddings")
    centers = fitted.cluster_centers_
    radii = [float(np.percentile(np.linalg.norm(projected[fitted.labels_ == i] - center, axis=1), 99))
             for i, center in enumerate(centers)]
    model = {"format": FORMAT, "kind": kind, "bands": list(BANDS), "bins": bins,
             "feature_mean": mean.tolist(), "feature_scale": scale.tolist(),
             "encoder_weights": w1.tolist(), "encoder_bias": b1.tolist(),
             "centers": centers.tolist(), "radii": radii,
             "n_train": len(train), "n_val": len(val), "train_loss": loss(train),
             "val_loss": loss(val), "best_epoch": best[1],
             "provenance": {"table": origin[0], "schema": origin[1], "seed": seed,
                            "clusters": clusters, "embedding": embedding, "epochs": epochs,
                            "target": "raw asinh flux, observed-mask MSE",
                            "radius_quantile": .99, "training_unit": "one complete object; no labels",
                            "feature_order": "flux, error, mask (each band-major bins), duration, scale",
                            "distance": "Euclidean encoder embedding; reject if > nearest-center radius"}}
    model["model_id"] = model_digest(model)
    return model


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--kind", choices=("dia", "ss", "both"), default="both")
    parser.add_argument("--clusters", type=positive_int, required=True)
    parser.add_argument("--embedding", type=positive_int, default=8)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--max-objects", type=positive_int, default=100000)
    parser.add_argument("--bins", type=positive_int, default=16)
    parser.add_argument("--epochs", type=positive_int, default=100)
    args = parser.parse_args(argv)
    if args.max_objects > 100000:
        parser.error("--max-objects cannot exceed 100000")
    if args.bins > 512 or args.embedding > 512 or args.epochs > 10000 or args.clusters > 100000:
        parser.error("bins/embedding cannot exceed 512; epochs cannot exceed 10000")
    try:
        grouped, origins = read_objects(args.input, args.max_objects)
        kinds = ("dia", "ss") if args.kind == "both" else (args.kind,)
        models = {kind: fit_kind(kind, grouped[kind], origins.get(kind), args.clusters,
                                 args.embedding, args.bins, args.epochs, args.seed)
                  for kind in kinds}
        args.output_dir.mkdir(parents=True, exist_ok=True)
        for kind, model in models.items():
            (args.output_dir / f"{kind}.json").write_text(
                json.dumps(model, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    except (OSError, ValueError) as exc:
        parser.error(str(exc))
    for kind, model in models.items():
        print(f"{kind}: {model['n_train'] + model['n_val']} objects "
              f"({model['n_train']} train, {model['n_val']} val), "
              f"{len(model['centers'])} clusters, embedding {len(model['encoder_bias'])}, "
              f"{model['bins']} bins, best epoch {model['best_epoch']}, "
              f"val loss {model['val_loss']:.6g}, output {args.output_dir / f'{kind}.json'}")


if __name__ == "__main__":
    main()

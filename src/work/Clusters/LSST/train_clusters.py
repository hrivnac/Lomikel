#!/usr/bin/env python3
"""Fit alert-level (not object-level) PCA/KMeans models from exported HBase JSONL.

Policy: DIA and SS are trained independently, never joined. Only allowlisted
r: measured alert-level morphology/flux cells present in at least 80% of a
kind are used; f: classifier/crossmatch output and unknown fields are excluded.
Missing eligible values use training medians; training mean/std standardizes;
standard deviations of constant columns are replaced with 1. At inference,
apply imputation, centering/scaling, PCA projection and nearest-center distance
in that order. Distances exceeding that center's training 99th percentile
radius are outliers (not a new astrophysical class). Cluster numbers carry no
astrophysical meaning; neither do they transfer between DIA and SS models.
"""

import argparse
import json
import math
import re
import sys
from collections import defaultdict
from pathlib import Path

import numpy as np
from sklearn.cluster import KMeans
from sklearn.decomposition import PCA

FORMAT = "lomikel-lsst-pca-kmeans-v1"
MIN_PRESENCE = 0.8
MAX_LINE_BYTES = 1024 * 1024
# A positive allowlist is necessary: numeric f:clf_* and f:xm_* cells are
# predictions/crossmatches, not measurements, and unknown r: cells may leak
# time, astrometry, history or labels. Expand only after schema review.
MEASURED = re.compile(
    r"(?:psfFlux|psfFluxErr|apFlux|apFluxErr|scienceFlux|scienceFluxErr|"
    r"templateFlux|templateFluxErr|snr|extendedness|ixx|ixy|iyy|"
    r"dipole(?:Flux|FluxErr|Length|Angle|Separation)|"
    r"trail(?:Flux|FluxErr|Length|Width|Angle|Snr))"
)


def eligible(name):
    return isinstance(name, str) and name.startswith("r:") and bool(MEASURED.fullmatch(name[2:]))


def positive_int(value):
    try:
        result = int(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("expected a positive integer") from exc
    if result < 1:
        raise argparse.ArgumentTypeError("expected a positive integer")
    return result


def components_arg(value):
    if re.fullmatch(r"[1-9][0-9]*", value):
        return int(value)
    try:
        target = float(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("components must be an integer or variance target in (0, 1)") from exc
    if not 0 < target < 1:
        raise argparse.ArgumentTypeError("variance target must be in (0, 1)")
    return target


def unique_pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def read_rows(path, limit):
    grouped = defaultdict(list)
    origins = {}
    seen = set()
    tables = {"dia": "rubin.diaSource_static", "ss": "rubin.diaSource_sso"}
    with path.open("rb") as stream:
        for number, raw in enumerate(stream, 1):
            if number > limit:
                raise ValueError(f"input exceeds --max-rows={limit}")
            if len(raw) > MAX_LINE_BYTES:
                raise ValueError(f"line {number}: exceeds {MAX_LINE_BYTES} bytes")
            try:
                item = json.loads(raw, object_pairs_hook=unique_pairs)
            except ValueError as exc:
                raise ValueError(f"line {number}: {exc}") from exc
            if (not isinstance(item, dict) or item.get("kind") not in {"dia", "ss"}
                    or not isinstance(item.get("row_key"), str) or not item["row_key"]
                    or not isinstance(item.get("cells"), dict)):
                raise ValueError(f"line {number}: expected kind, nonempty row_key, cells object")
            kind = item["kind"]
            identity = (kind, item["row_key"])
            if identity in seen:
                raise ValueError(f"line {number}: duplicate alert row key in {kind}")
            seen.add(identity)
            table = item.get("table", tables[kind])
            schema = item.get("schema", "schema_5.0rc0_8.52.0")
            if table != tables[kind] or not isinstance(schema, str) or not schema:
                raise ValueError(f"line {number}: unexpected table or schema for {kind}")
            origin = (table, schema)
            if kind in origins and origins[kind] != origin:
                raise ValueError(f"line {number}: mixed table/schema within {kind}")
            origins[kind] = origin
            cells = {}
            for key, value in item["cells"].items():
                if not eligible(key):
                    continue
                if not isinstance(value, str):
                    raise ValueError(f"line {number}: numeric cell {key} must be a string")
                try:
                    number_value = float(value)
                except ValueError:
                    continue  # textual/missing cells are not numeric observations
                if not math.isfinite(number_value):
                    raise ValueError(f"line {number}: non-finite numeric cell {key}")
                cells[key] = number_value
            grouped[item["kind"]].append(cells)
    return grouped, origins


def fit_kind(kind, rows, clusters, components, seed, origin):
    if len(rows) < max(2, clusters):
        raise ValueError(f"{kind}: need at least two rows and at least --clusters rows")
    counts = defaultdict(int)
    for cells in rows:
        for key in cells:
            counts[key] += 1
    features = sorted(key for key, count in counts.items()
                      if count / len(rows) >= MIN_PRESENCE)
    if not features:
        raise ValueError(f"{kind}: no eligible numeric features with >= {MIN_PRESENCE:g} presence")
    matrix = np.array([[cells.get(key, np.nan) for key in features] for cells in rows], dtype=float)
    impute = np.nanmedian(matrix, axis=0)
    matrix = np.where(np.isnan(matrix), impute, matrix)
    mean = matrix.mean(axis=0)
    scale = matrix.std(axis=0)
    scale[scale == 0] = 1.0
    normalized = (matrix - mean) / scale
    possible = min(len(rows), len(features))
    if isinstance(components, int) and components > possible:
        raise ValueError(f"{kind}: components exceeds min(rows, features)={possible}")
    # sklearn's float n_components requires singular spectrum and rejects zero
    # variance; explicitly retain one component for a constant-only sample.
    if np.all(normalized == 0):
        if isinstance(components, int) and components != 1:
            raise ValueError(f"{kind}: constant data only supports one component")
        raise ValueError(f"{kind}: all eligible features are constant")
    pca = PCA(n_components=components, svd_solver="full")
    projected = pca.fit_transform(normalized)
    model = KMeans(n_clusters=clusters, random_state=seed, n_init=10).fit(projected)
    radii = []
    for label, center in enumerate(model.cluster_centers_):
        distances = np.linalg.norm(projected[model.labels_ == label] - center, axis=1)
        radii.append(float(np.percentile(distances, 99)))
    return {
        "format": FORMAT, "kind": kind, "features": features,
        "impute": impute.tolist(), "mean": mean.tolist(), "scale": scale.tolist(),
        "components": pca.components_.tolist(),
        "explained_variance_ratio": pca.explained_variance_ratio_.tolist(),
        "centers": model.cluster_centers_.tolist(), "radii": radii,
        "n_rows": len(rows),
        "provenance": {
            "algorithm": "sklearn PCA(full) + KMeans(n_init=10)",
            "seed": seed, "clusters": clusters, "components_requested": components,
            "table": origin[0],
            "schema": origin[1],
            "min_presence": MIN_PRESENCE, "radius_quantile": 0.99,
            "distance": "Euclidean in PCA coordinates; reject if distance > nearest center radius",
            "training_unit": "one exported alert row; no astrophysical labels",
            "sklearn_version": __import__("sklearn").__version__,
        },
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--kind", choices=("dia", "ss", "both"), default="both")
    parser.add_argument("--clusters", type=positive_int, required=True)
    parser.add_argument("--components", type=components_arg, default=0.95,
                        help="positive integer count or explained variance target (0,1); default .95")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--max-rows", type=positive_int, default=100000)
    args = parser.parse_args(argv)
    try:
        grouped, origins = read_rows(args.input, args.max_rows)
        kinds = ("dia", "ss") if args.kind == "both" else (args.kind,)
        models = {kind: fit_kind(kind, grouped[kind], args.clusters, args.components,
                                 args.seed, origins.get(kind)) for kind in kinds}
        args.output_dir.mkdir(parents=True, exist_ok=True)
        for kind, model in models.items():
            (args.output_dir / f"{kind}.json").write_text(
                json.dumps(model, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    except (OSError, ValueError) as exc:
        parser.error(str(exc))


if __name__ == "__main__":
    main()

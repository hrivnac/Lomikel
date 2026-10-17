#!/usr/bin/env python3
"""Offline held-out association of label-free LSST clusters with OCol tag snapshots.

Inputs are independent local JSONL exports; there is no graph/HBase client here.
PCA row-map records require kind,row_key,object_id;
object_id must be the canonical graph object.objectId, never an SS row-key alias.
Tags accept flattened records (kind,object_id,classifier,survey,flavor,cls,weight)
or native export_graph_labels.py known-ID JSONL with nested classes.
PCA defaults to source-specific graph edge instance matching; explicit object
policy restores legacy propagation. LC remains object-level. These are FINK
tag associations, NOT physical truth.
"""
import argparse
import csv
import json
import math
import os
import re
import sys
import tempfile
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent.parent / "LightCurves" / "LSST"))
import train_clusters as pca
import train_lightcurves as lc

KEYS = ("classifier", "survey", "flavor", "cls")
MAX_LINE = 1024 * 1024
GRAPH_FORMAT = "lomikel-lsst-ocol-known-ids-v1"
GRAPH_SCOPE = {"classifier": "FINK", "survey": "LSST", "flavor": ""}


def read_jsonl(path, limit):
    records = []
    with path.open("rb") as source:
        for number, line in enumerate(source, 1):
            if number > limit or len(line) > MAX_LINE:
                raise ValueError(f"{path}: line {number}: exceeds input bound")
            try:
                record = json.loads(line, object_pairs_hook=pca.unique_pairs,
                                    parse_constant=lambda value: (_ for _ in ()).throw(ValueError(value)))
            except (ValueError, UnicodeDecodeError) as exc:
                raise ValueError(f"{path}: line {number}: {exc}") from exc
            if not isinstance(record, dict):
                raise ValueError(f"{path}: line {number}: expected JSON object")
            records.append(record)
    return records


def identity(value, field):
    if not isinstance(value, str) or not re.fullmatch(r"[0-9]+", value):
        raise ValueError(f"{field} must be a decimal string (large IDs must not be JSON numbers)")
    return value


def clean_inputs(mode, input_path, map_path, label_path, max_rows, requested_kind="both", label_format="auto"):
    rows = read_jsonl(input_path, max_rows)
    mapping = {}
    if mode == "pca":
        if map_path is None:
            raise ValueError("PCA requires --row-map with source_mjd")
        for item in read_jsonl(map_path, max_rows):
            if item.get("kind") not in ("dia", "ss") or not isinstance(item.get("row_key"), str) or not item["row_key"]:
                raise ValueError("mapping requires kind and nonempty row_key")
            oid = identity(item.get("object_id"), "mapping object_id")
            key = (item["kind"], item["row_key"])
            if key in mapping:
                raise ValueError("duplicate row mapping")
            # When a DIA exporter row key embeds the object ID, enforce it.
            if item["kind"] == "dia" and re.fullmatch(r"[0-9]{3}_[0-9]+_.+", item["row_key"]):
                if item["row_key"].split("_")[1] != oid:
                    raise ValueError("DIA row key and canonical object ID disagree")
            mapping[key] = item
    elif map_path is not None:
        raise ValueError("--row-map is PCA-only")
    objects = defaultdict(list)
    origins = {}
    seen_rows = set()
    for row in rows:
        if mode == "pca":
            if set(row) - {"kind", "row_key", "table", "schema", "cells"}:
                raise ValueError("alert JSONL has unexpected field (possible label leakage)")
            if not isinstance(row.get("cells"), dict) or any(not pca.eligible(k) for k in row["cells"]):
                raise ValueError("alert cells must contain only allowlisted measurements; no labels")
            key = (row.get("kind"), row.get("row_key"))
            if key in seen_rows:
                raise ValueError("duplicate alert row")
            seen_rows.add(key)
            if key not in mapping:
                raise ValueError("missing exact alert mapping")
            oid = mapping[key]["object_id"]
        else:
            if set(row) - {"kind", "object_id", "object_key", "table", "schema", "complete", "sources"}:
                raise ValueError("LC JSONL has unexpected field (possible label leakage)")
            if not isinstance(row.get("sources"), list) or any(
                    not isinstance(s, dict) or set(s) - {"row_key", "source_id", "mjd", "band", "flux", "flux_err"}
                    for s in row["sources"]):
                raise ValueError("LC sources have unexpected field (possible label leakage)")
            lc.validate(row)
            oid = row["object_id"]
            key = (row["kind"], oid)
            if key in seen_rows:
                raise ValueError("duplicate LC object")
            seen_rows.add(key)
        kind = row["kind"]
        table = "rubin.diaSource_static" if kind == "dia" else "rubin.diaSource_sso"
        origin = (row.get("table", table), row.get("schema", "schema_5.0rc0_8.52.0"))
        if origin[0] != table or not isinstance(origin[1], str) or not origin[1]:
            raise ValueError("unexpected table/schema")
        if kind in origins and origins[kind] != origin:
            raise ValueError("mixed table/schema within kind")
        origins[kind] = origin
        objects[kind, oid].append(row)
    if mode == "pca" and set(mapping) != seen_rows:
        raise ValueError("mapping has orphan rows or mismatched kind")
    labels, snapshots = parse_labels(read_jsonl(label_path, max_rows * 16), objects,
                                     requested_kind, label_format)
    return objects, labels, mapping, origins, snapshots


def parse_labels(records, objects, requested_kind, label_format="auto"):
    if not records and label_format != "flat":
        raise ValueError("empty label snapshot: specify --label-format flat only for an intentional empty legacy export")
    labels = defaultdict(list)
    snapshots, seen = {}, set()
    snapshot_format = None
    kinds_by_id = defaultdict(set)
    for kind, oid in objects:
        kinds_by_id[oid].add(kind)
    for item in records:
        oid = identity(item.get("object_id"), "label object_id")
        native = "format" in item or "classes" in item or "provenance" in item
        if label_format != "auto" and native != (label_format == "native"):
            raise ValueError("label snapshot format contradicts --label-format")
        if snapshot_format is not None and native != snapshot_format:
            raise ValueError("mixed label snapshot formats")
        snapshot_format = native
        if native:
            if set(item) != {"format", "object_id", "object_vertex_id", "classes",
                             "graph_object_found", "provenance"} or item["format"] != GRAPH_FORMAT:
                raise ValueError("wrong graph label format")
            provenance = item["provenance"]
            if (not isinstance(provenance, dict) or set(provenance) !=
                    {"scope", "endpoint", "coverage", "consistency", "unit"} or
                    provenance["scope"] != GRAPH_SCOPE or
                    not isinstance(provenance["endpoint"], str) or not provenance["endpoint"] or
                    provenance["coverage"] != "known-object-ids-only" or
                    provenance["consistency"] != "non-transactional per-batch reads" or
                    provenance["unit"] != "object memberships; instance MJDs do not label every HBase source"):
                raise ValueError("wrong graph label scope or provenance")
            found = item["graph_object_found"]
            if (not isinstance(found, bool) or not isinstance(item["classes"], list) or
                    (found and item["object_vertex_id"] is None) or
                    (not found and (item["object_vertex_id"] is not None or item["classes"]))):
                raise ValueError("contradictory graph object identity")
            candidates = kinds_by_id[oid]
            if not candidates:
                raise ValueError("graph label canonical object ID absent from input")
            if requested_kind == "both":
                if len(candidates) != 1:
                    raise ValueError("ambiguous graph label kind: provide --kind dia/ss for a single-kind input or canonical IDs unique across input kinds")
                kind = next(iter(candidates))
            else:
                if candidates and candidates != {requested_kind}:
                    raise ValueError("contradictory graph label kind and canonical input identity")
                kind = requested_kind
            if (kind, oid) in snapshots:
                raise ValueError("duplicate graph label snapshot identity")
            snapshots[kind, oid] = item
            edge_ids, ocol_ids, classes = set(), set(), set()
            for cls in item["classes"]:
                if not isinstance(cls, dict) or set(cls) != {
                        "cls", "weight", "instances", "weights", "edge_id", "ocol_id",
                        "edge_lbl", "ocol_lbl"}:
                    raise ValueError("wrong graph class format")
                if (not isinstance(cls["cls"], str) or not cls["cls"] or
                        cls["edge_id"] is None or cls["ocol_id"] is None or
                        cls["edge_lbl"] not in (None, "deepcontains") or
                        cls["ocol_lbl"] not in (None, "OCol")):
                    raise ValueError("contradictory graph class identity")
                edge, ocol = json.dumps(cls["edge_id"], sort_keys=True), json.dumps(cls["ocol_id"], sort_keys=True)
                if edge in edge_ids or ocol in ocol_ids or cls["cls"] in classes:
                    raise ValueError("duplicate graph class identity")
                edge_ids.add(edge); ocol_ids.add(ocol); classes.add(cls["cls"])
                for field in ("instances", "weights"):
                    values = cls[field]
                    if values is not None and (not isinstance(values, list) or any(
                            isinstance(v, bool) or not isinstance(v, (float, int)) or not math.isfinite(v)
                            for v in values)):
                        raise ValueError("invalid graph edge metadata")
                if (cls["instances"] is not None and cls["weights"] is not None and
                        len(cls["instances"]) != len(cls["weights"])):
                    raise ValueError("contradictory graph edge metadata")
                add_label(labels, seen, kind, oid, {**GRAPH_SCOPE, "cls": cls["cls"]}, cls["weight"])
        else:
            if item.get("kind") not in ("dia", "ss"):
                raise ValueError("label kind must be dia or ss")
            if any(not isinstance(item.get(k), str) or (not item[k].strip() and k != "flavor") for k in KEYS):
                raise ValueError("label classifier/survey/flavor/cls must be explicit strings")
            add_label(labels, seen, item["kind"], oid, {k: item[k] for k in KEYS}, item.get("weight"))
    if snapshot_format is True:
        missing = set(objects) - set(snapshots)
        if missing:
            raise ValueError(f"missing graph label snapshot for {len(missing)} input canonical objects")
    for tags in labels.values():
        if not math.isfinite(sum(tag["weight"] for tag in tags)):
            raise ValueError("nonfinite total label weight for object")
    return labels, snapshots


def add_label(labels, seen, kind, oid, label, weight):
    key = (kind, oid, *(label[k] for k in KEYS))
    if key in seen:
        raise ValueError("duplicate label snapshot identity")
    seen.add(key)
    if isinstance(weight, bool) or not isinstance(weight, (int, float)) or not math.isfinite(weight) or weight <= 0:
        raise ValueError("label weight must be finite positive number")
    labels[kind, oid].append({"label": label, "weight": float(weight)})


def instance_matches(objects, mapping, snapshots):
    """Join bounded feature rows to registered edge instances, never to object tags."""
    matched = {}
    missing = defaultdict(int)
    occurrences = defaultdict(int)
    seen_source_ids = set()
    for (kind, oid), rows in objects.items():
        source_rows = []
        for row in rows:
            item = mapping[kind, row["row_key"]]
            if set(item) != {"kind", "row_key", "object_id", "source_id", "mjd"}:
                raise ValueError("instance policy requires exact source_id and mjd identity sidecar")
            sid = identity(item["source_id"], "mapping source_id")
            if (kind, sid) in seen_source_ids:
                raise ValueError("duplicate source_id across HBase objects or rows")
            seen_source_ids.add((kind, sid))
            try:
                mjd = float(item["mjd"])
            except (ValueError, TypeError, OverflowError) as exc:
                raise ValueError("invalid mapping mjd") from exc
            if not isinstance(item["mjd"], str) or not math.isfinite(mjd) or mjd <= 0:
                raise ValueError("invalid mapping mjd")
            # DIA row keys produced by HBase carry the canonical object and MJD.
            parts = row["row_key"].split("_")
            if kind == "dia" and len(parts) == 3 and re.fullmatch(r"[0-9]{3}", parts[0]):
                try:
                    suffix_mjd = float(parts[2])
                except ValueError as exc:
                    raise ValueError("DIA row key MJD disagrees with identity sidecar") from exc
                if parts[1] != oid or not math.isfinite(suffix_mjd) or abs(suffix_mjd - mjd) > 1e-6:
                    raise ValueError("DIA row key and identity sidecar disagree")
            if any(other_sid == sid for _, other_sid, _ in source_rows):
                raise ValueError("duplicate HBase source_id within object")
            if any(abs(other_mjd - mjd) <= 1e-6 for other_mjd, _, _ in source_rows):
                raise ValueError("ambiguous duplicate HBase MJDs")
            source_rows.append((mjd, sid, row))
        snapshot = snapshots.get((kind, oid))
        if not snapshot:
            continue
        for cls in snapshot["classes"]:
            instances, weights = cls["instances"], cls["weights"]
            if instances is None:
                raise ValueError("graph class missing instances for instance policy")
            if weights is None:
                raise ValueError("graph class missing per-instance weights for instance policy")
            if any(w <= 0 for w in weights):
                raise ValueError("instance weights must be positive")
            for index, instance in enumerate(instances):
                if instance <= 0:
                    raise ValueError("graph instance MJD must be positive")
                occurrences[kind] += 1
                hits = [row for mjd, _, row in source_rows if abs(mjd - instance) <= 1e-6]
                if len(hits) > 1:
                    raise ValueError("ambiguous graph instance matches multiple source rows")
                if not hits:
                    missing[kind] += 1
                    continue
                key = (kind, hits[0]["row_key"])
                if any(tag["label"]["cls"] == cls["cls"] for tag in matched.get(key, [])):
                    raise ValueError("multiple graph instances match one source row and class")
                matched.setdefault(key, []).append({"label": {**GRAPH_SCOPE, "cls": cls["cls"]},
                                                      "weight": float(weights[index])})
    for tags in matched.values():
        if not math.isfinite(sum(t["weight"] for t in tags)):
            raise ValueError("nonfinite source label weight")
    return matched, missing, occurrences


def split_ids(ids, fraction, seed):
    import hashlib
    ordered = sorted(ids, key=lambda oid: (hashlib.sha256(f"{seed}:{oid}".encode()).digest(), oid))
    n = max(1, round(len(ids) * fraction))
    if n == len(ids):
        raise ValueError("holdout leaves no training objects")
    return sorted(ordered[n:]), sorted(ordered[:n])


def nearest(model, row, mode):
    if mode == "pca":
        if row["kind"] != model["kind"] or (row["table"], row["schema"]) != (
                model["provenance"]["table"], model["provenance"]["schema"]):
            raise ValueError("held-out PCA provenance mismatch")
        if not any(row["cells"].get(k) is not None and str(row["cells"][k]).strip() for k in model["features"]):
            raise ValueError("held-out alert has no model measurements")
        vector = []
        for i, field in enumerate(model["features"]):
            raw = row["cells"].get(field)
            if raw is None or not str(raw).strip():
                value = model["impute"][i]
            else:
                if not isinstance(raw, str):
                    raise ValueError(f"{field}: numeric cell must be a string")
                try:
                    value = float(raw)
                except ValueError as exc:
                    raise ValueError(f"{field}: invalid measured value") from exc
                if not math.isfinite(value):
                    raise ValueError(f"{field}: nonfinite measured value")
            z = (value - model["mean"][i]) / model["scale"][i]
            if not math.isfinite(z):
                raise ValueError("nonfinite normalized alert")
            vector.append(z)
        projection = [sum(w * value for w, value in zip(component, vector))
                      for component in model["components"]]
    else:
        if row["kind"] != model["kind"] or (row["table"], row["schema"]) != (
                model["provenance"]["table"], model["provenance"]["schema"]):
            raise ValueError("held-out LC provenance mismatch")
        features = lc.encode_features(row, model["bins"])
        normalized = [(float(v) - mean) / scale for v, mean, scale in zip(
            features, model["feature_mean"], model["feature_scale"])]
        projection = []
        for i, bias in enumerate(model["encoder_bias"]):
            activation = bias
            for j, value in enumerate(normalized):
                activation += value * model["encoder_weights"][j][i]
            projection.append(math.tanh(activation))
    if not all(map(math.isfinite, projection)):
        raise ValueError("nonfinite held-out projection")
    distances = []
    for center in model["centers"]:
        distance = 0.0
        for a, b in zip(projection, center):
            distance = math.hypot(distance, a - b)
        distances.append(distance)
    if not all(map(math.isfinite, distances)):
        raise ValueError("nonfinite held-out distance")
    index = min(range(len(distances)), key=distances.__getitem__)
    result = {"class_id": index, "distance": distances[index],
              "outlier": distances[index] > model["radii"][index]}
    if mode == "lc":
        result.update(source_count=len(row["sources"]),
                      bands_present=[band for band in lc.BANDS if any(s["band"] == band for s in row["sources"])])
    return result


def contingency(entries):
    """Each labeled object (or matching alert) has mass one, split among tags."""
    mass = defaultdict(float)
    total = 0.0
    for cluster, tags, weight in entries:
        total += weight
        norm = sum(tag["weight"] for tag in tags)
        for tag in tags:
            identity_key = tuple(tag["label"][k] for k in KEYS)
            mass[cluster, identity_key] += weight * tag["weight"] / norm
    if not total:
        return []
    row_sum = defaultdict(float)
    col_sum = defaultdict(float)
    for (cluster, tag), amount in mass.items():
        row_sum[cluster] += amount
        col_sum[tag] += amount
    cells = []
    for cluster, tag in sorted(mass):
        amount = mass[cluster, tag]
        cells.append({"class_id": cluster, **dict(zip(KEYS, tag)), "mass": amount,
                      "joint_fraction": amount / total,
                      "cluster_fraction": amount / row_sum[cluster],
                      "label_fraction": amount / col_sum[tag],
                      "covariation": amount / total - row_sum[cluster] * col_sum[tag] / total**2})
    # Include zero cells so the covariance matrix is complete.
    existing = {(c["class_id"], tuple(c[k] for k in KEYS)) for c in cells}
    for cluster in sorted(row_sum):
        for tag in sorted(col_sum):
            if (cluster, tag) not in existing:
                cells.append({"class_id": cluster, **dict(zip(KEYS, tag)), "mass": 0.0,
                              "joint_fraction": 0.0, "cluster_fraction": 0.0,
                              "label_fraction": 0.0,
                              "covariation": -row_sum[cluster] * col_sum[tag] / total**2})
    return sorted(cells, key=lambda c: (c["class_id"], *(c[k] for k in KEYS)))


def evaluate(kind, mode, objects, labels, mapping, origin, args, snapshots=None, source_tags=None, missing=None, occurrences=None):
    source_tags = source_tags or {}
    missing = missing or {}
    occurrences = occurrences or {}
    input_ids = sorted(oid for k, oid in objects if k == kind)
    specific = mode == "pca" and args.pca_label_policy == "instance"
    def applicable(oid, row):
        return (source_tags.get((kind, row["row_key"]), []) if specific
                else labels.get((kind, oid), []))
    labeled_ids = {oid for oid in input_ids if any(applicable(oid, r) for r in objects[kind, oid])}
    ids = [oid for oid in input_ids if args.cohort == "all" or oid in labeled_ids]
    if len(ids) < 3:
        raise ValueError(f"{kind}: need >=3 distinct objects in {args.cohort} cohort")
    train_ids, test_ids = split_ids(ids, args.holdout, args.seed)
    train = [row for oid in train_ids for row in objects[kind, oid]
             if not specific or args.cohort == "all" or applicable(oid, row)]
    if specific and args.cohort == "all":
        # All cohort retains unmatched objects, but does not fit on unregistered sources.
        train = [row for row in train if source_tags.get((kind, row["row_key"]))]
    if mode == "pca":
        # Use exactly the existing trainer measurement parsing and fit_kind routine.
        parsed = []
        for row in train:
            cells = {}
            for key, value in row["cells"].items():
                if not isinstance(value, str):
                    raise ValueError(f"numeric cell {key} must be a string")
                try:
                    number = float(value)
                except ValueError:
                    continue
                if not math.isfinite(number):
                    raise ValueError(f"nonfinite numeric cell {key}")
                cells[key] = number
            parsed.append(cells)
        model = pca.fit_kind(kind, parsed, args.clusters, args.components, args.seed, origin)
    else:
        model = lc.fit_kind(kind, train, origin, args.clusters, args.embedding,
                            args.bins, args.epochs, args.seed)
    predictions = []
    object_entries, alert_entries = [], []
    matched_objects = set()
    for oid in test_ids:
        matched = []
        for row in objects[kind, oid]:
            if specific and args.cohort == "labeled" and not applicable(oid, row):
                continue
            result = nearest(model, row, mode)
            result.update(kind=kind, object_id=oid)
            if mode == "pca":
                result["row_key"] = row["row_key"]
            tags = applicable(oid, row)
            result["matched_tag_count"] = len(tags)
            predictions.append(result)
            if tags:
                matched.append((result["class_id"], tags))
                alert_entries.append((result["class_id"], tags, 1.0))
        if matched:
            matched_objects.add(oid)
            for cluster, tags in matched:
                object_entries.append((cluster, tags, 1.0 / len(matched)))
    n_alerts = len(predictions)
    counts = {"input_objects": len(input_ids),
              "input_unlabeled_objects": len(input_ids) - len(labeled_ids),
              "excluded_unlabeled_objects": len(input_ids) - len(ids),
              "objects": len(ids), "train_objects": len(train_ids),
              "heldout_objects": len(test_ids), "train_rows": len(train),
              "heldout_alerts" if mode == "pca" else "heldout_curves": n_alerts,
              "labeled_heldout_objects": len(matched_objects),
              "unlabeled_heldout_objects": len(test_ids) - len(matched_objects),
              "matched_heldout_alerts" if mode == "pca" else "labeled_heldout_curves": len(alert_entries),
              "outlier_rows": sum(p["outlier"] for p in predictions)}
    if mode == "pca":
        heldout_candidates = sum(len(objects[kind, oid]) for oid in test_ids)
        counts.update(heldout_candidate_source_rows=heldout_candidates,
                      unmatched_heldout_source_rows=heldout_candidates - len(alert_entries),
                      input_source_rows=sum(len(objects[kind, oid]) for oid in input_ids),
                      matched_input_source_rows=sum(bool(source_tags.get((kind, r["row_key"])))
                                                    for oid in input_ids for r in objects[kind, oid]) if specific else
                                                sum(len(objects[kind, oid]) for oid in labeled_ids),
                      graph_instance_occurrences=occurrences.get(kind, 0) if specific else 0,
                      missing_graph_instances=missing.get(kind, 0) if specific else 0)
        counts["unmatched_input_source_rows"] = counts["input_source_rows"] - counts["matched_input_source_rows"]
    return model, {"mode": mode, "kind": kind, "cohort": args.cohort,
                   "pca_label_policy": args.pca_label_policy if mode == "pca" else None,
                   "interpretation": ("association with source-specific FINK tag instances; not physical truth"
                                      if specific else "association with object-level FINK tags; not physical truth"),
                   "matching": {"pca": ("canonical object_id plus a unique HBase identity-sidecar MJD within 1e-6 day; source_id is validated but absent from graph" if specific else
                                           "canonical object_id; OCol object tag deliberately propagated to every exported alert (edge instances do not limit coverage)")
                                if mode == "pca" else "canonical object_id"},
                   "seed": args.seed, "holdout_fraction": args.holdout, "counts": counts,
                   "train_object_ids": train_ids, "heldout_object_ids": test_ids,
                   "heldout_label_snapshot": [snapshots[kind, oid] for oid in test_ids
                                               if (kind, oid) in snapshots] if snapshots is not None else [],
                   "predictions": predictions,
                   "associations": {"object_weighted": contingency(object_entries),
                                    "alert_weighted": contingency(alert_entries) if mode == "pca" else None}}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument("--mode", choices=("pca", "lc"), required=True)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--row-map", type=Path)
    parser.add_argument("--labels", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--kind", choices=("dia", "ss", "both"), default="both")
    parser.add_argument("--cohort", choices=("labeled", "all"), default="labeled",
                        help="select classified objects before split/fit (default: labeled); all retains unlabeled objects")
    parser.add_argument("--label-format", choices=("auto", "native", "flat"), default="auto",
                        help="empty legacy snapshots require explicit flat; empty native/auto fail closed")
    parser.add_argument("--pca-label-policy", choices=("instance", "object"), default="instance",
                        help="instance (default) joins graph MJDs to HBase source identity; object explicitly propagates tags")
    parser.add_argument("--clusters", type=pca.positive_int, required=True)
    parser.add_argument("--components", type=pca.components_arg, default=.95)
    parser.add_argument("--bins", type=pca.positive_int, default=16)
    parser.add_argument("--embedding", type=pca.positive_int, default=8)
    parser.add_argument("--epochs", type=pca.positive_int, default=100)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--holdout", type=float, default=.2)
    parser.add_argument("--max-rows", type=pca.positive_int, default=100000)
    args = parser.parse_args(argv)
    if args.mode == "lc" and any(token == "--pca-label-policy" or token.startswith("--pca-label-policy=")
                                 for token in (sys.argv[1:] if argv is None else argv)):
        parser.error("--pca-label-policy is PCA-only")
    if (not math.isfinite(args.holdout) or not 0 < args.holdout < 1 or
            args.max_rows > 100000 or args.bins > 512 or args.embedding > 512 or args.epochs > 10000):
        parser.error("invalid holdout or training bound")
    try:
        if args.output_dir.exists():
            raise ValueError("output directory already exists; choose a fresh destination")
        objects, labels, mapping, origins, snapshots = clean_inputs(
            args.mode, args.input, args.row_map, args.labels, args.max_rows, args.kind,
            args.label_format)
        if args.mode == "pca" and args.pca_label_policy == "instance" and not snapshots:
            raise ValueError("flat snapshots require --pca-label-policy object")
        source_tags, missing, occurrences = (instance_matches(objects, mapping, snapshots)
                                             if args.mode == "pca" and args.pca_label_policy == "instance"
                                             else ({}, {}, {}))
        kinds = ("dia", "ss") if args.kind == "both" else (args.kind,)
        results = {kind: evaluate(kind, args.mode, objects, labels, mapping,
                                  origins.get(kind), args, snapshots, source_tags, missing, occurrences) for kind in kinds}
        args.output_dir.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix=".lsst-eval-", dir=args.output_dir.parent) as stage:
            stage = Path(stage)
            for kind, (model, report) in results.items():
                for suffix, data in (("model", model), ("report", report)):
                    (stage / f"{kind}.{suffix}.json").write_text(
                        json.dumps(data, indent=2, allow_nan=False) + "\n", encoding="utf-8")
                with (stage / f"{kind}.associations.csv").open("w", newline="", encoding="utf-8") as stream:
                    fields = ["unit", "class_id", *KEYS, "mass", "joint_fraction",
                              "cluster_fraction", "label_fraction", "covariation"]
                    writer = csv.DictWriter(stream, fieldnames=fields)
                    writer.writeheader()
                    for unit, cells in report["associations"].items():
                        for cell in cells or []:
                            writer.writerow({"unit": unit, **cell})
            os.rename(stage, args.output_dir)
    except (OSError, ValueError, TypeError) as exc:
        parser.error(str(exc))
    for kind in kinds:
        print(f"{kind}: {results[kind][1]['counts']['heldout_objects']} held-out objects; "
              f"report {args.output_dir / f'{kind}.report.json'}")


if __name__ == "__main__":
    main()

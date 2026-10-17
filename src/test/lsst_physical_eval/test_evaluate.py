"""Offline held-out, label-blind LSST association contract."""
import csv
import json
import subprocess
import sys
from pathlib import Path

import pytest

SCRIPT = Path(__file__).resolve().parents[2] / "work/Clusters/LSST/evaluate_physical.py"
SCHEMA = "schema_5.0rc0_8.52.0"


def dump(path, rows):
    path.write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")


def alert(i, kind="dia", oid=None, j=0):
    oid = oid or str(9007199254740993 + i)
    return {"kind": kind, "row_key": f"row-{kind}-{i}-{j}",
            "table": "rubin.diaSource_static" if kind == "dia" else "rubin.diaSource_sso",
            "schema": SCHEMA, "cells": {"r:psfFlux": str(i * 3 + j + 1),
                                      "r:ixx": str(i * i + j + 1)}}


def curve(i, kind="dia"):
    oid = str(9007199254740993 + i)
    prefix = f"000_{oid}_" if kind == "dia" else f"00_OBJ{i}_"
    return {"kind": kind, "object_id": oid, "object_key": prefix,
            "table": "rubin.diaSource_static" if kind == "dia" else "rubin.diaSource_sso",
            "schema": SCHEMA, "complete": True,
            "sources": [{"row_key": prefix + str(j), "source_id": str(10000 + i * 20 + j),
                         "mjd": str(60000 + i * .01 + j), "band": "ugr"[j % 3],
                         "flux": str(i * (j + 1) + j * j + 1), "flux_err": "1"}
                        for j in range(5)]}


def label(i, kind="dia", cls="variable", weight: float = 1):
    return {"kind": kind, "object_id": str(9007199254740993 + i),
            "classifier": "FINK", "survey": "LSST", "flavor": "science",
            "cls": cls, "weight": weight}

def graph_label(i, classes=None):
    classes = classes if classes is not None else [
        {"cls": "rubin.tag_A", "weight": 0.8, "instances": [60001.0],
         "weights": [0.5], "edge_id": f"edge-{i}", "ocol_id": f"ocol-{i}",
         "edge_lbl": "deepcontains", "ocol_lbl": "OCol"}]
    return {"format": "lomikel-lsst-ocol-known-ids-v1",
            "object_id": str(9007199254740993 + i),
            "object_vertex_id": f"vertex-{i}", "classes": classes,
            "graph_object_found": True,
            "provenance": {"scope": {"classifier": "FINK", "survey": "LSST", "flavor": ""},
                           "endpoint": "http://example.invalid", "coverage": "known-object-ids-only",
                           "consistency": "non-transactional per-batch reads",
                           "unit": "object memberships; instance MJDs do not label every HBase source"}}


def launch(tmp_path, mode="pca", rows=None, maps=None, labels=None, *flags, extra_flags=()):
    rows = rows if rows is not None else ([alert(i, j=j) for i in range(10) for j in range(2)]
                                           if mode == "pca" else [curve(i) for i in range(10)])
    labels = labels if labels is not None else [label(i, cls=c) for i in range(10)
                                                for c in (("variable", "AGN") if i % 2 == 0 else ("AGN",))]
    maps = maps if maps is not None else ([{"kind": r["kind"], "row_key": r["row_key"],
                                                                       "object_id": str(9007199254740993 + int(r["row_key"].split("-")[2]))}
                                         for r in rows] if mode == "pca" else [])
    source, snapshot, mapping, out = (tmp_path / n for n in ("train.jsonl", "labels.jsonl", "map.jsonl", "out"))
    dump(source, rows); dump(snapshot, labels); dump(mapping, maps)
    cmd = [sys.executable, str(SCRIPT), "--mode", mode, "--input", str(source),
           "--labels", str(snapshot), "--output-dir", str(out), "--kind", "dia",
           "--clusters", "2", "--seed", "17", "--holdout", "0.3"]
    if mode == "pca":
        cmd += ["--row-map", str(mapping), "--components", "2"]
    else:
        cmd += ["--bins", "2", "--embedding", "2", "--epochs", "3"]
    result = subprocess.run(cmd + list(flags) + list(extra_flags), text=True, capture_output=True)
    return result, out


def test_pca_heldout_multilabel_reports_and_models_are_label_blind(tmp_path):
    result, out = launch(tmp_path)
    assert result.returncode == 0, result.stderr
    report = json.loads((out / "dia.report.json").read_text())
    model = json.loads((out / "dia.model.json").read_text())
    assert report["interpretation"] == "association with object-level FINK tags; not physical truth"
    assert report["counts"]["objects"] == 10
    assert report["counts"]["train_objects"] == 7
    assert report["counts"]["heldout_objects"] == 3
    assert report["counts"]["heldout_alerts"] == 6
    assert model["n_rows"] == 14
    assert "AGN" not in json.dumps(model) and "classifier" not in json.dumps(model).lower()
    assert set(report["train_object_ids"]).isdisjoint(report["heldout_object_ids"])
    assert all(isinstance(p["object_id"], str) for p in report["predictions"])
    assert all(p["object_id"] in report["heldout_object_ids"] for p in report["predictions"])
    for name, expected in (("object_weighted", 3), ("alert_weighted", 6)):
        matrix = report["associations"][name]
        assert sum(cell["mass"] for cell in matrix) == pytest.approx(expected)
        assert sum(cell["joint_fraction"] for cell in matrix) == pytest.approx(1)
        assert sum(cell["covariation"] for cell in matrix) == pytest.approx(0)
    with (out / "dia.associations.csv").open() as stream:
        csvrows = list(csv.DictReader(stream))
    assert len(csvrows) == len(report["associations"]["object_weighted"]) * 2
    first = (out / "dia.report.json").read_bytes()
    repeat = tmp_path / "repeat"
    repeat.mkdir()
    result, again = launch(repeat)
    assert result.returncode == 0, result.stderr
    assert (again / "dia.report.json").read_bytes() == first


def test_lc_heldout_only_and_source_count(tmp_path):
    result, out = launch(tmp_path, "lc")
    assert result.returncode == 0, result.stderr
    report = json.loads((out / "dia.report.json").read_text())
    model = json.loads((out / "dia.model.json").read_text())
    assert model["n_train"] + model["n_val"] == 7
    assert report["counts"]["heldout_objects"] == 3
    assert all(p["source_count"] == 5 for p in report["predictions"])
    assert report["associations"]["alert_weighted"] is None


@pytest.mark.parametrize("mutation,needle", [
    (lambda rows, maps, labels: maps.pop(), "mapping"),
    (lambda rows, maps, labels: maps.append(dict(maps[0], object_id="999")), "duplicate"),
    (lambda rows, maps, labels: maps.__setitem__(0, dict(maps[0], object_id=9007199254740993)), "string"),
    (lambda rows, maps, labels: (rows[0].update(row_key="000_9007199254740993_1"),
                                maps[0].update(row_key="000_9007199254740993_1", object_id="999")), "dia"),
    (lambda rows, maps, labels: rows[0].update(labels=["AGN"]), "label"),
    (lambda rows, maps, labels: labels.append(dict(labels[0], weight=2)), "duplicate"),
    (lambda rows, maps, labels: labels[0].update(weight="NaN"), "weight"),
    (lambda rows, maps, labels: labels[0].update(object_id=9007199254740993), "string"),
])
def test_rejects_ambiguous_or_leaking_inputs_before_publication(tmp_path, mutation, needle):
    rows = [alert(i, j=j) for i in range(10) for j in range(2)]
    maps = [{"kind": "dia", "row_key": r["row_key"],
             "object_id": str(9007199254740993 + int(r["row_key"].split("-")[2]))} for r in rows]
    labels = [label(i) for i in range(10)]
    mutation(rows, maps, labels)
    result, out = launch(tmp_path, rows=rows, maps=maps, labels=labels)
    assert result.returncode != 0
    assert needle in result.stderr.lower()
    assert not out.exists()


@pytest.mark.parametrize("mode", ["pca", "lc"])
def test_default_cohort_excludes_unlabeled_before_split_and_fit(tmp_path, mode):
    labels = [label(i) for i in range(6)]
    result, out = launch(tmp_path, mode=mode, labels=labels)
    assert result.returncode == 0, result.stderr
    report = json.loads((out / "dia.report.json").read_text())
    model = json.loads((out / "dia.model.json").read_text())
    selected = {str(9007199254740993 + i) for i in range(6)}
    assert report["cohort"] == "labeled"
    assert set(report["train_object_ids"] + report["heldout_object_ids"]) == selected
    assert report["counts"]["input_objects"] == 10
    assert report["counts"]["excluded_unlabeled_objects"] == 4
    assert report["counts"]["objects"] == 6
    assert report["counts"]["unlabeled_heldout_objects"] == 0
    assert (model["n_rows"] if mode == "pca" else model["n_train"] + model["n_val"]) == (8 if mode == "pca" else 4)


def test_all_cohort_retains_sparse_unlabeled_objects(tmp_path):
    result, out = launch(tmp_path, labels=[label(0)], extra_flags=["--cohort", "all"])
    assert result.returncode == 0, result.stderr
    report = json.loads((out / "dia.report.json").read_text())
    assert report["counts"]["input_unlabeled_objects"] == 9
    assert report["counts"]["excluded_unlabeled_objects"] == 0
    assert report["counts"]["objects"] == 10
    assert set(report["train_object_ids"] + report["heldout_object_ids"]) == {
        str(9007199254740993 + i) for i in range(10)}


def test_unlabeled_holdout_coverage_does_not_silently_discard_objects(tmp_path):
    result, out = launch(tmp_path, labels=[], extra_flags=["--cohort", "all", "--label-format", "flat"])
    assert result.returncode == 0, result.stderr
    report = json.loads((out / "dia.report.json").read_text())
    assert report["cohort"] == "all"
    assert report["counts"]["input_unlabeled_objects"] == 10
    assert report["counts"]["excluded_unlabeled_objects"] == 0
    assert report["counts"]["labeled_heldout_objects"] == 0
    assert report["counts"]["unlabeled_heldout_objects"] == 3
    assert report["associations"]["object_weighted"] == []


def test_ss_alias_never_inferred_and_kind_isolated(tmp_path):
    rows = [alert(i, "ss", j=j) for i in range(9) for j in range(2)]
    maps = [{"kind": "ss", "row_key": r["row_key"],
             "object_id": str(9007199254740993 + i)}
            for i in range(9) for r in rows[2*i:2*i+2]]
    result, out = launch(tmp_path, rows=rows, maps=maps, labels=[label(i, "ss") for i in range(9)],
                         extra_flags=["--kind", "ss"])
    assert result.returncode == 0, result.stderr
    assert (out / "ss.report.json").exists()
    assert not (out / "dia.report.json").exists()


def test_groovy_nearest_center_parity_on_heldout_pca_and_lc(tmp_path):
    groovy = Path("/opt/groovy-4.0.9/bin/groovy")
    if not groovy.exists():
        pytest.skip("Groovy not installed")
    for mode in ("pca", "lc"):
        dest = tmp_path / mode
        dest.mkdir()
        result, out = launch(dest, mode)
        assert result.returncode == 0, result.stderr
        report = json.loads((out / "dia.report.json").read_text())
        rows = [json.loads(line) for line in (dest / "train.jsonl").read_text().splitlines()]
        target = report["predictions"][0]
        row = next(r for r in rows if (
            (mode == "pca" and r["row_key"] == target["row_key"]) or
            (mode == "lc" and r["object_id"] == target["object_id"])))
        record = dest / "row.json"
        record.write_text(json.dumps(row))
        classifier = (SCRIPT.parent / "classify_alert.groovy" if mode == "pca" else
                      SCRIPT.parents[2] / "LightCurves/LSST/classify_lightcurve.groovy")
        test_flag = "LSST_ALERT_TEST" if mode == "pca" else "LSST_LC_TEST"
        expression = ("def C = new GroovyShell(new Binding(" + test_flag + ":true)).evaluate(new File(" +
                      json.dumps(str(classifier)) + ")); def s=new groovy.json.JsonSlurper(); " +
                      "println groovy.json.JsonOutput.toJson(C.newInstance().classify(s.parse(new File(" +
                      json.dumps(str(out / "dia.model.json")) + ")),s.parse(new File(" +
                      json.dumps(str(record)) + "))))")
        check = subprocess.run([str(groovy), "-e", expression], text=True, capture_output=True)
        assert check.returncode == 0, check.stderr
        actual = json.loads(check.stdout)
        expected = report["predictions"][0]
        assert actual["class_id"] == expected["class_id"]
        assert actual["outlier"] == expected["outlier"]
        assert actual["distance"] == pytest.approx(expected["distance"], abs=1e-10)


def test_changing_labels_does_not_change_split_models_or_predictions(tmp_path):
    outputs = []
    for name, labels in (("one", [label(i) for i in range(10)]),
                         ("two", [label(i, cls="totally different") for i in range(10)])):
        dest = tmp_path / name
        dest.mkdir()
        result, out = launch(dest, labels=labels)
        assert result.returncode == 0, result.stderr
        outputs.append((out / "dia.model.json").read_bytes())
        report = json.loads((out / "dia.report.json").read_text())
        outputs.append(report["predictions"])
    assert outputs[0] == outputs[2]
    assert outputs[1] == outputs[3]


def test_object_weighting_differs_from_alert_weighting_when_counts_vary(tmp_path):
    rows = [alert(i) for i in range(10)] + [alert(0, j=j) for j in range(1, 5)]
    maps = [{"kind": "dia", "row_key": r["row_key"],
             "object_id": str(9007199254740993 + int(r["row_key"].split("-")[2]))} for r in rows]
    result, out = launch(tmp_path, rows=rows, maps=maps, labels=[label(i) for i in range(10)])
    assert result.returncode == 0, result.stderr
    report = json.loads((out / "dia.report.json").read_text())
    assert sum(c["mass"] for c in report["associations"]["object_weighted"]) == 3
    assert sum(c["mass"] for c in report["associations"]["alert_weighted"]) == report["counts"]["heldout_alerts"]
    assert all(p["matched_tag_count"] == 1 for p in report["predictions"])


def test_nonfinite_multilabel_weight_sum_fails_before_output(tmp_path):
    labels = [label(i, weight=1e308) for i in range(10)]
    labels += [label(i, cls="other", weight=1e308) for i in range(10)]
    result, out = launch(tmp_path, labels=labels)
    assert result.returncode != 0
    assert "weight" in result.stderr.lower()
    assert "traceback" not in result.stderr.lower()
    assert not out.exists()


def test_sparse_edge_instances_do_not_limit_intentional_object_propagation(tmp_path):
    labels = [dict(label(i), instances=[60000.0, 60001.0]) for i in range(10)]
    result, out = launch(tmp_path, labels=labels)
    assert result.returncode == 0, result.stderr
    report = json.loads((out / "dia.report.json").read_text())
    assert report["counts"]["matched_heldout_alerts"] == report["counts"]["heldout_alerts"]
    assert "propagated to every exported alert" in report["matching"]["pca"]

def test_native_graph_snapshot_propagates_tags_and_preserves_edge_metadata(tmp_path):
    labels = [graph_label(i) for i in range(10)]
    result, out = launch(tmp_path, labels=labels)
    assert result.returncode == 0, result.stderr
    report = json.loads((out / "dia.report.json").read_text())
    model = (out / "dia.model.json").read_text()
    assert report["counts"]["matched_heldout_alerts"] == 6
    assert all(p["matched_tag_count"] == 1 for p in report["predictions"])
    assert {r["object_id"] for r in report["heldout_label_snapshot"]} == set(report["heldout_object_ids"])
    assert report["heldout_label_snapshot"][0]["classes"][0]["instances"] == [60001.0]
    assert report["heldout_label_snapshot"][0]["classes"][0]["edge_id"].startswith("edge-")
    assert report["heldout_label_snapshot"][0]["provenance"]["scope"]["flavor"] == ""
    assert "rubin.tag_A" not in model and "edge-" not in model

@pytest.mark.parametrize("change,needle", [
    (lambda r: r.update(format="other"), "format"),
    (lambda r: r["provenance"]["scope"].update(flavor="wrong"), "scope"),
    (lambda r: r["provenance"].update(coverage="all"), "provenance"),
    (lambda r: r.update(graph_object_found=False), "identity"),
    (lambda r: r.update(object_vertex_id=None), "identity"),
    (lambda r: r["classes"].append(dict(r["classes"][0], cls="other")), "duplicate"),
    (lambda r: r["classes"].append(dict(r["classes"][0], edge_id="other")), "duplicate"),
    (lambda r: r["classes"][0].update(weight=0), "weight"),
    (lambda r: r["classes"][0].update(weight=float("inf")), "infinity"),
    (lambda r: r["classes"][0].update(weights=[float("nan")]), "nan"),
    (lambda r: r["classes"][0].update(edge_lbl="wrong"), "identity"),
    (lambda r: r.update(object_id=9007199254740993), "string"),
])
def test_native_graph_rejects_invalid_snapshot_without_output(tmp_path, change, needle):
    labels = [graph_label(i) for i in range(10)]
    change(labels[0])
    result, out = launch(tmp_path, labels=labels)
    assert result.returncode != 0
    assert needle in result.stderr.lower()
    assert not out.exists()

def test_native_graph_rejects_duplicate_object_even_if_unlabeled(tmp_path):
    labels = [graph_label(0, [])] * 2
    result, out = launch(tmp_path, labels=labels)
    assert result.returncode != 0 and "duplicate" in result.stderr.lower()
    assert not out.exists()


@pytest.mark.parametrize("mode,flags", [("pca", ()), ("pca", ("--cohort", "all")), ("lc", ())])
def test_native_graph_rejects_missing_input_object_before_cohort_selection(tmp_path, mode, flags):
    labels = [graph_label(i, []) if i == 0 else graph_label(i) for i in range(9)]
    result, out = launch(tmp_path, mode=mode, labels=labels, extra_flags=flags)
    assert result.returncode != 0
    assert "missing" in result.stderr.lower() and "snapshot" in result.stderr.lower()
    assert not out.exists()


@pytest.mark.parametrize("flags", [(), ("--label-format", "native"), ("--cohort", "all")])
def test_empty_snapshot_fails_closed_without_explicit_flat_format(tmp_path, flags):
    result, out = launch(tmp_path, labels=[], extra_flags=flags)
    assert result.returncode != 0
    assert "empty" in result.stderr.lower() and "snapshot" in result.stderr.lower()
    assert not out.exists()

def test_native_graph_unlabeled_objects_and_lc_mode(tmp_path):
    labels = [graph_label(i, []) if i % 2 else graph_label(i) for i in range(10)]
    result, out = launch(tmp_path, mode="lc", labels=labels, extra_flags=["--cohort", "all"])
    assert result.returncode == 0, result.stderr
    report = json.loads((out / "dia.report.json").read_text())
    assert len(report["heldout_label_snapshot"]) == 3
    assert report["counts"]["labeled_heldout_objects"] == sum(bool(x["classes"]) for x in report["heldout_label_snapshot"])


def test_native_graph_default_labeled_cohort_keeps_provenance(tmp_path):
    labels = [graph_label(i, []) if i % 2 else graph_label(i) for i in range(10)]
    result, out = launch(tmp_path, labels=labels)
    assert result.returncode == 0, result.stderr
    report = json.loads((out / "dia.report.json").read_text())
    assert report["counts"]["input_unlabeled_objects"] == 5
    assert report["counts"]["excluded_unlabeled_objects"] == 5
    assert len(report["heldout_label_snapshot"]) == report["counts"]["heldout_objects"]
    assert all(s["classes"] and s["provenance"]["coverage"] == "known-object-ids-only"
               for s in report["heldout_label_snapshot"])

def test_native_graph_kind_inferred_from_unique_canonical_ids_in_both_mode(tmp_path):
    rows = [alert(i, kind=k) for k in ("dia", "ss") for i in range(10)]
    # Distinct canonical IDs for the two kinds, as required by a kind-less graph export.
    maps = [{"kind": r["kind"], "row_key": r["row_key"],
             "object_id": str(9007199254740993 + int(r["row_key"].split("-")[2]) +
                              (100 if r["kind"] == "ss" else 0))} for r in rows]
    labels = [graph_label(i) for i in range(10)] + [dict(graph_label(i), object_id=str(9007199254741093 + i)) for i in range(10)]
    result, out = launch(tmp_path, rows=rows, maps=maps, labels=labels, extra_flags=["--kind", "both", "--clusters", "2"])
    assert result.returncode == 0, result.stderr
    assert (out / "ss.report.json").exists() and (out / "dia.report.json").exists()

def test_native_graph_rejects_kind_ambiguity_in_both_mode(tmp_path):
    rows = [alert(i, kind=k) for k in ("dia", "ss") for i in range(10)]
    maps = [{"kind": r["kind"], "row_key": r["row_key"],
             "object_id": str(9007199254740993 + int(r["row_key"].split("-")[2]))} for r in rows]
    result, out = launch(tmp_path, rows=rows, maps=maps, labels=[graph_label(0)], extra_flags=["--kind", "both"])
    assert result.returncode != 0 and "ambiguous" in result.stderr.lower()
    assert not out.exists()

def test_native_graph_rejects_unknown_canonical_id(tmp_path):
    result, out = launch(tmp_path, labels=[graph_label(999)])
    assert result.returncode != 0 and "canonical" in result.stderr.lower()
    assert not out.exists()

def test_native_graph_rejects_mixed_snapshot_formats(tmp_path):
    result, out = launch(tmp_path, labels=[graph_label(0), label(1)])
    assert result.returncode != 0 and "format" in result.stderr.lower()
    assert not out.exists()

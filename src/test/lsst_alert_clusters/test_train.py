"""Offline contract tests for the LSST alert-level trainer (synthetic rows only)."""
import importlib.util
import json
import math
import subprocess
import sys
from pathlib import Path

import pytest

TRAINER = Path(__file__).resolve().parents[2] / "work/Clusters/LSST/train_clusters.py"


def row(kind, index, **cells):
    return {"kind": kind, "row_key": f"object-{index}",
            "cells": {f"r:{key}": str(value) for key, value in cells.items()}}


def run_train(tmp_path, rows, *options):
    source = tmp_path / "alerts.jsonl"
    source.write_text("".join(json.dumps(r) + "\n" for r in rows), encoding="utf-8")
    dest = tmp_path / "models"
    result = subprocess.run([sys.executable, str(TRAINER), "--input", str(source),
                             "--output-dir", str(dest), *options],
                            text=True, capture_output=True)
    return result, dest


def test_success_summary_reports_each_written_model(tmp_path):
    rows = [row(kind, i, psfFlux=i + 1, ixx=i * i + 1)
            for kind in ("dia", "ss") for i in range(8)]
    result, dest = run_train(tmp_path, rows, "--clusters", "2", "--components", "2")
    assert result.returncode == 0, result.stderr
    lines = result.stdout.splitlines()
    assert len(lines) == 2
    for kind, line in zip(("dia", "ss"), lines):
        model = json.loads((dest / f"{kind}.json").read_text())
        assert line == (f"{kind}: {model['n_rows']} alerts, {len(model['centers'])} clusters, "
                        f"{len(model['components'])} PCA components, {len(model['features'])} features, "
                        f"explained variance {sum(model['explained_variance_ratio']):.1%}, "
                        f"output {dest / f'{kind}.json'}")


def test_no_success_summary_when_requested_kind_cannot_train(tmp_path):
    rows = [row("dia", i, psfFlux=i + 1) for i in range(8)]
    result, dest = run_train(tmp_path, rows, "--clusters", "2")
    assert result.returncode != 0
    assert result.stdout == ""
    assert not (dest / "dia.json").exists()


def test_no_success_summary_when_second_model_write_fails(tmp_path, monkeypatch, capsys):
    rows = [row(kind, i, psfFlux=i + 1) for kind in ("dia", "ss") for i in range(8)]
    source = tmp_path / "alerts.jsonl"
    source.write_text("".join(json.dumps(r) + "\n" for r in rows))
    spec = importlib.util.spec_from_file_location("alert_trainer_for_summary", TRAINER)
    trainer = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(trainer)
    write_text = Path.write_text

    def fail_second(path, *args, **kwargs):
        if path.name == "ss.json":
            raise OSError("simulated write failure")
        return write_text(path, *args, **kwargs)

    monkeypatch.setattr(Path, "write_text", fail_second)
    with pytest.raises(SystemExit):
        trainer.main(["--input", str(source), "--output-dir", str(tmp_path / "models"),
                      "--clusters", "2"])
    assert capsys.readouterr().out == ""


def test_trains_independent_models_and_excludes_leakage(tmp_path):
    rows = []
    for kind, offset in (("dia", 0), ("ss", 100)):
        for i in range(12):
            rows.append(row(kind, i, psfFlux=offset + i * 2, ixx=offset + (i % 3) * 4,
                            sourceId=i, diaObjectId=i, ra=i, dec=i, mjd=i,
                            lightcurve_psfFlux=i, history_psfFlux=i, meanFlux=i,
                            sparse=i if i == 0 else "missing"))
    result, dest = run_train(tmp_path, rows, "--kind", "both", "--clusters", "2", "--seed", "7")
    assert result.returncode == 0, result.stderr
    for kind in ("dia", "ss"):
        model = json.loads((dest / f"{kind}.json").read_text())
        assert model["format"] == "lomikel-lsst-pca-kmeans-v1"
        assert model["kind"] == kind
        assert model["features"] == ["r:ixx", "r:psfFlux"]
        assert model["n_rows"] == 12
        assert len(model["components"]) == len(model["explained_variance_ratio"])
        assert all(len(v) == len(model["features"]) for v in model["components"])
        assert len(model["centers"]) == len(model["radii"]) == 2
        assert all(len(v) == len(model["components"]) for v in model["centers"])
        assert all(r >= 0 for r in model["radii"])
        assert "provenance" in model
    assert json.loads((dest / "dia.json").read_text())["mean"] != json.loads((dest / "ss.json").read_text())["mean"]
    assert json.loads((dest / "ss.json").read_text())["provenance"]["table"] == "rubin.diaSource_sso"


def test_excludes_coordinate_time_and_identifier_variants(tmp_path):
    rows = [row("dia", i, psfFlux=i + 1, raErr=i, decSigma=i, coordX=i,
                observationTime=i, midPointMjdTai=i, ssObjectId=i,
                alertId=i, psFluxMean=i, nDiaSources=i, lcFlux=i,
                radec=i, detectionHistory=i) for i in range(6)]
    result, dest = run_train(tmp_path, rows, "--kind", "dia", "--clusters", "2")
    assert result.returncode == 0, result.stderr
    assert json.loads((dest / "dia.json").read_text())["features"] == ["r:psfFlux"]
    assert not (dest / "ss.json").exists()


def test_schema_allowlist_rejects_classifier_crossmatch_and_astrometry(tmp_path):
    rows = []
    for i in range(8):
        cells = {"r:psfFlux": str(i + 1), "r:psfFluxErr": str(i + 2),
                 "r:ixx": str(2 * i + 1), "r:extendedness": str(i / 8),
                 "r:trailRa": str(i), "r:trailDec": str(i),
                 "r:x": str(i), "r:y": str(i), "r:ephemerisRa": str(i),
                 "r:parentDiaSourceId": str(i), "r:exposureTime": str(i),
                 "r:unknownPrediction": str(i), "f:clf_star": str(i),
                 "f:xm_gaia": str(i), "f:psfFlux": str(i)}
        rows.append({"kind": "dia", "row_key": f"alert-{i}", "cells": cells})
    result, dest = run_train(tmp_path, rows, "--kind", "dia", "--clusters", "2")
    assert result.returncode == 0, result.stderr
    model = json.loads((dest / "dia.json").read_text())
    assert model["features"] == ["r:extendedness", "r:ixx", "r:psfFlux", "r:psfFluxErr"]
    assert model["provenance"]["table"] == "rubin.diaSource_static"
    assert model["provenance"]["schema"] == "schema_5.0rc0_8.52.0"


def test_rejects_duplicate_json_cell_keys(tmp_path):
    source = tmp_path / "rows.jsonl"
    source.write_text('{"kind":"dia","row_key":"one","cells":{"r:flux":"1","r:flux":"2"}}\n')
    result = subprocess.run([sys.executable, str(TRAINER), "--input", str(source),
                             "--output-dir", str(tmp_path / "out"), "--kind", "dia",
                             "--clusters", "1"], text=True, capture_output=True)
    assert result.returncode != 0
    assert "duplicate" in result.stderr.lower()
    assert not (tmp_path / "out" / "dia.json").exists()


def test_numeric_contract_and_reproducibility(tmp_path):
    rows = [row("dia", i, psfFlux=i + 1, ixx=i * 3 + 2,
                apFlux=i * i, psfFluxErr=i if i != 3 else "unavailable")
            for i in range(8)]
    first, dest = run_train(tmp_path, rows, "--kind", "dia", "--clusters", "2",
                            "--components", "2", "--seed", "19")
    assert first.returncode == 0, first.stderr
    serialized = (dest / "dia.json").read_text()
    model = json.loads(serialized)
    assert model["features"] == ["r:apFlux", "r:ixx", "r:psfFlux", "r:psfFluxErr"]
    assert model["impute"][3] == 4.0
    assert model["mean"][3] == 3.625  # median-imputed missing observation
    assert len(model["components"]) == 2
    assert len(model["explained_variance_ratio"]) == 2
    assert all(scale > 0 for scale in model["scale"])
    assert model["provenance"]["seed"] == 19
    second, _ = run_train(tmp_path, rows, "--kind", "dia", "--clusters", "2",
                          "--components", "2", "--seed", "19")
    assert second.returncode == 0, second.stderr
    assert (dest / "dia.json").read_text() == serialized


def test_presence_threshold_and_default_variance_target(tmp_path):
    rows = [row("dia", i, psfFlux=i + 1, apFlux=3 * i + 1,
                ixx=i if i < 8 else "missing", ixy=i if i < 7 else "missing")
            for i in range(10)]
    result, dest = run_train(tmp_path, rows, "--kind", "dia", "--clusters", "2")
    assert result.returncode == 0, result.stderr
    model = json.loads((dest / "dia.json").read_text())
    assert model["features"] == ["r:apFlux", "r:ixx", "r:psfFlux"]
    assert sum(model["explained_variance_ratio"]) >= 0.95
    assert sum(model["explained_variance_ratio"][:-1]) < 0.95


def test_radii_are_clusterwise_pca_distance_quantiles(tmp_path):
    rows = [row("dia", i, psfFlux=i * i + 1, ixx=i % 3 + 1)
            for i in range(12)]
    result, dest = run_train(tmp_path, rows, "--kind", "dia", "--clusters", "2", "--seed", "1")
    assert result.returncode == 0, result.stderr
    model = json.loads((dest / "dia.json").read_text())
    buckets = [[] for _ in model["centers"]]
    for item in rows:
        vector = [(float(item["cells"][key]) - mean) / scale
                  for key, mean, scale in zip(model["features"], model["mean"], model["scale"])]
        projected = [sum(x * weight for x, weight in zip(vector, component))
                     for component in model["components"]]
        distances = [math.dist(projected, center) for center in model["centers"]]
        nearest = min(range(len(distances)), key=distances.__getitem__)
        buckets[nearest].append(distances[nearest])
    for distances, radius in zip(buckets, model["radii"]):
        distances.sort()
        position = (len(distances) - 1) * 0.99
        lo = math.floor(position)
        expected = distances[lo] + (distances[math.ceil(position)] - distances[lo]) * (position - lo)
        assert math.isclose(radius, expected, rel_tol=1e-10, abs_tol=1e-10)


def test_bounded_rows_and_invalid_records_are_rejected_without_output(tmp_path):
    cases = [
        ([row("dia", 0, psfFlux=1), row("dia", 0, psfFlux=2)], (), "duplicate"),
        ([dict(row("dia", i, psfFlux=i), table="rubin.diaSource_sso", schema="s1") for i in range(3)], (), "table"),
        ([dict(row("dia", i, psfFlux=i), table="rubin.diaSource_static", schema="s" + str(i % 2)) for i in range(3)], (), "mixed"),
        ([row("dia", i, psfFlux=i) for i in range(4)], ("--max-rows", "3"), "exceeds"),
        ([{"kind": "other", "row_key": "bad", "cells": {}}], (), "kind"),
        ([row("dia", i, psfFlux="NaN") for i in range(3)], (), "non-finite"),
        ([row("dia", i, psfFlux=i) for i in range(3)], ("--components", "0"), "variance"),
        ([row("dia", i, psfFlux=i) for i in range(3)], ("--clusters", "4"), "clusters"),
    ]
    for number, (rows, args, error) in enumerate(cases):
        subdir = tmp_path / str(number)
        subdir.mkdir()
        result, dest = run_train(subdir, rows, "--kind", "dia", "--clusters", "2", *args)
        assert result.returncode != 0
        assert error in result.stderr.lower()
        assert not (dest / "dia.json").exists()

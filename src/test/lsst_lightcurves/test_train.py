"""Offline, synthetic contract checks for LSST object light-curve training."""
import importlib.util
import json
import math
import subprocess
import sys
from pathlib import Path

import numpy as np
import pytest

TRAINER = Path(__file__).resolve().parents[2] / "work/LightCurves/LSST/train_lightcurves.py"
CLASSIFIER = TRAINER.with_name("classify_lightcurve.groovy")
GROOVY = "/opt/groovy-4.0.9/bin/groovy"


def object_row(kind, i, bands=("u", "g", "r", "i", "z", "y")):
    table = "rubin.diaSource_static" if kind == "dia" else "rubin.diaSource_sso"
    oid = str((8000 if kind == "dia" else 9000) + i)
    prefix = f"000_{oid}_" if kind == "dia" else f"00_OBJ{i}_"
    return {"kind": kind, "object_id": oid, "object_key": prefix,
            "table": table, "schema": "schema_5.0rc0_8.52.0", "complete": True,
            "sources": [{"row_key": f"{prefix}{j}", "source_id": str(100000 + i * 100 + j),
                         "mjd": str(60000 + j + i * .01), "band": bands[j % len(bands)],
                         "flux": str((i + 1) * (j % 3 - 1) + j * .3),
                         "flux_err": str(1 + j * .1)} for j in range(9)]}


def run(tmp_path, rows, *extra):
    source = tmp_path / "objects.jsonl"
    source.write_text("".join(json.dumps(row) + "\n" for row in rows))
    out = tmp_path / "models"
    result = subprocess.run([sys.executable, str(TRAINER), "--input", str(source),
                             "--output-dir", str(out), "--clusters", "2", "--embedding", "3",
                             "--bins", "4", "--epochs", "12", "--seed", "12", *extra],
                            text=True, capture_output=True)
    return result, out


def load_module():
    spec = importlib.util.spec_from_file_location("lsst_trainer", TRAINER)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_independent_models_and_standalone_embedding(tmp_path):
    rows = [object_row(kind, i) for kind in ("dia", "ss") for i in range(9)]
    result, out = run(tmp_path, rows)
    assert result.returncode == 0, result.stderr
    for kind in ("dia", "ss"):
        model = json.loads((out / f"{kind}.json").read_text())
        assert model["format"] == "lomikel-lsst-lc-nn-kmeans-v1"
        assert model["kind"] == kind and model["bands"] == ["u", "g", "r", "i", "z", "y"]
        assert model["bins"] == 4 and len(model["feature_mean"]) == 74
        assert len(model["feature_scale"]) == 74
        assert len(model["encoder_weights"]) == 74
        assert all(len(w) == 3 for w in model["encoder_weights"])
        assert len(model["encoder_bias"]) == 3
        assert len(model["centers"]) == len(model["radii"]) == 2
        assert model["n_train"] + model["n_val"] == 9
        assert model["provenance"]["table"] == rows[0 if kind == "dia" else 9]["table"]
        assert model["model_id"]
        feature = load_module().encode_features(rows[0 if kind == "dia" else 9], model["bins"])
        x = (feature - model["feature_mean"]) / model["feature_scale"]
        z = np.tanh(x @ np.array(model["encoder_weights"]) + model["encoder_bias"])
        assert np.isfinite(z).all()
        assert math.isfinite(model["train_loss"]) and math.isfinite(model["val_loss"])
    assert not (out / "both.json").exists()


def test_even_length_extreme_flux_median_remains_finite():
    row = object_row("dia", 0)
    row["sources"] = [dict(row["sources"][i], flux="1e308", mjd=str(i + 1)) for i in range(4)]
    vector = load_module().encode_features(row, 2)
    assert np.isfinite(vector).all()
    assert vector[-1] == pytest.approx(math.log1p(1e308) / 10)


def test_even_length_extreme_flux_median_matches_groovy(tmp_path):
    row = object_row("dia", 0)
    row["sources"] = [dict(row["sources"][i], flux="1e308", mjd=str(i + 1)) for i in range(4)]
    row_path = tmp_path / "large.json"
    row_path.write_text(json.dumps(row))
    script = ("def C = new GroovyShell(new Binding(LSST_LC_TEST:true)).evaluate(new File(" +
              json.dumps(str(CLASSIFIER)) + ")); " +
              "def row = new groovy.json.JsonSlurper().parse(new File(" +
              json.dumps(str(row_path)) + ")); " +
              "println groovy.json.JsonOutput.toJson(C.features(row,2)[0])")
    result = subprocess.run([GROOVY, "-e", script], text=True, capture_output=True)
    assert result.returncode == 0, result.stderr
    expected = load_module().encode_features(row, 2)
    assert np.array(json.loads(result.stdout)) == pytest.approx(expected, abs=1e-12)


def test_feature_order_mask_and_sorted_equal_weight_aggregation():
    trainer = load_module()
    row = object_row("dia", 0, ("g",))
    row["sources"] = [
        {"row_key": row["object_key"] + "3", "source_id": "3", "mjd": "3", "band": "g", "flux": "-4", "flux_err": "2"},
        {"row_key": row["object_key"] + "1", "source_id": "1", "mjd": "1", "band": "g", "flux": "2", "flux_err": "1"},
        {"row_key": row["object_key"] + "2", "source_id": "2", "mjd": "1", "band": "g", "flux": "4", "flux_err": "3"},
    ]
    f = trainer.encode_features(row, 2)
    assert len(f) == 38
    # scale=median(abs([2,4,-4]))=4, first bin g averages two points.
    assert f[2] == pytest.approx((np.arcsinh(.5) + np.arcsinh(1)) / 2)
    assert f[14] == pytest.approx((np.log1p(.25) + np.log1p(.75)) / 2)
    assert f[26] == 1 and f[27] == 1
    assert sum(f[24:36]) == 2
    assert f[-2] == pytest.approx(np.log1p(2) / 5)
    assert f[-1] == pytest.approx(np.log1p(4) / 10)


def test_deterministic_outputs_and_missing_bands(tmp_path):
    rows = [object_row("dia", i, ("r",)) for i in range(8)]
    first, out = run(tmp_path, rows, "--kind", "dia")
    assert first.returncode == 0, first.stderr
    snapshot = (out / "dia.json").read_bytes()
    second, _ = run(tmp_path, rows, "--kind", "dia")
    assert second.returncode == 0, second.stderr
    assert (out / "dia.json").read_bytes() == snapshot
    model = json.loads(snapshot)
    assert all(v == 0 for v in load_module().encode_features(rows[0], 4)[48:56])
    assert all(math.isfinite(v) and v > 0 for v in model["feature_scale"])
    assert not (out / "ss.json").exists()


def test_model_digest(tmp_path):
    rows = [object_row("ss", i) for i in range(8)]
    result, out = run(tmp_path, rows, "--kind", "ss")
    assert result.returncode == 0, result.stderr
    model = json.loads((out / "ss.json").read_text())
    digest = model["model_id"]
    trainer = load_module()
    assert digest == trainer.model_digest(model)
    assert len(digest) == 64 and all(c in "0123456789abcdef" for c in digest)
    for field, changed in (("encoder_weights", 0.125), ("centers", 0.25),
                           ("radii", 0.5), ("feature_mean", 0.75)):
        altered = json.loads(json.dumps(model))
        if field in ("encoder_weights", "centers"):
            altered[field][0][0] += changed
        else:
            altered[field][0] += changed
        assert trainer.model_digest(altered) != digest
    altered = json.loads(json.dumps(model))
    altered["provenance"]["schema"] = "changed"
    assert trainer.model_digest(altered) != digest


def test_python_model_and_extreme_features_match_groovy(tmp_path):
    rows = [object_row("dia", i) for i in range(8)]
    result, out = run(tmp_path, rows, "--kind", "dia")
    assert result.returncode == 0, result.stderr
    model_path = out / "dia.json"
    model = json.loads(model_path.read_text())
    row = {"kind": "dia", "object_id": "123", "object_key": "000_123_",
           "table": "rubin.diaSource_static", "schema": model["provenance"]["schema"],
           "complete": True, "sources": [
               {"row_key": f"000_123_{i}", "source_id": str(i), "mjd": str(i),
                "band": band, "flux": flux, "flux_err": "1"}
               for i, (band, flux) in enumerate(zip("ugr", ("-1e308", "1", "1")), 1)]}
    row_path = tmp_path / "row.json"
    row_path.write_text(json.dumps(row))
    script = ("def C = new GroovyShell(new Binding(LSST_LC_TEST:true)).evaluate(new File(" +
              json.dumps(str(CLASSIFIER)) + ")); " +
              "def sl = new groovy.json.JsonSlurper(); " +
              "def m = sl.parse(new File(" + json.dumps(str(model_path)) + ")); " +
              "def r = sl.parse(new File(" + json.dumps(str(row_path)) + ")); " +
              "C.validateModel(m); println C.modelDigest(m); " +
              "println groovy.json.JsonOutput.toJson(C.features(r,2)[0]); " +
              "['encoder_weights','centers','radii','provenance'].each { field -> " +
              "def changed = sl.parse(new File(" + json.dumps(str(model_path)) + ")); " +
              "if (field == 'encoder_weights') changed.encoder_weights[0][0] += 0.1; " +
              "if (field == 'centers') changed.centers[0][0] += 0.1; " +
              "if (field == 'radii') changed.radii[0] += 0.1; " +
              "if (field == 'provenance') changed.provenance.schema = 'changed'; " +
              "try { C.validateModel(changed); throw new AssertionError('accepted '+field) } " +
              "catch (IllegalArgumentException expected) {} }")
    checked = subprocess.run([GROOVY, "-e", script], text=True, capture_output=True)
    assert checked.returncode == 0, checked.stderr
    digest, vector = checked.stdout.strip().splitlines()
    assert digest == model["model_id"] == load_module().model_digest(model)
    expected = load_module().encode_features(row, 2)
    assert np.array(json.loads(vector)) == pytest.approx(expected, abs=1e-12)


@pytest.mark.parametrize("change", [
    lambda row: row.update(object_id="not-a-number"),
    lambda row: (row.update(object_key="export/123_"),
                 [source.update(row_key="export/123_" + str(i))
                  for i, source in enumerate(row["sources"])]),
    lambda row: row["sources"][0].update(source_id="not-a-number"),
])
def test_rejects_object_identity_not_supported_by_groovy(tmp_path, change):
    rows = [object_row("dia", i) for i in range(8)]
    change(rows[0])
    result, out = run(tmp_path, rows, "--kind", "dia")
    assert result.returncode != 0
    assert "traceback" not in result.stderr.lower()
    assert not (out / "dia.json").exists()


def test_duplicate_json_keys_rejected(tmp_path):
    source = tmp_path / "objects.jsonl"
    source.write_text('{"kind":"dia","kind":"ss"}\n')
    result = subprocess.run([sys.executable, str(TRAINER), "--input", str(source),
                             "--output-dir", str(tmp_path / "models"), "--clusters", "2"],
                            capture_output=True, text=True)
    assert result.returncode != 0 and "duplicate json key" in result.stderr.lower()


def test_train_only_normalization_and_reconstruction(tmp_path):
    rows = [object_row("dia", i) for i in range(20)]
    result, out = run(tmp_path, rows, "--kind", "dia", "--epochs", "80")
    assert result.returncode == 0, result.stderr
    model = json.loads((out / "dia.json").read_text())
    matrix = np.stack([load_module().encode_features(row, 4) for row in rows])
    order = np.random.default_rng(12).permutation(len(rows))
    train = order[model["n_val"]:]
    assert np.array(model["feature_mean"]) == pytest.approx(matrix[train].mean(axis=0))
    assert np.array(model["feature_scale"]) == pytest.approx(
        np.where(matrix[train].std(axis=0) == 0, 1, matrix[train].std(axis=0)))
    baseline = np.sum(matrix[train, :24] ** 2 * matrix[train, 48:72]) / np.sum(matrix[train, 48:72])
    assert model["train_loss"] < baseline


def test_rejects_collapsed_embedding_instead_of_empty_center(tmp_path):
    rows = [object_row("dia", i) for i in range(8)]
    for row in rows:
        for j, source in enumerate(row["sources"]):
            source["mjd"] = str(60000 + j)
            source["flux"] = str((j % 3 - 1) + j * .3)
    result, out = run(tmp_path, rows, "--kind", "dia")
    assert result.returncode != 0
    assert "distinct training embeddings" in result.stderr.lower()
    assert "traceback" not in result.stderr.lower()
    assert not (out / "dia.json").exists()


@pytest.mark.parametrize("mutation,needle", [
    (lambda r: r.update(complete=False), "complete"),
    (lambda r: r.update(table="rubin.diaSource_sso"), "table"),
    (lambda r: r.update(object_key="wrong_"), "object_key"),
    (lambda r: r["sources"][1].update(source_id=r["sources"][0]["source_id"]), "duplicate"),
    (lambda r: r["sources"][1].update(row_key=r["sources"][0]["row_key"]), "duplicate"),
    (lambda r: r["sources"][1].update(flux="NaN"), "finite"),
    (lambda r: r["sources"][1].update(flux_err="0"), "positive"),
    (lambda r: r["sources"][1].update(band="R"), "band"),
    (lambda r: r["sources"].__setitem__(slice(2, None), []), "observations"),
    (lambda r: [s.update(mjd="1") for s in r["sources"]], "span"),
    (lambda r: r["sources"].extend([dict(r["sources"][0], row_key=f"x{k}", source_id=f"x{k}") for k in range(512)]), "512"),
])
def test_invalid_records_fail_without_output(tmp_path, mutation, needle):
    rows = [object_row("dia", i) for i in range(8)]
    mutation(rows[0])
    result, out = run(tmp_path, rows, "--kind", "dia")
    assert result.returncode != 0
    assert needle in result.stderr.lower()
    assert not (out / "dia.json").exists()


def test_duplicate_object_and_bounds_fail(tmp_path):
    rows = [object_row("dia", i) for i in range(8)]
    for bad_rows, flags, needle in ((rows + [rows[0]], (), "duplicate"),
                                   (rows, ("--max-objects", "7"), "exceeds"),
                                   (rows, ("--embedding", "0"), "positive"),
                                   (rows, ("--kind", "both"), "ss")):
        result, out = run(tmp_path, bad_rows, *flags)
        assert result.returncode != 0
        assert needle in result.stderr.lower()
        assert not (out / "dia.json").exists()

# LSST alert-property PCA + K-means (DIA and SS)

These scripts fit **separate**, unlabeled DIA and SS models from *individual alert rows*, then assign a new alert to its nearest cluster center in PCA space. A cluster ID is not an astrophysical class or a calibrated classification probability. The returned `outlier` flag means its distance exceeds the 99th-percentile training radius for that center; it is especially unreliable with small clusters. Do not promote smoke-test models to production.

## Inputs and feature policy

`export_alerts.groovy` performs **read-only**, explicitly bounded HBase access to `rubin.diaSource_static` (DIA) or `rubin.diaSource_sso` (SS), selecting numeric `r:` alert-level measurements from a strict allowlist. It excludes positions, coordinates, IDs, times, history/light curves, classifier outputs (`f:clf_*`), crossmatches (`f:xm_*`) and unknown fields. Keep the allowlists in the exporter, Python trainer and Groovy classifier aligned if changing them. Each JSONL row contains `kind`, `row_key`, `table`, `schema`, and `cells` (numeric strings keyed by `r:<name>`). The trainer also accepts older JSONL missing `table`/`schema` and assumes the verified LSST tables and `schema_5.0rc0_8.52.0`; prefer provenance-bearing exports.

The exporter **requires** explicit `--keys` (exact row keys) *or* a **known, narrow** `--prefix`, as well as an explicit `--limit`; both client result and search limits are set. It verifies exact-key completeness and makes an output file visible only after a fully successful read (atomic replacement). The current Lomikel `HBaseClient` source has the `handleScanIOException` hook used by the exporter's strict subclass; **rebuild Lomikel from this branch** before running the exporter. Older prebuilt executable JARs without that hook fail compilation of the exporter rather than silently accepting partial scans. A limited prefix scan is still potentially biased and should not be treated as a representative sample. Repeating a prefix export with different row limits is not independent sampling. Do not use a broad prefix or an unbounded scan on production HBase. Concatenate carefully chosen, disjoint JSONL partitions only after checking sampling design; repeated `(kind,row_key)` is rejected.

## Example usage

From Lomikel's `ant/` directory after sourcing its setup (it defines `version`):

```bash
source setup.sh
java -jar "../lib/Lomikel-HBase-${version}.exe.jar" -b -q \
  -s ../src/work/Clusters/LSST/export_alerts.groovy \
  -o "host='cchbase1.in2p3.fr',port=2183,table='rubin.diaSource_static',schema='schema_5.0rc0_8.52.0',kind='dia',prefix='KNOWN_NARROW_PREFIX',limit=100,output='../run/agent/lsst-dia.jsonl'"
java -jar "../lib/Lomikel-HBase-${version}.exe.jar" -b -q \
  -s ../src/work/Clusters/LSST/export_alerts.groovy \
  -o "host='cchbase1.in2p3.fr',port=2183,table='rubin.diaSource_sso',schema='schema_5.0rc0_8.52.0',kind='ss',prefix='KNOWN_NARROW_PREFIX',limit=100,output='../run/agent/lsst-ss.jsonl'"
```

Replace `KNOWN_NARROW_PREFIX` with one you have established safely; alternatively pass `keys='KEY1,KEY2'` and a limit at least as large as the key list. Put large inputs and model directories on `../run`. Combine DIA and SS JSONL without changing their `kind` or provenance fields, then fit one model per kind:

```bash
cat ../run/agent/lsst-dia.jsonl ../run/agent/lsst-ss.jsonl > ../run/agent/lsst-both.jsonl
python3 ../src/work/Clusters/LSST/train_clusters.py \
  --input ../run/agent/lsst-both.jsonl --output-dir ../run/agent/lsst-models \
  --kind both --clusters 10 --components 0.95 --seed 42 --max-rows 100000
```

Python requires NumPy and scikit-learn. `--components` accepts an integer count or an explained-variance target strictly between 0 and 1. Each kind must have enough rows for the requested `--clusters` and PCA dimensions. The trainer selects eligible columns present in at least 80% of a kind's rows, sorts them, median-imputes missing values, subtracts training means, divides by training standard deviations (constant columns use scale 1), fits PCA, then seeded K-means. It writes `dia.json` and `ss.json` with model format `lomikel-lsst-pca-kmeans-v1`: ordered features, imputation/scaling/PCA parameters, centers and per-cluster radii, counts and provenance. `--max-rows` is a hard input cap, not a sampling strategy.

For an **exact new HBase row** (read-only, projected to the model's feature columns), run from `ant/`:

```bash
java -jar "../lib/Lomikel-HBase-${version}.exe.jar" -b -q \
  -s ../src/work/Clusters/LSST/classify_alert.groovy \
  -o "model='../run/agent/lsst-models/dia.json',host='cchbase1.in2p3.fr',port=2183,table='rubin.diaSource_static',schema='schema_5.0rc0_8.52.0',kind='dia',rowKey='EXACT_ROW_KEY'"
```

Use the SS model and table for an SS row. The classifier refuses mismatched `kind`, table or schema when the model has provenance. For an already exported single row in a JSON file, `groovy classify_alert.groovy --model /path/dia.json --row-json /path/single-row.json` uses identical projection logic without HBase. Output contains `kind`, `row_key`, `class_id` (zero-based), PCA-space Euclidean `distance`, and `outlier`.

## Verification and limitations

Offline tests: `python3 -m pytest src/test/lsst_alert_clusters/test_train.py -q` and `groovy src/test/lsst_alert_clusters/test_groovy.groovy` from the repository root. The integration smoke used *only 25 rows per kind*, a narrow prefix, three clusters, and compared Groovy JSON-row inference with exact-key live HBase inference for one training row per kind. This checks plumbing and numerical agreement, **not predictive validity**, sample representativeness, optimal K, separation, stability, or astrophysical interpretation. Before using classes operationally, design a representative time/object-stratified training sample, hold out objects/time spans, evaluate cluster stability and meaningful associations, and calibrate outlier behavior. Model files from the smoke are deliberately kept outside the repository.

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

Python requires NumPy and scikit-learn. On success, each exporter reports the row count and output path; the trainer reports one line per fitted kind with its sample count, model dimensions and model path. These summaries are console output, not extra JSONL records or model fields. `--components` accepts an integer count or an explained-variance target strictly between 0 and 1. Each kind must have enough rows for the requested `--clusters` and PCA dimensions. The trainer selects eligible columns present in at least 80% of a kind's rows, sorts them, median-imputes missing values, subtracts training means, divides by training standard deviations (constant columns use scale 1), fits PCA, then seeded K-means. It writes `dia.json` and `ss.json` with model format `lomikel-lsst-pca-kmeans-v1`: ordered features, imputation/scaling/PCA parameters, centers and per-cluster radii, counts and provenance. `--max-rows` is a hard input cap, not a sampling strategy.

For an **exact new HBase row** (read-only, projected to the model's feature columns), run from `ant/`:

```bash
java -jar "../lib/Lomikel-HBase-${version}.exe.jar" -b -q \
  -s ../src/work/Clusters/LSST/classify_alert.groovy \
  -o "model='../run/agent/lsst-models/dia.json',host='cchbase1.in2p3.fr',port=2183,table='rubin.diaSource_static',schema='schema_5.0rc0_8.52.0',kind='dia',rowKey='EXACT_ROW_KEY'"
```

Use the SS model and table for an SS row. The classifier refuses mismatched `kind`, table or schema when the model has provenance. For an already exported single row in a JSON file, `groovy classify_alert.groovy --model /path/dia.json --row-json /path/single-row.json` uses identical projection logic without HBase. Output contains `kind`, `row_key`, `class_id` (zero-based), PCA-space Euclidean `distance`, and `outlier`.

## Held-out association against read-only graph tag snapshots

From `ant/`, first export feature-only alerts with `export_alerts.groovy` as above, then produce a **separate exact alert-identity map** for the same rows. The map must contain exactly one `kind,row_key,object_id,mjd,source_id` record per alert in the default instance policy; `source_id` must be a decimal string, and `mjd` is the source's `r:midpointMjdTai`. The graph has no source ID, so matching uses verified canonical object ID plus a unique MJD within `1e-6` day—not an independent graph source-ID comparison. Duplicate source IDs in the sampled HBase input and ambiguous MJD matches fail closed. Keep these as provenance/identity data, not model features or labels. SS row-key aliases are **not** canonical object IDs. The identity exporter reads the exact requested HBase rows with bounded batches and writes the map. For DIA data at `../run/agent/lsst-dia.jsonl`:

```bash
java -jar "../lib/Lomikel-HBase-${version}.exe.jar" -b -q \
  -s ../src/work/Clusters/LSST/export_alert_identity.groovy \
  -o "host='cchbase1.in2p3.fr',port=2183,table='rubin.diaSource_static',schema='schema_5.0rc0_8.52.0',kind='dia',input='../run/agent/lsst-dia.jsonl',output='../run/agent/lsst-dia-identity.jsonl',maxRows=10000,batchSize=50"
python3 - <<'PY'
import json
from pathlib import Path
p = Path('../run/agent/lsst-dia-identity.jsonl')
ids = sorted({json.loads(line)['object_id'] for line in p.open(encoding='utf-8')})
assert ids and all(isinstance(oid, str) and oid.isdecimal() for oid in ids)
Path('../run/agent/lsst-dia-object-ids.txt').write_text('\n'.join(ids) + '\n', encoding='utf-8')
print(len(ids), 'distinct canonical IDs')
PY
python3 ../src/work/Clusters/LSST/export_graph_labels.py \
  --input ../run/agent/lsst-dia-object-ids.txt \
  --output ../run/agent/lsst-dia-graph-labels.jsonl \
  --endpoint https://GRAPH_HOST:GRAPH_PORT \
  --classifier FINK --survey LSST --flavor '' \
  --max-objects 10000 --batch-size 50
python3 ../src/work/Clusters/LSST/evaluate_physical.py \
  --mode pca --kind dia --input ../run/agent/lsst-dia.jsonl \
  --row-map ../run/agent/lsst-dia-identity.jsonl \
  --labels ../run/agent/lsst-dia-graph-labels.jsonl \
  --output-dir ../run/agent/lsst-dia-evaluation \
  --clusters 3 --components 0.95 --seed 42 --holdout 0.2 \
  --cohort labeled --pca-label-policy instance
```

`--pca-label-policy instance` is the PCA default and is shown explicitly here to make the scientific assumption visible. The native graph export records OCol object memberships with `instances` (edge MJDs) and corresponding `weights` from `deepcontains` edges. Those edge instances represent the **subset of observations registered from `rubin.tag_*` tables**, not every HBase alert of the object. In instance mode the evaluator uses canonical object identity plus the identity-map MJD/source identity to associate only registered alert instances with tags; per-instance weights matter. It restricts PCA training and held-out association to matched, labeled observations in the labeled object cohort; unregistered alerts from those objects are not silently relabeled. Objects, not rows, are split before any model fit, and labels and instance metadata remain outside the feature matrix and fitted model. A native graph snapshot with usable instance metadata is needed for this source-level interpretation; a legacy flat/object-only tag export cannot establish observation matches.

For an **explicit legacy comparison only**, replace the last option with `--pca-label-policy object`. That policy propagates each OCol object tag to *every* exported alert of the canonical object, regardless of edge-instance MJDs. It answers a different object-membership question and can greatly overstate alert-level label coverage; do not compare its alert counts or contingency mass to instance-mode results as if the labeled units were the same. The light-curve `--mode lc` evaluation is object-level and does not use this PCA-only switch.

Replace the graph endpoint with a trusted reachable service. The default is loopback `http://127.0.0.1:24444`; remote HTTP is refused unless you explicitly pass both `--endpoint http://GRAPH_HOST:GRAPH_PORT --allow-insecure-http`. That flag permits plaintext responses vulnerable to network tampering: prefer HTTPS or a protected loopback tunnel. Redirects are refused, including HTTPS-to-HTTP redirects. Outputs must be fresh paths. Ensure at least three distinct canonical objects and enough *training* rows after object holdout for the selected cluster count and PCA dimensions; `--clusters 3` is illustrative, not a scientifically justified choice. Use `--kind ss` and corresponding SS exports for SS; for `--kind both`, concatenate disjoint DIA/SS feature and mapping files, and only use native graph labels when each canonical object ID occurs under exactly one input kind. The graph snapshot has no `kind`, so an ambiguous ID in both kinds is rejected. Do not train on or merge the nested labels into the feature JSONL: splitting is by canonical object and all model fitting precedes held-out label association.

The evaluator consumes native `lomikel-lsst-ocol-known-ids-v1` JSONL directly and requires the FINK/LSST/empty-flavor scope. Native snapshots must contain exactly one graph record for **every** input canonical object, including objects with empty `classes`; missing coverage fails before fitting. It preserves graph provenance and edge `instances`, `weights`, IDs and labels in the held-out report (never in the model). The default `--cohort labeled` excludes objects without graph classes before object-grouped splitting; `--cohort all` is a distinct sparse-label exploration, not a way to fill missing instance labels. Under the default PCA instance policy, the effective source-level training/evaluation cohort is narrower still: only successfully matched registered observations contribute. Inspect coverage and support before interpreting any object- or alert-weighted contingency view or CSV. Legacy flattened labels are appropriate only to the explicit object-propagation policy because they do not encode edge instances. These are broker tag associations, **not independent physical truth or a calibrated accuracy estimate**. The graph exporter fetches only the supplied *known IDs* and reads in nontransactional batches; graph `rubin.tag_*` registrations may be incomplete or delayed relative to HBase. Neither this export nor a bounded HBase sample establishes all-object or all-source coverage, representativeness, or a consistent global snapshot. No graph or HBase writes occur in this evaluation workflow.

Offline tests from `ant/`: `python3 -m pytest --import-mode=importlib ../src/test/lsst_physical_eval/test_evaluate.py ../src/test/lsst_graph_labels/test_export_graph_labels.py -q` with NumPy, scikit-learn and pytest installed.

## Re-train and evaluate against configurable LSST tag classes

`tag_classes.json` contains the full list of learning classes, the HBase
endpoint, sampling caps, measured features, PCA/K grid and evaluation split.
Change the `classes` array to add/remove `rubin.tag_*` tables, then re-run the
wrapper. The default seven classes are early SN Ia candidate, extragalactic
<20 mag candidate, extragalactic new candidate, extragalactic SVOM, M-dwarf
flare, most likely SN and SN near galaxy candidate. Empty tables are reported
and omitted from fitting without changing the config. A `rubin.tag_*` entry is
a **Fink filter tag on a source alert**, not an independent astrophysical
ground-truth classification. Alerts may have several tags at once.

The exporter reads all keys of each configured tag table, samples
**objects** in seeded hash order (no more than 3 alerts per object and 4000
per class by default), and fetches exactly those rows from the canonical
`rubin.diaSource_static` alert table. It checks `r:diaObjectId` and
`r:midpointMjdTai` against the tag key. Only allowlisted measured `r:`
columns and `r:band` enter `alerts.jsonl`. A few sampled tagged alerts
(about 10% of the SN-near-galaxy sample, from night MJD 61094) have no row in
`rubin.diaSource_static`; with `sampling.fallback_to_tag_row` (default) their
cells are read from the tag row, a byte copy of the alert row, after the same
identity checks, and `summary.json` counts them per class. Anything still
missing is counted per class and fails the export above
`sampling.max_missing_fraction` (5%). The per-object cap applies within each
class, so an object tagged by several classes can contribute more alerts in
total; identities and all tag labels go
into a separate `labels.jsonl`. There are no HBase or JanusGraph writes.
The exporter records table sizes, missing alert rows, schema names and
per-class sample sizes in `summary.json`; it fails before publishing a partial
export on unexpected keys or inconsistent identity. The scan cap is explicit:
if a table exceeds `sampling.max_scan_keys_per_class`, raise it intentionally
rather than silently truncating. The fetch cap is
`sampling.max_alerts_per_class` *per class*.

From the repo root, after `ant war` has populated `lib/`:

```bash
PYTHON=/path/to/venv/bin/python \
  src/work/Clusters/LSST/run_tag_evaluation.sh \
  src/work/Clusters/LSST/tag_classes.json run/lsst-tag-eval/20261113
```

Python needs NumPy and scikit-learn; matplotlib is optional for PNG plots.
For a rerun of the evaluator without another HBase scan, set `EXPORT` to an
existing exported directory and choose a **fresh** output directory:

```bash
EXPORT=run/lsst-tag-eval/20261113/export PYTHON=/path/to/venv/bin/python \
  src/work/Clusters/LSST/run_tag_evaluation.sh \
  src/work/Clusters/LSST/tag_classes.json run/lsst-tag-eval/other-features
```

The output contains `export/{alerts.jsonl,labels.jsonl,summary.json}` and
`eval/{report.md,metrics.json,model.json,test_per_class.csv,cluster_profiles.csv,
tag_overlap.csv,test_contingency.csv}` and figures if matplotlib is installed.
No input or model is committed. Do **not** change `classes` when reusing an
export to claim a different dataset: the model can filter already-exported
alerts, but it cannot resurrect a class that was never scanned.

The evaluator separates objects into stratified train/validation/test sets
(no object can leak across splits). It fits all transforms on training only:
configured measurements (80% presence by default), signed `asinh`
compression, median imputation, scaling, optional one-hot bands, and PCA.
A seeded K-means grid is selected by validation macro F1, then the winner is
refitted on train+validation and tested only once. Clusters carry the training
fractions of each tag. Top-1 is right if the predicted tag belongs to the
alert's tag set. Reported per-tag ranking metrics are ROC AUC and average
precision; `lift` scores instead of raw fractions are optional in the config
but are not calibrated probabilities. The class-prior baseline and supervised
random forest reference are evaluated on the **same held-out objects**. The
reference is for context; it is not used to tune the PCA+K-means model.

`model.json` has format `lomikel-lsst-pca-kmeans-tags-v1` and is intentionally
**separate** from the older unsupervised `lomikel-lsst-pca-kmeans-v1` format
consumed by `classify_alert.groovy`. To run the learned tag-profile model on
exported DIA alerts:

```bash
python3 src/work/Clusters/LSST/evaluate_tags.py predict \
  --model run/lsst-tag-eval/20261113/eval/model.json \
  --input run/lsst-tag-eval/20261113/export/alerts.jsonl \
  --output run/lsst-tag-eval/20261113/predictions.jsonl
```

The training sample is deliberately **class-balanced and object-capped**.
Thus tag fractions in clusters are associations **in this sampling design**,
not real-world class probabilities. The test score estimates how well this
same design generalises to previously unseen objects, *not* performance on
untagged live-stream alerts. For operational use, also evaluate an unbiased
representative stream and a later time window, and validate independent
spectroscopic classifications. An unchanged tag table can still grow with
future data; rerun the export to update the snapshot and model.

Offline tests:

```bash
python3 -m pytest -q --import-mode=importlib -p no:cacheprovider \
  src/test/lsst_tag_eval/test_evaluate_tags.py
java -cp "$(ls lib/*.jar | tr '\n' ':')" groovy.ui.GroovyMain \
  src/test/lsst_tag_eval/test_export_groovy.groovy
```

## Verification and limitations

Offline tests: `python3 -m pytest src/test/lsst_alert_clusters/test_train.py -q` and `groovy src/test/lsst_alert_clusters/test_groovy.groovy` from the repository root. The integration smoke used *only 25 rows per kind*, a narrow prefix, three clusters, and compared Groovy JSON-row inference with exact-key live HBase inference for one training row per kind. This checks plumbing and numerical agreement, **not predictive validity**, sample representativeness, optimal K, separation, stability, or astrophysical interpretation. Before using classes operationally, design a representative time/object-stratified training sample, hold out objects/time spans, evaluate cluster stability and meaningful associations, and calibrate outlier behavior. Model files from the smoke are deliberately kept outside the repository.

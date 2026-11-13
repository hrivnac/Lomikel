# LSST six-band light-curve neural-network clusters (DIA and SS)

This is an **exploratory, unlabeled** counterpart to `src/work/Clusters/LSST/`: Groovy exports one object's HBase observations, Python trains a masked neural autoencoder separately for DIA and SS, K-means identifies centers in the encoder's latent space, and Groovy assigns a new object to the nearest center. The `class_id` is a zero-based **data-driven cluster ID**, not an astrophysical class or a probability. The returned `outlier` compares distance with the 99th-percentile radius of that center in the training set. No JanusGraph writes are performed. The model format and `model_id` are intended to identify a *future*, separately reviewed graph classification schema.

## Data contract and scope

Both scripts read individual HBase rows from `rubin.diaSource_static` (DIA) or `rubin.diaSource_sso` (SS), schema `schema_5.0rc0_8.52.0`. A row's `r:band`, `r:midpointMjdTai`, `r:psfFlux`, and `r:psfFluxErr` are projected, as well as the canonical `r:diaObjectId` or `r:ssObjectId` and source ID `r:diaSourceId`. The row key's middle component is **not** assumed to equal the SS object ID. The `psfFlux` series is used consistently across both types; science-image flux is not mixed with difference-image PSF flux. Negative but finite flux is retained. Invalid or missing flux, error, band, or identity causes the requested object export to fail rather than silently dropping observations.

Provide **verified full object-key prefixes** ending in `_`, for example `000_170028485649105000_` (DIA) or `00_K00A00G_` (SS). These are specific observed examples. Explicit-prefix mode still rejects broad `prefixes='00'` and substring object-ID scans. Alternatively use **automatic bounded discovery** with `candidatePrefix='00'`, `candidateRows` (at most 5000), `minPoints` (at least 3 and at most `maxPoints`) and `maxObjects` (the exact desired output count, at most 100). The discovery scan projects only DIA/SS identity columns, sets both scanner limits to `candidateRows+1`, takes at most that many returned rows and at most the first 100 distinct full object prefixes, then scans each complete object with the normal `maxPoints+1` limits and validation. It ranks eligible distinct canonical IDs by valid source count descending, with object ID as deterministic tie-break, and publishes **nothing** unless the requested count is met. Validation failures (short, malformed, capped, duplicate canonical IDs) are counted on stderr; scanner I/O failures abort rather than skip. Only a bounded lexicographic sample under the supplied prefix is considered: this is *not* an unbiased or global top-N search, and a broader prefix may yield insufficient distinct objects within the row cap. Try a different two-digit candidate prefix or adjust bounded limits; do not use unbounded scans. At most 100 objects and 512 observations per object are allowed per run. The exporter projects only required columns, rejects capped/mismatched object results, handles scanner I/O failures strictly, closes the client and atomically publishes output. A prefix scan is **prefix-complete at the time of that read**, not proven globally complete across unknown alternative keys, ingestion lag, or future observations. The JSON field `complete: true` means the bounded prefix was not truncated; do not interpret it as a permanent complete light curve.

Each JSONL record has `kind`, canonical numeric-string `object_id`, `object_key` (requested prefix), `table`, `schema`, `complete: true`, and `sources` sorted by MJD/source ID. Both trainer and classifier require numeric-string source IDs; DIA keys have the form `DDD_OBJECTID_` (three digits, then the matching numeric object ID) and SS keys `DD_ALIAS_` (two digits, then an alphanumeric alias), with an underscore terminator. Each source retains string `row_key`, `source_id`, `mjd`, `flux`, `flux_err`, and one of the six literal bands `u,g,r,i,z,y`. A missing band contributes no invented observations. The trainer rejects duplicate objects/sources, mixed schemas, nonfinite values, nonpositive errors, fewer than three observations, zero time span, and more than 512 sources per object. It fits one model per kind, never pools DIA and SS.

## Running from Lomikel `ant/`

Build Lomikel from current source first. An older executable JAR without the protected `HBaseClient.handleScanIOException` hook fails explicitly when compiling these scripts. In the current source tree, source `setup.sh` to obtain `version`, then export known objects using the HBase-enabled executable JAR:

```bash
source setup.sh
java -jar "../lib/Lomikel-HBase-${version}.exe.jar" -b -q \
  -s ../src/work/LightCurves/LSST/export_lightcurves.groovy \
  -o "host='cchbase1.in2p3.fr',port=2183,table='rubin.diaSource_static',schema='schema_5.0rc0_8.52.0',kind='dia',prefixes='000_170028485649105000_',maxObjects=1,maxPoints=512,output='../run/agent/lc-dia.jsonl'"
java -jar "../lib/Lomikel-HBase-${version}.exe.jar" -b -q \
  -s ../src/work/LightCurves/LSST/export_lightcurves.groovy \
  -o "host='cchbase1.in2p3.fr',port=2183,table='rubin.diaSource_sso',schema='schema_5.0rc0_8.52.0',kind='ss',prefixes='00_K00A00G_',maxObjects=1,maxPoints=512,output='../run/agent/lc-ss.jsonl'"
```

For a bounded attempt to obtain at least 12 distinct eligible objects **in each file** (the command fails without replacing an existing file if fewer than 12 qualify), run from `ant/` after building the HBase JAR:

```bash
java -jar "../lib/Lomikel-HBase-${version}.exe.jar" -b -q \
  -s ../src/work/LightCurves/LSST/export_lightcurves.groovy \
  -o "host='cchbase1.in2p3.fr',port=2183,table='rubin.diaSource_static',schema='schema_5.0rc0_8.52.0',kind='dia',candidatePrefix='00',candidateRows=5000,minPoints=3,maxObjects=12,maxPoints=512,output='../run/agent/lc-dia.jsonl'"
java -jar "../lib/Lomikel-HBase-${version}.exe.jar" -b -q \
  -s ../src/work/LightCurves/LSST/export_lightcurves.groovy \
  -o "host='cchbase1.in2p3.fr',port=2183,table='rubin.diaSource_sso',schema='schema_5.0rc0_8.52.0',kind='ss',candidatePrefix='00',candidateRows=5000,minPoints=3,maxObjects=12,maxPoints=512,output='../run/agent/lc-ss.jsonl'"
python3 - <<'PY'
import json
for kind in ('dia', 'ss'):
    records = [json.loads(line) for line in open(f'../run/agent/lc-{kind}.jsonl')]
    ids = [record['object_id'] for record in records]
    #assert len(ids) >= 12 and len(ids) == len(set(ids)), (kind, len(ids), len(set(ids)))
    print(kind, len(ids), 'distinct objects')
PY
```

The example commands are **not evidence that the live database contains 12 eligible objects in that sample**; if they fail, choose another two-digit prefix and rerun, or gather disjoint bounded exports. Twelve per kind may still be inadequate for a 10-cluster training split; count actual post-holdout training objects before choosing clusters. Neither command performs graph writes.

For training, collect a representative set of **distinct objects** of each kind, using bounded exports with disjoint prefixes, and combine the JSONL files. **The following is a template, not runnable from just the two example exports:** ten clusters require substantially more than ten suitable objects of *each* kind after the training/validation split. Do not train on just these two example objects. Python requires NumPy and scikit-learn:

```bash
cat ../run/agent/lc-dia.jsonl ../run/agent/lc-ss.jsonl > ../run/agent/lc-both.jsonl
python3 ../src/work/LightCurves/LSST/train_lightcurves.py \
  --input ../run/agent/lc-both.jsonl --output-dir ../run/agent/lc-models \
  --kind both --clusters 10 --embedding 8 --bins 16 --epochs 100 \
  --seed 42 --max-objects 100000
```

Successful exports report the number of object curves and source observations written; the trainer reports one line per fitted kind with its object counts, model dimensions and output path. These summaries appear on the console, not in the JSONL records or model fields.

For each object, the trainer divides elapsed time by the observed duration and maps observations to fixed bins. For each of six bands and each bin it stores mean `asinh(flux / object_scale)`, mean `log1p(error / object_scale)`, and a presence mask; missing bins stay masked. It appends log-duration and log-flux-scale features. Mean/std normalization is fit **only on training objects**. A masked-reconstruction autoencoder (`tanh` encoder, linear decoder) learns the embedding, using a separate held-out validation-object split for epoch selection. K-means and 99th-percentile distance radii are fit **only on training embeddings**. `dia.json` and `ss.json` serialize encoder parameters, preprocessing, centers/radii, counts, losses, HBase provenance and an immutable hash-based `model_id`. The six-band order and bin layout are versioned in the model. The digest detects accidental parameter changes under the same ID; it is **not** an authenticity signature—someone who can replace the model can recompute its ID. Only load models from a trusted location. This network ignores flux in unobserved bins but does **not** yet use uncertainty-weighted reconstruction loss; `flux_err` is an input feature.

Classify from a single exported **object** JSON record, without HBase:

```bash
/opt/groovy-4.0.9/bin/groovy ../src/work/LightCurves/LSST/classify_lightcurve.groovy \
  --model ../run/agent/lc-models/dia.json --row-json ../run/agent/one-dia-object.json
```

Or use an **exact existing row key** as the anchor to read that object's verified prefix, bounded again. The classifier recomputes the whole object's features; it does not classify that alert in isolation:

```bash
java -jar "../lib/Lomikel-HBase-${version}.exe.jar" -b -q \
  -s ../src/work/LightCurves/LSST/classify_lightcurve.groovy \
  -o "model='../run/agent/lc-models/dia.json',host='cchbase1.in2p3.fr',port=2183,table='rubin.diaSource_static',schema='schema_5.0rc0_8.52.0',kind='dia',rowKey='000_170028485649105000_61090.04758412875',maxPoints=512"
```

For SS, change model, kind, table and anchor key accordingly. Output fields are `kind`, `object_id`, `class_id`, `distance`, `outlier`, `model_id`, `bands_present`, and `source_count`. The classifier checks kind/table/schema provenance and exact anchor membership. It never writes to HBase or JanusGraph. A later graph integration should use a separate light-curve classifier namespace plus the immutable model ID as flavor, and should explicitly decide how to handle sparse data and outliers. Do not interpret its Euclidean distance as a graph membership weight.

## Held-out graph-tag association (object-level, read-only)

For an adequate **distinct-object** light-curve export, use the bounded discovery workflow above. From `ant/`, create a canonical known-ID list and export native graph labels separately; do not append tags to the light-curve JSONL:

```bash
python3 - <<'PY'
import json
from pathlib import Path
rows = Path('../run/agent/lc-dia.jsonl')
ids = sorted({json.loads(line)['object_id'] for line in rows.open(encoding='utf-8')})
assert ids and all(isinstance(oid, str) and oid.isdecimal() for oid in ids)
Path('../run/agent/lc-dia-object-ids.txt').write_text('\n'.join(ids) + '\n', encoding='utf-8')
print(len(ids), 'distinct canonical IDs')
PY
python3 ../src/work/Clusters/LSST/export_graph_labels.py \
  --input ../run/agent/lc-dia-object-ids.txt \
  --output ../run/agent/lc-dia-graph-labels.jsonl \
  --endpoint https://GRAPH_HOST:GRAPH_PORT \
  --classifier FINK --survey LSST --flavor '' \
  --max-objects 10000 --batch-size 50
python3 ../src/work/Clusters/LSST/evaluate_physical.py \
  --mode lc --kind dia --input ../run/agent/lc-dia.jsonl \
  --labels ../run/agent/lc-dia-graph-labels.jsonl \
  --output-dir ../run/agent/lc-dia-evaluation \
  --clusters 3 --embedding 8 --bins 16 --epochs 100 \
  --seed 42 --holdout 0.2 --cohort labeled
```

Replace the graph endpoint with a trusted reachable service. The default is loopback `http://127.0.0.1:24444`; remote HTTP requires both `--endpoint http://GRAPH_HOST:GRAPH_PORT --allow-insecure-http`, explicitly accepting network-tamperable plaintext. Prefer HTTPS or a protected loopback tunnel; redirects (including HTTPS-to-HTTP) are refused. Choose a fresh output directory. The **one-object** example above is not enough: evaluation needs at least three distinct objects, and enough post-holdout training objects for the cluster count plus internal validation. For SS, use `lc-ss.jsonl`, an SS known-ID list and `--kind ss`. `--kind both` requires combined distinct-object input and graph IDs that resolve unambiguously to a single kind. The graph export has no kind field; IDs shared by DIA and SS are ambiguous. The native graph snapshot requires classifier `FINK`, survey `LSST`, flavor `''`, and exactly one record per input canonical object (an empty `classes` list is valid); missing records fail closed. The default `--cohort labeled` splits and trains only objects bearing classes, withholding the actual classes and edge metadata from fitting; `--cohort all` includes unlabeled objects as a separate exploratory mode. Classes/edge metadata are retained in the held-out report but never passed to model fitting. **LC association remains object-level:** a light curve is one object, so its OCol membership applies to that object's curve; edge instance MJDs are metadata, not a per-source filter here. This differs from the alert-row PCA evaluator's default `--pca-label-policy instance`, which only associates registered source observations via the row-map MJD/source identity and uses per-instance weights; its `--pca-label-policy object` explicitly restores legacy tag propagation to all alerts. Do not use the PCA-only switch with `--mode lc`, or infer source-level LC validation from this object-level report. `rubin.tag_*` registrations may cover only a subset of HBase observations. These tags are broker associations, **not ground-truth physical classifications**. The graph exporter covers only supplied known IDs in nontransactional batches, not every graph object or a globally consistent snapshot; the bounded HBase sample is likewise not representative by default. Neither step writes to graph or HBase.

Run `python3 -m pytest --import-mode=importlib ../src/test/lsst_physical_eval/test_evaluate.py ../src/test/lsst_graph_labels/test_export_graph_labels.py -q` from `ant/` with NumPy, scikit-learn and pytest available.

## Re-runnable evaluation against Fink tag classes (DIA)

`run_tag_evaluation.sh` exports tagged DIA light curves from HBase (read-only), then trains and evaluates the light-curve classifier against configurable `rubin.tag_*` classes. It is the light-curve counterpart of `src/work/Clusters/LSST/run_tag_evaluation.sh` and uses the same split and metric code.

All choices are in `tag_classes.json`: the learning classes, the HBase endpoint, sampling caps, curve windows, the bins × embedding × K grid and the evaluation split. To change the classes, edit the `classes` list and re-run; nothing is hard-coded. The default classes are the seven requested tags (early SN Ia candidate, extragalactic <20 mag candidate, extragalactic new candidate, extragalactic SVOM, M-dwarf flare, most likely SN, SN near galaxy candidate). Empty tables are reported and left out.

```bash
PYTHON=/path/to/venv/bin/python \
  src/work/LightCurves/LSST/run_tag_evaluation.sh \
  src/work/LightCurves/LSST/tag_classes.json run/lsst-lc-tag-eval/20261113
# reuse an export with another config (classes must have been scanned by it):
EXPORT=run/lsst-lc-tag-eval/20261113/export PYTHON=... \
  src/work/LightCurves/LSST/run_tag_evaluation.sh my_config.json run/lsst-lc-tag-eval/other
```

Run it from the repository root after `ant exe` has built `lib/Lomikel-HBase-*.exe.jar`. Every run writes a fresh directory: `export/` (`curves.jsonl`, `labels.jsonl`, `summary.json`), `config.json`, logs and `eval/`. The `eval/` directory holds `report.md`, `metrics.json`, per-window CSV tables (per class, test predictions, grid), figures (confusion, K selection, embedding) and `model_<window>.json`. Apply a model to new curves with `evaluate_tags.py predict --model eval/model_full.json --input curves.jsonl --output predictions.jsonl`.

**Export** (`export_tag_lightcurves.groovy`):
- It lists all keys (`<midpointMjdTai>_<diaObjectId>`) of every configured tag table. It samples **objects** in seeded hash order, by default at most 1500 per class.
- For each sampled object it reads the whole prefix `<diaObjectId mod 1000>_<diaObjectId>_` of `rubin.diaSource_static`: `psfFlux`, `psfFluxErr`, `band` and `midpointMjdTai`, with identity checks.
- Curves longer than `max_points` (512, the `train_lightcurves.py` limit), counting observations recovered from tag rows, are excluded and counted, never truncated.
- Tagged alerts that have no static row (for example night MJD 61094) are read from the tag row, a byte copy of the alert row. The same identity checks apply, and duplicates are removed by `diaSourceId`.
- Tagged alerts found in neither place fail the export above `max_missing_fraction`.
- Curves with fewer than `min_points` valid observations are excluded and counted. These are mostly objects of the large "new"/"most likely SN" tags with one or two detections.
- `curves.jsonl` follows the `train_lightcurves.py` contract and carries no labels. Labels go to `labels.jsonl`: an object's classes are **every configured tag carried by any of its alerts**, with first and last tag MJDs.

**Evaluation** (`evaluate_tags.py`):
- Objects are split once into train/validation/test, stratified by the rarest tag, with no object in two splits.
- Two curve windows are evaluated:
  - `full`: all observations.
  - `until_first_tag`: only observations up to the object's first configured tag, i.e. what was known when Fink first tagged it.
- Features are `encode_features`. The model is the masked autoencoder of `train_lightcurves.py` followed by K-means. Each cluster stores the fraction of its training objects carrying each class.
- Bins, embedding and K are chosen on validation. The model is then refitted on train+validation with the selected number of epochs and tested once.
- Reported references are class priors, K-means on the binned features without the encoder, and a supervised random forest on the same features.
- A class with too few objects in some split is not evaluated in that window, and the report says so.
- The `until_first_tag` cutoff comes from the tags themselves (its last observation is the tagging alert). A deployed classifier does not know this cutoff, so this window measures association at tagging time, not a prospective forecast.
- `predict` does not cut curves: a `model_until_first_tag.json` model expects input already cut the same way.
- Two identical runs give identical metrics; the `model_id` can differ because multithreaded K-means centres differ at the 1e-16 level.

Results on CC HBase (2026-11-13):
- 4,171 objects (165,977 observations, 1,526 of them read from tag rows). Excluded: 1,338 short curves and 23 over 512 points. No tagged alert was missing.
- `full` window (test objects): hit rate 0.80, macro F1 0.63. For comparison, the class prior gets 0.10, K-means without the encoder 0.59 and the random forest 0.71.
- `until_first_tag` window: 2,578 objects, macro F1 0.60. Here K-means without the encoder does slightly better (0.62); the random forest gets 0.71.
- Validation selects a single time bin (per-band mean flux, error and coverage plus duration and flux scale, i.e. no curve shape), embedding 128 and K=128–256. Larger K (512, 1024) and more bins score lower.
- The tags are therefore mostly explained by per-band brightness, band coverage and duration, not by detailed curve shape. The autoencoder adds little over clustering the binned features directly.

Tags are Fink filter memberships of single alerts propagated to the object, not physical ground truth. The sample is class-capped, short curves are excluded and HBase tables are read sequentially, so the numbers describe this design on unseen objects, not live-stream performance.

Tests: `python3 -m pytest --import-mode=importlib src/test/lsst_lc_tag_eval -q` and `java -cp "$(ls lib/*.jar | tr '\n' ':')" groovy.ui.GroovyMain src/test/lsst_lc_tag_eval/test_export_groovy.groovy` from the repository root.

## Verification and scientific limitations

Run from the repository root:

```bash
python3 -m pytest --import-mode=importlib src/test/lsst_lightcurves/test_train.py src/test/lsst_alert_clusters/test_train.py -q
groovy src/test/lsst_lightcurves/test_groovy.groovy
```

`--import-mode=importlib` avoids the two independent `test_train.py` modules colliding in one pytest run. The bounded integration smoke exported **one real object of each kind** (148 DIA observations across `g,i,r,z`; eight SS observations across `r,i,z`) and checked Python-versus-Groovy numerical inference and exact-key HBase lookup using models trained primarily on **synthetic fixtures**. That demonstrates plumbing, not a meaningful astrophysical model. A production scientific study must establish complete/selectable object coverage, representative object sampling (not first sorted keys), temporally honest cutoffs, unseen-object validation, cluster stability across seeds and windows, uncertainty/cadence sensitivity, and any post-hoc association with independent labels. Do not add graph classifications based on smoke-test models.

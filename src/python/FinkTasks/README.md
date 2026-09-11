# FinkTasks

Small, reusable Python command-line tasks combining Fink data services. The first task finds LSST objects with classifications similar to a selected object and uses sky separation to break equal classifier-distance results.

## Requirements

- Python 3.10 or newer
- Network access to the Fink LSST services
- Matplotlib 3.7 or newer for PNG light-curve plots (installed automatically with the package)

The standalone `object_neighbors.py` script uses only the Python standard library. The standalone `most_points.py` script also uses only the standard library for ranking; PNG light-curve output additionally requires Matplotlib.

## Install

```bash
python3 -m venv .venv
.venv/bin/python -m pip install -e .
```

## Find classifier neighbours

```bash
fink-object-neighbors 170028486134595648 --allow-insecure-graph
```

Equivalent module invocation:

```bash
python -m fink_tasks.object_neighbors 170028486134595648 --allow-insecure-graph
```

Options:

```text
--classifier CLASSIFIER     classifier name; default: FINK
--distance METRIC          JensenShannon, Euclidean, or Cosine;
                           default: JensenShannon
-n, --results VALUE        result-count/cutoff parameter; default: 10
--rest-columns COLUMNS     additional Fink REST /objects columns; repeatable
--columns COLUMNS          alias for --rest-columns
--json                     machine-readable JSON output
--allow-insecure-graph     opt in to the default remote plaintext Gremlin endpoint
--timeout SECONDS          per-request timeout; default: 180
```

Examples:

```bash
# Ten neighbours using the defaults
fink-object-neighbors 170028486134595648 --allow-insecure-graph

# Twenty Euclidean-distance neighbours
fink-object-neighbors 170028486134595648 --allow-insecure-graph --distance Euclidean -n 20

# Variable-size result selected by Lomikel's relative gap cutoff
fink-object-neighbors 170028486134595648 --allow-insecure-graph -n 0.2 --json

# Add one or more values from the Fink REST /objects service
fink-object-neighbors 170028486134595648 --allow-insecure-graph \
  --columns r:g_psfFluxMax,r:nDiaSources

# The option can also be repeated; JSON includes values for target and neighbours
fink-object-neighbors 170028486134595648 --allow-insecure-graph \
  --rest-columns r:g_psfFluxMax --rest-columns f:main_label_classifier --json

# All graph candidates (potentially large and slow)
fink-object-neighbors 170028486134595648 --allow-insecure-graph -n 0
```

### Meaning of `--results`

The value is passed to the JanusGraph/Lomikel `objectNeighborhood` implementation:

- `VALUE >= 1`: return that many graph neighbours. It must be a whole number.
- `0 < VALUE < 1`: use Lomikel's relative distance-gap cutoff, so the number of returned objects varies with the distance distribution.
- `VALUE = 0`: return all graph candidates. This can be large and require many Fink REST API batches.

For the relative cutoff, Lomikel walks the ordered graph distances and stops when the ratio between successive non-flat distance gaps exceeds the supplied value. A smaller positive threshold is therefore more selective.

## Ranking

The command:

1. asks Fink JanusGraph for `objectNeighborhood(object_id, classifier, results, distance)`;
2. obtains object coordinates from the LSST Fink REST `/api/v1/objects` endpoint;
3. sorts the returned candidates by:
   1. JanusGraph classifier distance, ascending;
   2. great-circle angular distance, ascending;
   3. object ID, for deterministic ties.

For a positive result count, the command adaptively asks JanusGraph for enough neighbours to include the complete classifier-distance tie at the requested boundary. It then applies the angular tie-break and returns the requested count. This avoids arbitrary selection when many objects have exactly the same classifier distance. A relative cutoff or `-n 0` keeps the variable-size/all-results behavior defined by Lomikel.

## Find objects with the most points

Rank LSST objects by the cardinality of their Elasticsearch `mjd` field. With no type selection, both `ss_mjd` and `dia_mjd` are queried:

```bash
fink-most-points --allow-insecure-es
```

`most_points.py` is also a self-contained executable. After downloading or copying the single file:

```bash
chmod +x most_points.py
./most_points.py --allow-insecure-es
```

Ranking does not need third-party packages. To generate PNG light curves with the standalone file, activate an environment containing Matplotlib first:

```bash
python3 -m venv .venv
.venv/bin/python -m pip install "matplotlib>=3.7"
. .venv/bin/activate
./most_points.py --allow-insecure-es --object-type ss --lightcurves
```

Select one type or change the result count:

```bash
fink-most-points --allow-insecure-es --object-type ss --results 20
fink-most-points --allow-insecure-es --object-type dia -n 5
```

For ranked Solar System objects, optionally reverse-resolve each Elasticsearch SS ID and obtain its complete all-band light curve from the Fink REST API. The REST query uses the resolver's packed provisional designation, which is accepted by Quaero even for historical objects whose unpacked form (for example `A921 VA`) is rejected:

```bash
fink-most-points --allow-insecure-es \
  --object-type ss --results 3 --lightcurves \
  --output-dir results
```

When `--object-type both` is used with `--lightcurves`, light curves are generated only for the SS results. The output directory contains:

```text
ss_most_points.json                  SS ranking and verified MJD counts
dia_most_points.json                 DIA ranking and verified MJD counts
ss_<object-id>_lightcurve.json       complete REST rows for all returned bands
ss_<object-id>_lightcurve.png        all-band science/PSF flux plot
manifest.json                        artifact inventory and run parameters
```

The public Elasticsearch read endpoint is remote plaintext HTTP, so using the default requires explicit `--allow-insecure-es`. The script sends anonymous read-only `_search` requests and never attaches credentials.

## Test

```bash
PYTHONPATH=src python3 -m unittest discover -s tests -v
```

## Data services

Defaults target LSST:

- JanusGraph Gremlin REST: `http://134.158.243.144:24444`
- Fink REST API: `https://api.lsst.fink-portal.org`

The public graph service currently offers anonymous, read-only HTTP rather than HTTPS. Because plaintext responses lack transport-integrity protection, the CLI requires the explicit `--allow-insecure-graph` opt-in when using this default. Prefer an HTTPS endpoint or a protected loopback tunnel when available. No credentials are stored or transmitted by this repository.

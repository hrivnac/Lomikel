---
name: fink-task-development
description: Build reusable, verified Fink data-analysis tasks.
version: 0.1.0
author: Julius Hrivnac (hrivnac), Hermes Agent
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [Fink, Astronomy, Python, Elasticsearch, JanusGraph]
    related_skills: []
---

# Fink Task Development

Build self-contained Python command-line tasks that combine Fink REST, Elasticsearch, and JanusGraph data without depending on a particular user's machine, credentials, or private network. Treat `src/python/FinkTasks/` as the reference implementation and preserve scientific provenance, transport safety, deterministic results, and testability.

## When to Use

Use this skill when asked to:

- create a Fink analysis command similar to `object_neighbors.py` or `most_points.py`;
- rank, filter, cross-match, or enrich Fink LSST or ZTF objects;
- combine Fink REST data with Elasticsearch or JanusGraph results;
- export reproducible JSON, tabular, or plot artifacts;
- extend or repair the reusable `FinkTasks` Python package.

Do not use it for generic astronomy questions that need no Fink data, for graph or index mutations, or for operational database administration.

## Repository Map

Work from the repository root and inspect these files before designing a task:

```text
src/python/FinkTasks/README.md
src/python/FinkTasks/pyproject.toml
src/python/FinkTasks/src/fink_tasks/object_neighbors.py
src/python/FinkTasks/src/fink_tasks/most_points.py
src/python/FinkTasks/tests/test_object_neighbors.py
src/python/FinkTasks/tests/test_most_points.py
```

Use `read_file` for targeted source inspection and `search_files` to find reusable parsers, validators, transport helpers, data classes, and tests. Extend an existing module when the requested behavior belongs to it; create a new module only for a distinct command.

## Data Sources

Choose the most selective source first and keep the survey explicit throughout the workflow.

Current public documentation:

```text
Fink LSST: https://doc.lsst.fink-broker.org
Fink ZTF:  https://doc.ztf.fink-broker.org
Lomikel:   https://hrivnac.web.cern.ch/Activities/Packages/Lomikel/
Source:    https://github.com/hrivnac/Lomikel
```

### Fink REST API

Official survey endpoints:

```text
LSST: https://api.lsst.fink-portal.org
ZTF:  https://api.ztf.fink-portal.org
```

Use REST for supported object summaries, sources/light curves, conesearch, schemas, and resolver queries. Check the live survey documentation before assuming endpoint names, request fields, response columns, pagination, or SS/DIA semantics.

### Elasticsearch

Use Elasticsearch for bulk index searches, object-ID selection, stored positions, observation times, and cardinality ranking. Relevant LSST index families include:

```text
ss_mjd     Solar System object observation times
ss_radec   Solar System object positions
dia_mjd    DIA object observation times
dia_radec  DIA object positions
```

Treat the Elasticsearch document `_id` as the object identifier unless the live mapping proves otherwise. Do not confuse the number of documents with the number of MJD or coordinate values inside a document.

### JanusGraph / Gremlin

Use JanusGraph for classification relations, tags, graph neighborhoods, overlaps, and schema translation. A remote Gremlin HTTP response is GraphSON: validate both the HTTP status and `status.code`, then unwrap typed `@value` structures deliberately.

Lomikel operations such as `objectNeighborhood(object_id, classifier, nmax, distance)` can supply classifier-distance candidates. Verify the current server-side contract from Lomikel source or documentation before generating a traversal or interpreting its result.

## Prerequisites

- Python 3.10 or newer.
- Network access to the selected Fink services for live runs.
- Matplotlib only when PNG plots are requested.
- Credentials supplied through environment variables or an external secret manager when an authenticated endpoint genuinely requires them.

Never commit credentials, tokens, passwords, private hostnames, or machine-local paths. Never place Basic authentication on a remote plaintext HTTP connection.

The package can be installed from the repository root with:

```text
python -m venv .venv
python -m pip install -e src/python/FinkTasks
```

Use the Python executable from the newly created environment. Environment activation syntax is platform-specific and is not required if that interpreter is invoked explicitly.

## Procedure

### 1. Define the scientific contract

Write down before coding:

- survey: LSST, ZTF, or an explicitly separated comparison;
- input identifiers or selection region;
- authoritative source for each field;
- ranking and tie-breaking rules;
- requested result limit and its exact meaning;
- output columns and artifact formats;
- behavior for missing, malformed, duplicate, or non-finite data.

Completion criterion: every output field and ranking decision has a named source and deterministic rule.

### 2. Verify live semantics

Consult current Fink and Lomikel documentation, then inspect live schemas or mappings with read-only requests where available. Do not silently reuse LSST fields for ZTF, or REST semantics for Elasticsearch documents.

Completion criterion: the implementation records the survey, endpoint family, index or operation name, and query parameters it actually uses.

### 3. Select the source order

Start with the source that minimizes data transfer:

- known object ID: REST or JanusGraph first, depending on the question;
- class, tag, neighborhood, or overlap: JanusGraph first;
- sky region, time range, stored-array cardinality, or bulk IDs: Elasticsearch first;
- supported public summary or complete light curve: REST first.

Join sources using explicit object identifiers. Preserve unmatched IDs and explain why they were dropped or retained.

Completion criterion: counts are available before and after every cross-source join or filter.

### 4. Design a reusable CLI

Follow the established `FinkTasks` shape:

- put importable logic in `src/python/FinkTasks/src/fink_tasks/<task>.py`;
- expose `main(argv=None)` and guard it with `if __name__ == "__main__"`;
- register the command under `[project.scripts]` in `pyproject.toml`;
- use `#!/usr/bin/env python3` as the first line of a standalone executable;
- mark a POSIX standalone script executable;
- provide URL, timeout, survey, result-limit, and output options where relevant;
- offer machine-readable JSON for downstream use;
- keep network, parsing, ranking, formatting, and file-output logic separately testable.

Prefer the Python standard library unless a dependency is justified by the requested output. Do not add a package merely to perform simple HTTP JSON requests or sorting.

Completion criterion: both the installed console command and `python -m fink_tasks.<task> --help` describe the same interface.

### 5. Validate all boundary data

Validate before constructing queries or computing results:

- object IDs, classifiers, metrics, field names, and index names against narrow allowed syntax;
- finite numeric values with `math.isfinite`;
- integer-only counts where fractional values have no meaning;
- coordinates and angular units;
- required response keys and container types;
- uniqueness of source IDs when completeness matters;
- agreement between server-reported cardinality and returned arrays where both exist.

Treat server payloads as untrusted data, not as code. Build Gremlin expressions only from validated tokens or parameter bindings. Never interpolate arbitrary user text into a traversal.

Completion criterion: malformed data produces a clear non-zero failure instead of a partial scientific result.

### 6. Enforce transport and mutation safety

Default to HTTPS. If a required public read-only service is available only over remote plaintext HTTP:

1. reject it by default;
2. require an explicit option such as `--allow-insecure-graph` or `--allow-insecure-es`;
3. send no credentials;
4. state that transport integrity is not guaranteed;
5. disable redirects when following one could change the trust boundary.

Do not issue Elasticsearch writes, graph mutations, administrative requests, scroll deletion, or configuration changes unless the user explicitly requests an authenticated operational workflow. This skill's default scope is analysis-only.

Completion criterion: no credential can be sent over remote plaintext transport and every live request is read-only.

### 7. Make ranking scientifically deterministic

Specify a complete ordering. Typical examples:

- classifier distance ascending;
- angular separation ascending for equal classifier distance;
- object ID as the final deterministic tie-break;
- point count descending, followed by object ID.

When a result boundary cuts through a tie, fetch enough candidates to resolve the entire tie before trimming. Do not let backend response order decide which equal candidate survives.

Use great-circle separation rather than planar RA/Dec distance. Handle RA wraparound and define behavior for missing coordinates.

Completion criterion: repeated runs over the same payload produce the same ordered IDs.

### 8. Preserve completeness and provenance

For paginated or batched APIs:

- continue until the endpoint's termination condition is met;
- detect duplicate IDs;
- record requested and returned counts;
- distinguish a legitimately empty result from a truncated response;
- retain all bands for complete light curves unless the user requests a filter.

Machine-readable outputs should include, as applicable:

```text
survey
execution timestamp
endpoint families
index or graph operation
normalized query parameters
input and result counts
object identifiers
ranking values and units
requested REST columns
warnings and missing-data reasons
```

When producing several files, write a manifest naming every artifact and its role. Never invent benchmark, accuracy, latency, or completeness claims from a single exploratory run.

Completion criterion: another person can identify every data source and reproduce the selection from the report and command arguments.

### 9. Test without depending on live services

Add focused tests under `src/python/FinkTasks/tests/`. Unit tests must not require external network availability. Use representative response fixtures, mocks at the transport boundary, or a local HTTP server.

Cover at least:

- valid response parsing;
- unsuccessful HTTP and service-level statuses;
- malformed and missing fields;
- NaN and infinity rejection;
- deterministic sorting and complete ties;
- transport policy and redirect behavior;
- count/cardinality cross-checks;
- CLI argument validation;
- JSON normalization and artifact manifests;
- direct `--help` execution for a standalone script.

Run from the repository root after installing the package into an isolated environment:

```text
python -m unittest discover -s src/python/FinkTasks/tests -v
python -m py_compile src/python/FinkTasks/src/fink_tasks/<task>.py
python -m fink_tasks.<task> --help
fink-object-neighbors --help
fink-most-points --help
```

Run the installed-console check for the command being added or changed. On POSIX, also run the script directly after verifying its executable bit. A live smoke test is optional and must use only documented read-only operations.

Completion criterion: the complete FinkTasks suite passes, direct/module help succeeds, and no test needs production credentials.

### 10. Document and review the result

Update `src/python/FinkTasks/README.md` with:

- installation and invocation examples;
- option semantics, including ambiguous limits or cutoffs;
- source and ranking definitions;
- generated artifacts;
- transport limitations;
- the exact test command.

Before publishing, inspect the final diff, run syntax and unit tests, and ensure only intended files changed. Report real test output and disclose any live endpoint that could not be verified.

Completion criterion: source, tests, CLI registration, and README agree on names, defaults, and behavior.

## Reference Patterns

### Object-neighborhood task

`object_neighbors.py` demonstrates a multi-source workflow:

1. validate object, classifier, metric, and result-limit inputs;
2. ask JanusGraph/Lomikel for classifier-distance neighbors;
3. parse GraphSON and verify Gremlin status;
4. fetch coordinates and optional columns from the Fink REST API;
5. rank by graph distance, angular separation, and object ID;
6. emit a human table or JSON.

Preserve the distinction between `nmax >= 1` as a result count, `0 < nmax < 1` as a relative distance-gap cutoff, and `nmax = 0` as all graph candidates.

### Most-points task

`most_points.py` demonstrates a bulk-index and artifact workflow:

1. rank `ss_mjd` and/or `dia_mjd` documents by `mjd` cardinality;
2. verify each reported count against the returned array;
3. cross-check corresponding coordinate-document cardinality;
4. optionally resolve Solar System identifiers and fetch complete all-band REST light curves;
5. write rankings, light curves, plots, and a manifest.

Do not assume the unpacked Solar System designation is accepted by every REST route; preserve and test the resolver identifier required by the live API.

## Pitfalls

1. **Mixing LSST and ZTF contracts.** Their hosts, columns, object types, and API behavior are not interchangeable.
2. **Counting documents instead of observations.** Elasticsearch `_count` does not measure the length of an object's `mjd` array.
3. **Trusting GraphSON as ordinary JSON.** Typed map/list wrappers require explicit decoding.
4. **Ignoring service-level errors.** HTTP 200 does not prove Gremlin `status.code` is successful.
5. **Arbitrary tie truncation.** Fetch and resolve the full boundary tie before applying a secondary sort.
6. **Using Euclidean sky distance.** Use great-circle geometry and handle RA wraparound.
7. **Silently accepting incomplete pages.** Track IDs and counts across every batch.
8. **Sending credentials to HTTP endpoints.** Explicit opt-in is only for anonymous read-only traffic.
9. **Embedding local infrastructure.** Hostnames, paths, and credentials must be runtime inputs, not committed defaults unless they are documented public services.
10. **Writing only a notebook.** Reusable work belongs in importable functions, a tested CLI, and machine-readable artifacts.
11. **Missing executable metadata.** A standalone POSIX script needs both a Python shebang and executable mode.
12. **Fabricating scientific confidence.** Report observed data and limitations; do not promote exploratory output to a validated classifier result.

## Verification Checklist

- [ ] Survey and object type are explicit.
- [ ] Live endpoint semantics were checked rather than guessed.
- [ ] Every output field has a named authoritative source.
- [ ] Requests are read-only and transport policy is enforced.
- [ ] User-controlled tokens cannot become Gremlin code.
- [ ] Numeric values, coordinates, cardinalities, and response shapes are validated.
- [ ] Cross-source joins report input, matched, missing, and output counts.
- [ ] Ranking has deterministic tie-breaking.
- [ ] JSON/artifact outputs include parameters and provenance.
- [ ] No credentials, private paths, or private hosts are committed.
- [ ] Standalone scripts have a shebang and, on POSIX, executable mode.
- [ ] All FinkTasks tests and CLI smoke checks pass.
- [ ] Documentation matches the implemented command.

#!/usr/bin/env python3
"""Rank Fink LSST objects by Elasticsearch point count and export light curves."""

from __future__ import annotations

import argparse
import json
import math
import re
import sys
import urllib.error
import urllib.request
from dataclasses import asdict, dataclass, replace
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Sequence
from urllib.parse import urlsplit


DEFAULT_ES_URL = "http://134.158.243.139:24499"
DEFAULT_API_URL = "https://api.lsst.fink-portal.org"
LIGHTCURVE_COLUMNS = (
    "r:diaSourceId,r:diaObjectId,r:ssObjectId,r:midpointMjdTai,r:band,"
    "r:scienceFlux,r:scienceFluxErr,r:psfFlux,r:psfFluxErr,r:ra,r:dec"
)


class _NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def _post_json(
    url: str,
    payload: dict[str, Any],
    timeout: float,
    *,
    allow_redirects: bool = True,
) -> Any:
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        if allow_redirects:
            response_context = urllib.request.urlopen(request, timeout=timeout)
        else:
            opener = urllib.request.build_opener(_NoRedirectHandler())
            response_context = opener.open(request, timeout=timeout)
        with response_context as response:
            body = response.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")[:1000]
        raise RuntimeError(f"HTTP {exc.code} from {url}: {detail}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"request to {url} failed: {exc.reason}") from exc
    return json.loads(body)


def _json_safe(value: Any) -> Any:
    if isinstance(value, float) and not math.isfinite(value):
        return None
    if isinstance(value, dict):
        return {str(key): _json_safe(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_json_safe(item) for item in value]
    return value


@dataclass(frozen=True)
class RankedObject:
    object_type: str
    object_id: str
    point_count: int
    mjd_min: float
    mjd_max: float
    radec_point_count: int | None = None


@dataclass(frozen=True)
class ResolverMatch:
    ss_object_id: str
    packed_designation: str
    unpacked_designation: str


def build_ranking_query(results: int) -> dict[str, Any]:
    """Build the Elasticsearch query that ranks by MJD array cardinality."""
    if results < 1:
        raise ValueError("results must be at least 1")
    return {
        "size": results,
        "track_total_hits": True,
        "_source": ["mjd"],
        "query": {"exists": {"field": "mjd"}},
        "sort": [
            {
                "_script": {
                    "type": "number",
                    "script": {"lang": "painless", "source": "doc['mjd'].size()"},
                    "order": "desc",
                }
            },
            {"_id": {"order": "asc"}},
        ],
    }


def parse_ranking_response(
    response: dict[str, Any], object_type: str
) -> list[RankedObject]:
    """Parse ranked hits and verify script cardinality against returned MJD values."""
    if not isinstance(response, dict) or not isinstance(response.get("hits"), dict):
        raise ValueError("Elasticsearch response has no hits object")
    hits = response["hits"].get("hits")
    if not isinstance(hits, list):
        raise ValueError("Elasticsearch response has no hits list")
    ranked: list[RankedObject] = []
    for hit in hits:
        if not isinstance(hit, dict):
            raise ValueError("Elasticsearch hit must be an object")
        if hit.get("_id") is None:
            raise ValueError("Elasticsearch hit has no object ID")
        object_id = str(hit["_id"])
        source = hit.get("_source")
        if not isinstance(source, dict):
            raise ValueError(f"Elasticsearch hit has no source object for {object_id}")
        raw_mjd = source.get("mjd")
        values = raw_mjd if isinstance(raw_mjd, list) else [raw_mjd]
        if not values or any(value is None for value in values):
            raise ValueError(f"missing MJD values for {object_id}")
        try:
            mjds = [float(value) for value in values]
        except (TypeError, ValueError, OverflowError) as exc:
            raise ValueError(f"invalid MJD values for {object_id}") from exc
        if not all(math.isfinite(value) for value in mjds):
            raise ValueError(f"non-finite MJD values for {object_id}")
        sort = hit.get("sort")
        if not isinstance(sort, list) or not sort:
            raise ValueError(f"missing script cardinality for {object_id}")
        try:
            raw_count = float(sort[0])
        except (TypeError, ValueError, OverflowError) as exc:
            raise ValueError(f"invalid script cardinality for {object_id}") from exc
        if not math.isfinite(raw_count) or not raw_count.is_integer():
            raise ValueError(f"invalid script cardinality for {object_id}")
        count = int(raw_count)
        if count != len(mjds):
            raise ValueError(
                f"MJD cardinality disagreement for {object_id}: sort={count}, source={len(mjds)}"
            )
        ranked.append(
            RankedObject(object_type, object_id, count, min(mjds), max(mjds))
        )
    return ranked


def parse_radec_response(
    response: dict[str, Any], object_ids: Sequence[str]
) -> dict[str, int]:
    """Verify paired coordinate documents and return location cardinalities."""
    if not isinstance(response, dict):
        raise ValueError("Elasticsearch radec response must be an object")
    docs = response.get("docs")
    if not isinstance(docs, list):
        raise ValueError("Elasticsearch radec response has no docs list")
    counts: dict[str, int] = {}
    for doc in docs:
        if not isinstance(doc, dict):
            raise ValueError("Elasticsearch radec row must be an object")
        if doc.get("_id") is None:
            raise ValueError("Elasticsearch radec row has no object ID")
        object_id = str(doc["_id"])
        if not doc.get("found"):
            raise ValueError(f"paired radec document not found for {object_id}")
        source = doc.get("_source")
        if not isinstance(source, dict):
            raise ValueError(f"paired radec document has no source for {object_id}")
        location = source.get("location")
        if location is None:
            raise ValueError(f"paired radec document has no location for {object_id}")
        counts[object_id] = len(location) if isinstance(location, list) else 1
    missing = set(object_ids) - counts.keys()
    if missing:
        raise ValueError(f"paired radec response omitted: {sorted(missing)}")
    return counts


def fetch_ranked_objects(
    es_url: str,
    object_type: str,
    results: int,
    timeout: float,
) -> list[RankedObject]:
    if object_type not in {"ss", "dia"}:
        raise ValueError(f"unsupported object type: {object_type}")
    response = _post_json(
        f"{es_url.rstrip('/')}/{object_type}_mjd/_search",
        build_ranking_query(results),
        timeout,
        allow_redirects=False,
    )
    ranked = parse_ranking_response(response, object_type)
    ids = [row.object_id for row in ranked]
    if not ids:
        return ranked
    radec_response = _post_json(
        f"{es_url.rstrip('/')}/{object_type}_radec/_mget",
        {"ids": ids},
        timeout,
        allow_redirects=False,
    )
    counts = parse_radec_response(radec_response, ids)
    return [replace(row, radec_point_count=counts[row.object_id]) for row in ranked]


def select_resolver_match(rows: Any, ss_object_id: str) -> ResolverMatch:
    """Select the exact reverse-resolver row for an Elasticsearch SS ID."""
    if not isinstance(rows, list):
        raise ValueError("resolver response must be a list")
    matches = []
    for row in rows:
        if not isinstance(row, dict):
            raise ValueError("resolver response row must be an object")
        row_id = row.get("r:ssObjectId")
        if row_id is None or isinstance(row_id, (dict, list)):
            raise ValueError("resolver response row has no valid ssObjectId")
        if str(row_id) == ss_object_id:
            matches.append(row)
    if len(matches) != 1:
        raise ValueError(
            f"resolver returned {len(matches)} exact matches for {ss_object_id}"
        )
    row = matches[0]
    unpacked = row.get("r:unpacked_primary_provisional_designation")
    if not unpacked:
        raise ValueError(f"resolver match for {ss_object_id} has no designation")
    return ResolverMatch(
        ss_object_id,
        str(row.get("r:packed_primary_provisional_designation") or ""),
        str(unpacked),
    )


def normalize_ss_lightcurve(rows: Any, ss_object_id: str) -> list[dict[str, Any]]:
    """Verify SS identity/source uniqueness and sort all returned bands by MJD."""
    if not isinstance(rows, list):
        raise ValueError("SSO response must be a list")
    source_ids: set[str] = set()
    normalized: list[dict[str, Any]] = []
    for row in rows:
        if not isinstance(row, dict):
            raise ValueError("SSO response row must be an object")
        row_id = row.get("r:ssObjectId")
        if row_id is None or isinstance(row_id, (dict, list)):
            raise ValueError("SSO response row has no valid ssObjectId")
        if str(row_id) != ss_object_id:
            raise ValueError(f"SSO response contains another ssObjectId")
        raw_source_id = row.get("r:diaSourceId")
        if (
            raw_source_id is None
            or isinstance(raw_source_id, (bool, dict, list))
            or (isinstance(raw_source_id, str) and not raw_source_id.strip())
        ):
            raise ValueError("SSO response row has no valid diaSourceId")
        source_id = str(raw_source_id)
        if source_id in source_ids:
            raise ValueError(f"duplicate diaSourceId {source_id}")
        source_ids.add(source_id)
        mjd = _finite(row.get("r:midpointMjdTai"))
        if mjd is None:
            raise ValueError("SSO response row must have a finite MJD")
        if row.get("r:band") is None:
            raise ValueError("SSO response row is missing MJD or band")
        normalized.append(dict(row))
    normalized.sort(key=lambda row: (float(row["r:midpointMjdTai"]), str(row["r:diaSourceId"])))
    return normalized


def fetch_ss_lightcurve(
    api_url: str,
    ss_object_id: str,
    timeout: float,
) -> tuple[ResolverMatch, list[dict[str, Any]]]:
    """Reverse-resolve an SS ID and fetch all-band Fink REST source rows."""
    resolver_rows = _post_json(
        f"{api_url.rstrip('/')}/api/v1/resolver",
        {
            "resolver": "ssodnet",
            "name_or_id": ss_object_id,
            "reverse": True,
            "nmax": 10,
            "output-format": "json",
        },
        timeout,
        allow_redirects=False,
    )
    match = select_resolver_match(resolver_rows, ss_object_id)
    query_designation = match.packed_designation or match.unpacked_designation
    source_rows = _post_json(
        f"{api_url.rstrip('/')}/api/v1/sso",
        {
            "n_or_d": query_designation,
            "columns": LIGHTCURVE_COLUMNS,
            "output-format": "json",
        },
        timeout,
        allow_redirects=False,
    )
    return match, normalize_ss_lightcurve(source_rows, ss_object_id)


def build_lightcurve_document(
    ss_object_id: str,
    designation: str,
    es_point_count: int,
    sources: list[dict[str, Any]],
) -> dict[str, Any]:
    """Build the JSON-serializable all-band light-curve artifact."""
    band_counts: dict[str, int] = {}
    for row in sources:
        band = str(row["r:band"])
        band_counts[band] = band_counts.get(band, 0) + 1
    return {
        "object_type": "ss",
        "ss_object_id": ss_object_id,
        "designation": designation,
        "elasticsearch_point_count": es_point_count,
        "rest_source_count": len(sources),
        "bands": dict(sorted(band_counts.items())),
        "sources": sources,
    }


def selected_object_types(selection: str) -> list[str]:
    if selection == "both":
        return ["ss", "dia"]
    if selection in {"ss", "dia"}:
        return [selection]
    raise ValueError(f"unsupported object type: {selection}")


def _finite(value: Any) -> float | None:
    try:
        number = float(value)
    except (TypeError, ValueError, OverflowError):
        return None
    return number if math.isfinite(number) else None


def plot_ss_lightcurve(document: dict[str, Any], output_path: Path) -> None:
    """Plot all returned Rubin bands for science and difference flux."""
    try:
        import matplotlib
    except ImportError as exc:
        raise RuntimeError(
            'Matplotlib is required for --lightcurves; create or activate a virtual '
            'environment, then run: python3 -m pip install "matplotlib>=3.7"'
        ) from exc

    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    sources = document.get("sources", [])
    bands = sorted({str(row.get("r:band")) for row in sources})
    colors = {
        "u": "#7b2cbf",
        "g": "#2ca02c",
        "r": "#d62728",
        "i": "#8c564b",
        "z": "#222222",
        "y": "#ff9f1c",
    }
    fig, axes = plt.subplots(2, 1, figsize=(10, 8), sharex=True)
    plotted = 0
    for axis, flux_column, error_column, title in [
        (axes[0], "r:scienceFlux", "r:scienceFluxErr", "Science flux"),
        (axes[1], "r:psfFlux", "r:psfFluxErr", "Difference-image PSF flux"),
    ]:
        for band in bands:
            points = []
            for row in sources:
                if str(row.get("r:band")) != band:
                    continue
                mjd = _finite(row.get("r:midpointMjdTai"))
                flux = _finite(row.get(flux_column))
                if mjd is None or flux is None:
                    continue
                error = _finite(row.get(error_column))
                points.append((mjd, flux, 0.0 if error is None or error < 0 else error))
            if not points:
                continue
            points.sort()
            x, y, yerr = zip(*points)
            axis.errorbar(
                x,
                y,
                yerr=yerr,
                fmt="o-",
                markersize=3,
                linewidth=0.8,
                capsize=1.5,
                label=band,
                color=colors.get(band),
            )
            plotted += len(points)
        axis.set_ylabel("Flux [nJy]")
        axis.set_title(title)
        axis.grid(alpha=0.25)
        if axis.lines:
            axis.legend(title="Band", ncol=max(1, min(6, len(bands))))
    axes[1].axhline(0.0, color="gray", linewidth=0.8)
    axes[1].set_xlabel("MJD (TAI)")
    fig.suptitle(
        f"Fink LSST SS {document['ss_object_id']} — {document['designation']}"
    )
    if plotted == 0:
        plt.close(fig)
        raise ValueError("light curve has no finite flux points to plot")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig.tight_layout()
    fig.savefig(output_path, dpi=180)
    plt.close(fig)


def _validate_service_url(url: str, label: str, allow_remote_http: bool) -> str:
    parsed = urlsplit(url)
    if parsed.username is not None or parsed.password is not None:
        raise ValueError(f"{label} URL must not contain userinfo")
    if not parsed.hostname or parsed.scheme not in {"http", "https"}:
        raise ValueError(f"{label} URL must be absolute HTTP(S)")
    if parsed.scheme == "https" or parsed.hostname in {"localhost", "127.0.0.1", "::1"}:
        return url
    if allow_remote_http:
        return url
    raise ValueError(
        f"remote plaintext {label} URL requires --allow-insecure-es"
    )


def _safe_file_token(value: str) -> str:
    if not re.fullmatch(r"[A-Za-z0-9_.-]+", value):
        raise ValueError(f"unsafe object ID for output filename: {value!r}")
    return value


def _write_json(path: Path, document: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(_json_safe(document), indent=2, sort_keys=True, allow_nan=False)
        + "\n",
        encoding="utf-8",
    )


def _ranking_document(
    object_type: str, rows: Sequence[RankedObject], generated_at: str
) -> dict[str, Any]:
    return {
        "generated_at": generated_at,
        "source": f"Elasticsearch {object_type}_mjd",
        "object_type": object_type,
        "returned": len(rows),
        "objects": [
            {"rank": rank, **asdict(row)} for rank, row in enumerate(rows, 1)
        ],
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Rank Fink LSST SS/DIA objects by Elasticsearch MJD point count and "
            "optionally export all-band SS light curves as JSON and PNG."
        )
    )
    parser.add_argument(
        "--object-type",
        choices=("ss", "dia", "both"),
        default="both",
        help="objects to rank (default: both)",
    )
    parser.add_argument(
        "-n", "--results", type=int, default=10, help="objects per type (default: 10)"
    )
    parser.add_argument(
        "--lightcurves",
        "--ss-lightcurves",
        action="store_true",
        help="download all-band Fink REST light curves and PNG plots for ranked SS objects",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("fink-most-points-output"),
        help="artifact directory (default: fink-most-points-output)",
    )
    parser.add_argument(
        "--allow-insecure-es",
        action="store_true",
        help="explicitly allow the public remote plaintext Elasticsearch endpoint",
    )
    parser.add_argument("--es-url", default=DEFAULT_ES_URL, help=argparse.SUPPRESS)
    parser.add_argument("--api-url", default=DEFAULT_API_URL, help=argparse.SUPPRESS)
    parser.add_argument("--timeout", type=float, default=180.0)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        if args.results < 1:
            raise ValueError("results must be at least 1")
        if not math.isfinite(args.timeout) or args.timeout <= 0:
            raise ValueError("timeout must be a finite positive number")
        object_types = selected_object_types(args.object_type)
        if args.lightcurves and "ss" not in object_types:
            raise ValueError("--lightcurves requires SS objects")
        es_url = _validate_service_url(
            args.es_url, "Elasticsearch", args.allow_insecure_es
        )
        api_url = _validate_service_url(args.api_url, "Fink REST", False)
        output_dir = args.output_dir.resolve()
        output_dir.mkdir(parents=True, exist_ok=True)
        generated_at = datetime.now(timezone.utc).isoformat()
        ranked_by_type: dict[str, list[RankedObject]] = {}
        artifacts: list[dict[str, Any]] = []

        for object_type in object_types:
            rows = fetch_ranked_objects(
                es_url, object_type, args.results, args.timeout
            )
            ranked_by_type[object_type] = rows
            path = output_dir / f"{object_type}_most_points.json"
            _write_json(path, _ranking_document(object_type, rows, generated_at))
            artifacts.append({"kind": "ranking_json", "path": path.name})

        if args.lightcurves:
            for ranked in ranked_by_type.get("ss", []):
                match, sources = fetch_ss_lightcurve(
                    api_url, ranked.object_id, args.timeout
                )
                document = build_lightcurve_document(
                    ranked.object_id,
                    match.unpacked_designation,
                    ranked.point_count,
                    sources,
                )
                document["packed_designation"] = match.packed_designation
                document["generated_at"] = generated_at
                safe_id = _safe_file_token(ranked.object_id)
                json_path = output_dir / f"ss_{safe_id}_lightcurve.json"
                png_path = output_dir / f"ss_{safe_id}_lightcurve.png"
                _write_json(json_path, document)
                plot_ss_lightcurve(document, png_path)
                artifacts.extend(
                    [
                        {"kind": "lightcurve_json", "path": json_path.name},
                        {"kind": "lightcurve_png", "path": png_path.name},
                    ]
                )

        manifest_path = output_dir / "manifest.json"
        _write_json(
            manifest_path,
            {
                "generated_at": generated_at,
                "object_type": args.object_type,
                "results_per_type": args.results,
                "ss_lightcurves": args.lightcurves,
                "artifacts": artifacts,
            },
        )
        print(manifest_path)
        for artifact in artifacts:
            print(output_dir / artifact["path"])
        return 0
    except (OSError, RuntimeError, ValueError, json.JSONDecodeError) as exc:
        parser.exit(2, f"error: {exc}\n")


if __name__ == "__main__":
    sys.exit(main())

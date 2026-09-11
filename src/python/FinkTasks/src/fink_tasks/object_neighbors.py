"""Find LSST/Fink objects nearest in classifier and sky distance."""

from __future__ import annotations

import argparse
import json
import math
import re
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from typing import Any, Iterable, Sequence
from urllib.parse import urlsplit

DEFAULT_GRAPH_URL = "http://134.158.243.144:24444"
DEFAULT_API_URL = "https://api.lsst.fink-portal.org"
SUPPORTED_METRICS = ("JensenShannon", "Euclidean", "Cosine")


@dataclass(frozen=True)
class Neighbor:
    object_id: str
    graph_distance: float
    classes: dict[str, float]


@dataclass(frozen=True)
class RankedNeighbor:
    neighbor: Neighbor
    angular_distance_arcsec: float
    ra_deg: float | None
    dec_deg: float | None
    rest_values: dict[str, Any] = field(default_factory=dict)


def validate_nmax(value: float) -> float:
    """Validate Lomikel's dual-purpose nmax argument."""
    if not math.isfinite(value) or value < 0:
        raise ValueError("results must be a finite, non-negative number")
    if value >= 1 and not value.is_integer():
        raise ValueError("results >= 1 must be a whole number")
    return value


def validate_safe_token(value: str, label: str) -> str:
    """Reject values that cannot safely become server-side Gremlin literals."""
    if not re.fullmatch(r"[A-Za-z0-9_.:=\-]+", value):
        raise ValueError(
            f"{label} must contain only letters, digits, dot, underscore, colon, "
            "equals, or hyphen"
        )
    return value


def parse_rest_columns(values: Sequence[str] | None) -> list[str]:
    """Parse repeatable comma-separated Fink REST column names."""
    columns: list[str] = []
    for group in values or []:
        for raw_column in group.split(","):
            column = raw_column.strip()
            if not column:
                continue
            if not re.fullmatch(r"[A-Za-z0-9_.:\-]+", column):
                raise ValueError(f"invalid REST column: {column!r}")
            if column not in columns:
                columns.append(column)
    return columns


def validate_graph_transport(url: str, allow_insecure: bool) -> str:
    """Require response-integrity protection or explicit plaintext opt-in."""
    parsed = urlsplit(url)
    if not parsed.hostname or parsed.scheme not in {"http", "https"}:
        raise ValueError("graph URL must be an absolute HTTP(S) URL")
    if parsed.scheme == "https":
        return url
    if parsed.hostname in {"localhost", "127.0.0.1", "::1"}:
        return url
    if allow_insecure:
        return url
    raise ValueError(
        "remote plaintext graph URL requires --allow-insecure-graph; "
        "responses can otherwise be altered in transit"
    )


def _graphson_scalar(value: Any) -> Any:
    while isinstance(value, dict) and "@value" in value:
        value = value["@value"]
    return value


def _finite_number(
    value: Any,
    label: str,
    minimum: float | None = None,
    maximum: float | None = None,
) -> float:
    try:
        number = float(value)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"{label} is not numeric") from exc
    if not math.isfinite(number):
        raise ValueError(f"{label} must be finite")
    if minimum is not None and number < minimum:
        raise ValueError(f"{label} must be >= {minimum}")
    if maximum is not None and number > maximum:
        raise ValueError(f"{label} must be <= {maximum}")
    return number


def _graphson_map_pairs(value: Any) -> list[tuple[Any, Any]]:
    raw = _graphson_scalar(value)
    if not isinstance(raw, list) or len(raw) % 2:
        raise ValueError("invalid GraphSON map")
    return [(raw[i], raw[i + 1]) for i in range(0, len(raw), 2)]


def parse_neighborhood_response(response: dict[str, Any]) -> list[Neighbor]:
    """Parse the Map<Map.Entry<objectId, distance>, classification> GraphSON."""
    status = response.get("status", {})
    code = _graphson_scalar(status.get("code"))
    if code != 200:
        message = status.get("message", "")
        raise RuntimeError(f"Gremlin status {code}: {message}")

    data = _graphson_scalar(response.get("result", {}).get("data", []))
    if data is None:
        return []
    if not isinstance(data, list):
        raise ValueError("invalid GraphSON neighborhood list")

    neighbors: list[Neighbor] = []
    for outer_map in data:
        outer_pairs = _graphson_map_pairs(outer_map)
        if len(outer_pairs) != 1:
            raise ValueError("expected one neighborhood entry per GraphSON map")
        entry_node, classes_node = outer_pairs[0]
        entry_pairs = _graphson_map_pairs(entry_node)
        if len(entry_pairs) != 1:
            raise ValueError("invalid object-distance entry")
        object_id, distance = entry_pairs[0]
        classes = {
            str(_graphson_scalar(name)): _finite_number(
                _graphson_scalar(weight), "classification weight", 0.0, 1.0
            )
            for name, weight in _graphson_map_pairs(classes_node)
        }
        neighbors.append(
            Neighbor(
                object_id=str(_graphson_scalar(object_id)),
                graph_distance=_finite_number(
                    _graphson_scalar(distance), "graph distance", 0.0, 1.0
                ),
                classes=classes,
            )
        )
    return neighbors


def angular_distance_arcsec(
    ra1_deg: float, dec1_deg: float, ra2_deg: float, dec2_deg: float
) -> float:
    """Great-circle separation in arcseconds using the haversine formula."""
    ra1, dec1, ra2, dec2 = map(
        math.radians, (ra1_deg, dec1_deg, ra2_deg, dec2_deg)
    )
    delta_ra = (ra2 - ra1 + math.pi) % (2 * math.pi) - math.pi
    delta_dec = dec2 - dec1
    a = (
        math.sin(delta_dec / 2) ** 2
        + math.cos(dec1) * math.cos(dec2) * math.sin(delta_ra / 2) ** 2
    )
    return math.degrees(2 * math.asin(min(1.0, math.sqrt(a)))) * 3600.0


def sort_neighbors(
    neighbors: Iterable[Neighbor],
    target_position: tuple[float, float],
    positions: dict[str, tuple[float, float]],
    rest_values: dict[str, dict[str, Any]] | None = None,
) -> list[RankedNeighbor]:
    """Sort first by graph distance, then angular distance, then object ID."""
    rows: list[RankedNeighbor] = []
    rest_values = rest_values or {}
    target_ra, target_dec = target_position
    for neighbor in neighbors:
        position = positions.get(neighbor.object_id)
        values = rest_values.get(neighbor.object_id, {})
        if position is None:
            rows.append(RankedNeighbor(neighbor, math.inf, None, None, values))
        else:
            ra, dec = position
            rows.append(
                RankedNeighbor(
                    neighbor,
                    angular_distance_arcsec(target_ra, target_dec, ra, dec),
                    ra,
                    dec,
                    values,
                )
            )
    return sorted(
        rows,
        key=lambda row: (
            row.neighbor.graph_distance,
            row.angular_distance_arcsec,
            row.neighbor.object_id,
        ),
    )


def tie_boundary_is_complete(
    neighbors: Sequence[Neighbor], requested_count: int, probe_size: int
) -> bool:
    """Return whether a graph probe contains the whole count-boundary tie."""
    if len(neighbors) < probe_size:
        return True
    if requested_count < 1 or len(neighbors) < requested_count:
        return True
    distances = sorted(row.graph_distance for row in neighbors)
    return distances[-1] > distances[requested_count - 1]


def trim_ranked_results(
    rows: Sequence[RankedNeighbor], nmax: float
) -> list[RankedNeighbor]:
    """Apply a positive count after graph/angular ordering; keep cutoff results."""
    if nmax >= 1:
        return list(rows[: int(nmax)])
    return list(rows)


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


def fetch_neighborhood(
    object_id: str,
    classifier: str,
    nmax: float,
    metric: str,
    graph_url: str,
    timeout: float,
) -> list[Neighbor]:
    # Values are validated before becoming single-quoted Groovy literals.
    oid_literal = f"'{validate_safe_token(object_id, 'object ID')}'"
    classifier_literal = f"'{validate_safe_token(classifier, 'classifier')}'"
    metric_literal = f"'{validate_safe_token(metric, 'distance measure')}'"
    gremlin = (
        f"gr.objectNeighborhood({oid_literal},{classifier_literal},"
        f"{nmax!r},{metric_literal})"
    )
    response = _post_json(
        graph_url,
        {"gremlin": gremlin},
        timeout,
        allow_redirects=False,
    )
    return parse_neighborhood_response(response)


def _chunks(values: Sequence[str], size: int) -> Iterable[Sequence[str]]:
    for start in range(0, len(values), size):
        yield values[start : start + size]


def fetch_object_data(
    object_ids: Sequence[str],
    api_url: str,
    timeout: float,
    rest_columns: Sequence[str] = (),
    batch_size: int = 250,
) -> dict[str, dict[str, Any]]:
    """Fetch LSST object summaries, coordinates, and requested REST columns."""
    data: dict[str, dict[str, Any]] = {}
    endpoint = f"{api_url.rstrip('/')}/api/v1/objects"
    columns = list(
        dict.fromkeys(
            ["r:diaObjectId", "r:ra", "r:dec", *parse_rest_columns(rest_columns)]
        )
    )
    for batch in _chunks(list(dict.fromkeys(object_ids)), batch_size):
        rows = _post_json(
            endpoint,
            {
                "diaObjectId": ",".join(batch),
                "columns": ",".join(columns),
                "output-format": "json",
            },
            timeout,
        )
        if not isinstance(rows, list):
            raise RuntimeError("Fink objects API returned a non-list response")
        for row in rows:
            if not isinstance(row, dict):
                raise ValueError("Fink objects API row must be an object")
            if "r:diaObjectId" not in row:
                raise ValueError("Fink objects API row has no diaObjectId")
            object_id = str(row["r:diaObjectId"])
            if row.get("r:ra") is not None:
                _finite_number(row["r:ra"], "RA", 0.0, 360.0)
            if row.get("r:dec") is not None:
                _finite_number(row["r:dec"], "Dec", -90.0, 90.0)
            data[object_id] = row
    return data


def object_positions(
    object_data: dict[str, dict[str, Any]],
) -> dict[str, tuple[float, float]]:
    """Extract validated coordinates from Fink object-summary rows."""
    positions: dict[str, tuple[float, float]] = {}
    for object_id, row in object_data.items():
        if row.get("r:ra") is None or row.get("r:dec") is None:
            continue
        positions[object_id] = (
            _finite_number(row["r:ra"], "RA", 0.0, 360.0),
            _finite_number(row["r:dec"], "Dec", -90.0, 90.0),
        )
    return positions


def fetch_positions(
    object_ids: Sequence[str],
    api_url: str,
    timeout: float,
    batch_size: int = 250,
) -> dict[str, tuple[float, float]]:
    """Backward-compatible coordinate-only REST helper."""
    return object_positions(
        fetch_object_data(object_ids, api_url, timeout, batch_size=batch_size)
    )


def _json_safe(value: Any) -> Any:
    """Recursively normalize non-finite JSON numbers to null."""
    if isinstance(value, float):
        return value if math.isfinite(value) else None
    if isinstance(value, dict):
        return {str(key): _json_safe(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_json_safe(item) for item in value]
    return value


def _json_ready(row: RankedNeighbor) -> dict[str, Any]:
    angular = row.angular_distance_arcsec
    return {
        "object_id": row.neighbor.object_id,
        "janusgraph_distance": row.neighbor.graph_distance,
        "angular_distance_arcsec": None if math.isinf(angular) else angular,
        "angular_distance_arcmin": None if math.isinf(angular) else angular / 60.0,
        "ra_deg": row.ra_deg,
        "dec_deg": row.dec_deg,
        "classification": row.neighbor.classes,
        "rest": _json_safe(row.rest_values),
    }


def _format_rest_value(value: Any) -> str:
    value = _json_safe(value)
    if value is None:
        return "NA"
    if isinstance(value, float):
        return f"{value:.9g}"
    if isinstance(value, (dict, list, bool, str)):
        return json.dumps(value, separators=(",", ":"), sort_keys=True)
    return str(value)


def print_table(
    rows: Sequence[RankedNeighbor],
    rest_columns: Sequence[str] = (),
    target_object_id: str | None = None,
    target_rest_values: dict[str, Any] | None = None,
) -> None:
    if rest_columns and target_object_id is not None:
        values = target_rest_values or {}
        rendered = "  ".join(
            f"{column}={_format_rest_value(values.get(column))}"
            for column in rest_columns
        )
        print(f"target  {target_object_id}  {rendered}")
    header = "rank  object_id           graph_distance  angular_arcsec  angular_arcmin"
    if rest_columns:
        header += "  " + "  ".join(rest_columns)
    print(header)
    for rank, row in enumerate(rows, 1):
        angular = row.angular_distance_arcsec
        arcsec = "NA" if math.isinf(angular) else f"{angular:.6f}"
        arcmin = "NA" if math.isinf(angular) else f"{angular / 60.0:.6f}"
        base = (
            f"{rank:>4}  {row.neighbor.object_id:<18}  "
            f"{row.neighbor.graph_distance:>14.9g}  {arcsec:>14}  {arcmin:>14}"
        )
        if rest_columns:
            base += "  " + "  ".join(
                _format_rest_value(row.rest_values.get(column))
                for column in rest_columns
            )
        print(base)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Find LSST/Fink classifier neighbors and sort them by JanusGraph "
            "distance, then angular separation."
        )
    )
    parser.add_argument("object_id", help="required LSST diaObjectId")
    parser.add_argument("--classifier", default="FINK", help="classifier (default: FINK)")
    parser.add_argument(
        "--distance",
        default="JensenShannon",
        choices=SUPPORTED_METRICS,
        help="JanusGraph distance metric (default: JensenShannon)",
    )
    parser.add_argument(
        "-n",
        "--results",
        type=float,
        default=10.0,
        help=(
            "result count when >=1; 0 returns all; 0<n<1 is Lomikel's "
            "relative distance-gap cutoff (default: 10)"
        ),
    )
    parser.add_argument(
        "--rest-columns",
        "--columns",
        action="append",
        default=[],
        metavar="COLUMN[,COLUMN...]",
        help=(
            "additional Fink REST /objects values to include; repeatable or "
            "comma-separated, e.g. r:g_psfFluxMax"
        ),
    )
    parser.add_argument("--json", action="store_true", help="emit JSON instead of a table")
    parser.add_argument(
        "--allow-insecure-graph",
        action="store_true",
        help="explicitly allow the default remote plaintext Gremlin endpoint",
    )
    parser.add_argument("--graph-url", default=DEFAULT_GRAPH_URL, help=argparse.SUPPRESS)
    parser.add_argument("--api-url", default=DEFAULT_API_URL, help=argparse.SUPPRESS)
    parser.add_argument("--timeout", type=float, default=180.0, help="HTTP timeout in seconds")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        nmax = validate_nmax(args.results)
        rest_columns = parse_rest_columns(args.rest_columns)
        validate_safe_token(args.object_id, "object ID")
        validate_safe_token(args.classifier, "classifier")
        if not math.isfinite(args.timeout) or args.timeout <= 0:
            raise ValueError("timeout must be a finite positive number")
        graph_url = validate_graph_transport(
            args.graph_url, args.allow_insecure_graph
        )
        if nmax >= 1:
            requested_count = int(nmax)
            probe_size = max(512, requested_count * 2)
            while True:
                neighbors = fetch_neighborhood(
                    args.object_id,
                    args.classifier,
                    float(probe_size),
                    args.distance,
                    graph_url,
                    args.timeout,
                )
                if tie_boundary_is_complete(neighbors, requested_count, probe_size):
                    break
                probe_size *= 2
        else:
            neighbors = fetch_neighborhood(
                args.object_id,
                args.classifier,
                nmax,
                args.distance,
                graph_url,
                args.timeout,
            )
        if not neighbors:
            raise RuntimeError(
                f"no {args.classifier} neighborhood found for {args.object_id}"
            )
        requested_ids = [args.object_id, *(row.object_id for row in neighbors)]
        object_data = fetch_object_data(
            requested_ids,
            args.api_url,
            args.timeout,
            rest_columns,
        )
        positions = object_positions(object_data)
        requested_values = {
            object_id: {column: row.get(column) for column in rest_columns}
            for object_id, row in object_data.items()
        }
        target_position = positions.get(args.object_id)
        if target_position is None:
            raise RuntimeError(f"Fink REST API has no position for {args.object_id}")
        ranked = trim_ranked_results(
            sort_neighbors(
                neighbors,
                target_position,
                positions,
                requested_values,
            ),
            nmax,
        )
    except (RuntimeError, ValueError, json.JSONDecodeError) as exc:
        parser.exit(2, f"error: {exc}\n")

    if args.json:
        output = {
            "object_id": args.object_id,
            "classifier": args.classifier,
            "distance_measure": args.distance,
            "results_parameter": nmax,
            "target": {
                "ra_deg": target_position[0],
                "dec_deg": target_position[1],
                "rest": _json_safe(requested_values.get(args.object_id, {})),
            },
            "rest_columns": rest_columns,
            "graph_candidates_considered": len(neighbors),
            "returned": len(ranked),
            "results": [_json_ready(row) for row in ranked],
        }
        print(json.dumps(output, indent=2, sort_keys=True, allow_nan=False))
    else:
        print_table(
            ranked,
            rest_columns,
            args.object_id,
            requested_values.get(args.object_id, {}),
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())

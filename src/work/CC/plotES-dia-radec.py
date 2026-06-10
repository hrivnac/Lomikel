#!/usr/bin/env python3

import argparse
import random
import requests
import matplotlib.pyplot as plt

DEFAULT_ES_URL = "http://134.158.243.139:20200"
DEFAULT_INDEX = "dia_radec"
DEFAULT_FIELD = "location"

def normalize_locations(value):
    """
    Return a list of geo_point-like values from _source[field].

    For dia_radec there should normally be exactly one point, but this
    keeps the script robust if a document contains a list.
    """
    if value is None:
        return []
    if isinstance(value, list):
        # Ambiguous case:
        #   geo_point as [lon, lat] is also a list of two numbers.
        #   multiple geo_points are a list of maps/strings/lists.
        if len(value) == 2 and all(isinstance(x, (int, float)) for x in value):
            return [value]
        return value
    return [value]

def parse_point(point):
    """
    Accept common geo_point _source forms:
      {"lat": dec, "lon": ra_minus_180}
      "lat,lon"
      [lon, lat]
    """
    if isinstance(point, dict):
        lat = float(point["lat"])
        lon = float(point["lon"])
    elif isinstance(point, str):
        lat_s, lon_s = point.split(",", 1)
        lat = float(lat_s)
        lon = float(lon_s)
    elif isinstance(point, list) and len(point) == 2:
        # Elasticsearch array geo_point convention is [lon, lat].
        lon = float(point[0])
        lat = float(point[1])
    else:
        raise ValueError(f"Unsupported geo_point format: {point!r}")
    # Your convention:
    #   lon = ra - 180
    # therefore:
    #   ra = lon + 180
    ra = lon + 180.0
    dec = lat
    return ra, dec

def es_search(es_url, index, body, scroll = None):
    url = f"{es_url}/{index}/_search"
    params = {}
    if scroll:
        params["scroll"] = scroll
    r = requests.post(url, params=params, json=body)
    r.raise_for_status()
    return r.json()

def es_scroll(es_url, scroll_id, scroll = "2m"):
    url = f"{es_url}/_search/scroll"
    body = {
        "scroll": scroll,
        "scroll_id": scroll_id,
    }
    r = requests.post(url, json = body)
    r.raise_for_status()
    return r.json()

def es_clear_scroll(es_url, scroll_id):
    if not scroll_id:
        return
    try:
        requests.delete(
            f"{es_url}/_search/scroll",
            json={"scroll_id": [scroll_id]},
            timeout = 10,
        )
    except Exception:
        pass

def collect_all_points(es_url, index, field, batch_size=1000):
    """
    Scan the index and collect all DIA RA/Dec points.

    Returns a list of:
      (doc_id, ra, dec)
    """
    body = {
        "_source": [field],
        "size": batch_size,
        "query": {
            "exists": {
                "field": field
            }
        }
    }
    data = es_search(es_url, index, body, scroll = "2m")
    scroll_id = data.get("_scroll_id")
    points = []
    n_docs = 0
    n_bad = 0
    try:
        while True:
            hits = data.get("hits", {}).get("hits", [])
            if not hits:
                break
            for hit in hits:
                n_docs += 1
                doc_id = hit["_id"]
                source = hit.get("_source", {})
                locations = normalize_locations(source.get(field))
                if not locations:
                    continue
                # DIA should have one point. If there are more, plot them all.
                for location in locations:
                    try:
                        ra, dec = parse_point(location)
                        points.append((doc_id, ra, dec))
                    except Exception as e:
                        n_bad += 1
                        print(f"Skipping bad location for {doc_id}: {location!r} ({e})")
            data = es_scroll(es_url, scroll_id, scroll="2m")
            scroll_id = data.get("_scroll_id", scroll_id)
    finally:
        es_clear_scroll(es_url, scroll_id)
    print(f"Scanned documents: {n_docs}")
    print(f"Collected points:   {len(points)}")
    if n_bad:
        print(f"Bad locations:      {n_bad}")
    return points


def subsample_points(points, max_points = None, seed = None):
    if max_points is None or max_points <= 0 or len(points) <= max_points:
        return points
    rng = random.Random(seed)
    return rng.sample(points, max_points)

def plot_points(points, output=None, invert_ra=False, marker_size = 1.0, alpha = 0.6):
    if not points:
        raise RuntimeError("No DIA RA/Dec points to plot.")
    ra_values = [p[1] for p in points]
    dec_values = [p[2] for p in points]
    plt.figure(figsize=(10, 6))
    plt.scatter(ra_values, dec_values, s = marker_size, alpha = alpha)
    plt.xlabel("ra [deg]")
    plt.ylabel("dec [deg]")
    plt.title(f"dia object positions, {len(points)} points")
    plt.grid(True)
    if invert_ra:
        plt.gca().invert_xaxis()
    plt.tight_layout()
    if output:
        plt.savefig(output, dpi = 300)
        print(f"Wrote {output}")
    else:
        plt.show()

def main():
    parser = argparse.ArgumentParser(
        description="Plot DIA object RA/Dec positions from Elasticsearch geo_point index."
    )

    parser.add_argument(
        "--es-url",
        default=DEFAULT_ES_URL,
        help=f"Elasticsearch URL, default: {DEFAULT_ES_URL}",
    )

    parser.add_argument(
        "--index",
        default=DEFAULT_INDEX,
        help=f"Index name, default: {DEFAULT_INDEX}",
    )

    parser.add_argument(
        "--field",
        default=DEFAULT_FIELD,
        help=f"geo_point field name, default: {DEFAULT_FIELD}",
    )

    parser.add_argument(
        "--batch-size",
        type=int,
        default=1000,
        help="Elasticsearch scroll batch size, default: 1000",
    )

    parser.add_argument(
        "--max-points",
        type=int,
        help="Randomly plot at most this many points.",
    )

    parser.add_argument(
        "--seed",
        type=int,
        default=12345,
        help="Random seed for reproducible subsampling, default: 12345",
    )

    parser.add_argument(
        "--marker-size",
        type=float,
        default=1.0,
        help="Scatter marker size, default: 2.0",
    )

    parser.add_argument(
        "--alpha",
        type=float,
        default=0.6,
        help="Marker transparency, default: 0.6",
    )

    parser.add_argument(
        "--invert-ra",
        action="store_true",
        help="Invert RA axis, common in astronomical plots.",
    )

    parser.add_argument(
        "--output",
        help="Output PNG file. If omitted, show interactive plot.",
    )

    args = parser.parse_args()

    points = collect_all_points(
        es_url=args.es_url,
        index=args.index,
        field=args.field,
        batch_size=args.batch_size,
    )

    plotted_points = subsample_points(
        points=points,
        max_points=args.max_points,
        seed=args.seed,
    )

    if args.max_points and len(points) > len(plotted_points):
        print(
            f"Random subsample: {len(plotted_points)} "
            f"from {len(points)} total points"
        )

    plot_points(
        points=plotted_points,
        output=args.output,
        invert_ra=args.invert_ra,
        marker_size=args.marker_size,
        alpha=args.alpha,
    )

if __name__ == "__main__":
    main()
    
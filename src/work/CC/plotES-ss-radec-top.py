#!/usr/bin/env python3

import argparse
import heapq
import requests
import matplotlib.pyplot as plt

DEFAULT_ES_URL = "http://134.158.243.139:20200"
DEFAULT_INDEX = "ss_radec"
DEFAULT_FIELD = "location"

def normalize_locations(value):
    """Return a list of geo_point-like values from _source[field]."""
    if value is None:
        return []
    if isinstance(value, list):
        return value
    return [value]

def count_locations(source, field):
    return len(normalize_locations(source.get(field)))

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
        # Elasticsearch array geo_point convention is [lon, lat]
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

def es_search(es_url, index, body, scroll=None):
    url = f"{es_url}/{index}/_search"
    params = {}
    if scroll:
        params["scroll"] = scroll
    r = requests.post(url, params=params, json=body)
    r.raise_for_status()
    return r.json()

def es_scroll(es_url, scroll_id, scroll="2m"):
    url = f"{es_url}/_search/scroll"
    body = {
        "scroll": scroll,
        "scroll_id": scroll_id,
    }
    r = requests.post(url, json=body)
    r.raise_for_status()
    return r.json()

def es_clear_scroll(es_url, scroll_id):
    if not scroll_id:
        return
    try:
        requests.delete(
            f"{es_url}/_search/scroll",
            json={"scroll_id": [scroll_id]},
            timeout=10,
        )
    except Exception:
        pass

def find_top_objects(es_url, index, field, top_n=10, batch_size=1000):
    """
    Scan the index and keep the top_n docs with the largest number of points.

    Returns a list of:
      (count, doc_id, source)
    sorted from largest count to smallest.
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
    data = es_search(es_url, index, body, scroll="2m")
    scroll_id = data.get("_scroll_id")
    heap = []
    try:
        while True:
            hits = data.get("hits", {}).get("hits", [])
            if not hits:
                break
            for hit in hits:
                source = hit.get("_source", {})
                npoints = count_locations(source, field)
                if npoints <= 0:
                    continue
                item = (npoints, hit["_id"], source)
                if len(heap) < top_n:
                    heapq.heappush(heap, item)
                elif npoints > heap[0][0]:
                    heapq.heapreplace(heap, item)
            data = es_scroll(es_url, scroll_id, scroll="2m")
            scroll_id = data.get("_scroll_id", scroll_id)
    finally:
        es_clear_scroll(es_url, scroll_id)
    return sorted(heap, key=lambda x: x[0], reverse=True)

def plot_objects(top_objects, field, output=None, invert_ra=False):
    plt.figure(figsize=(10, 7))
    for npoints, doc_id, source in top_objects:
        locations = normalize_locations(source.get(field))
        points = [parse_point(p) for p in locations]
        ra_values = [p[0] for p in points]
        dec_values = [p[1] for p in points]
        label = f"{doc_id} ({npoints})"
        plt.scatter(ra_values, dec_values, s=20, label=label)
    plt.xlabel("ra [deg]")
    plt.ylabel("dec [deg]")
    plt.title("Top 10 ss objects by number of ra/dec points")
    plt.grid(True)
    plt.legend(title="Object ID (points)", fontsize="small")
    if invert_ra:
        plt.gca().invert_xaxis()
    plt.tight_layout()
    if output:
        plt.savefig(output, dpi=150)
        print(f"Wrote {output}")
    else:
        plt.show()

def main():
    parser = argparse.ArgumentParser(
        description="Plot 10 SS objects with maximal number of geo_point locations."
    )
    parser.add_argument(
        "--es-url",
        default = DEFAULT_ES_URL,
        help = f"Elasticsearch URL, default: {DEFAULT_ES_URL}",
    )
    parser.add_argument(
        "--index",
        default = DEFAULT_INDEX,
        help = f"Index name, default: {DEFAULT_INDEX}",
    )
    parser.add_argument(
        "--field",
        default = DEFAULT_FIELD,
        help = f"geo_point field name, default: {DEFAULT_FIELD}",
    )
    parser.add_argument(
        "--top-n",
        type = int,
        default = 10,
        help = "Number of objects to plot, default: 10",
    )
    parser.add_argument(
        "--batch-size",
        type = int,
        default = 1000,
        help = "Elasticsearch scroll batch size, default: 1000",
    )
    parser.add_argument(
        "--output",
        help = "Output PNG file. If omitted, show interactive plot.",
    )
    parser.add_argument(
        "--invert-ra",
        action = "store_true",
        help = "Invert RA axis, common in astronomical plots.",
    )
    args = parser.parse_args()
    top_objects = find_top_objects(
        es_url = args.es_url,
        index = args.index,
        field = args.field,
        top_n = args.top_n,
        batch_size = args.batch_size,
    )
    if not top_objects:
        raise RuntimeError("No objects with location points were found.")
    print("Top objects:")
    for npoints, doc_id, _ in top_objects:
        print(f"  {doc_id}: {npoints} points")
    plot_objects(
        top_objects = top_objects,
        field = args.field,
        output = args.output,
        invert_ra = args.invert_ra,
    )

if __name__ == "__main__":
    main()
    
"""
scp almalinux@134.158.243.139:/home/almalinux/tmp/top.png ./
"""
    
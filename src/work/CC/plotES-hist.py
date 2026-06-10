#!/usr/bin/env python3

import argparse
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

def collect_point_counts(es_url, index, field, batch_size=1000):
    """
    Scan the index and return a list with the number of radec points
    for each object/document.
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
    counts = []
    try:
        while True:
            hits = data.get("hits", {}).get("hits", [])
            if not hits:
                break
            for hit in hits:
                source = hit.get("_source", {})
                npoints = count_locations(source, field)
                counts.append(npoints)
            data = es_scroll(es_url, scroll_id, scroll = "2m")
            scroll_id = data.get("_scroll_id", scroll_id)
    finally:
        es_clear_scroll(es_url, scroll_id)
    return counts

def plot_histogram(counts, bins = None, output = None, log_y = False, index = "", field = ""):
    if not counts:
        raise RuntimeError("No objects with radec points were found.")
    if bins is None:
        max_count = max(counts)
        bins = range(1, max_count + 2)
    plt.figure(figsize = (9, 6))
    plt.hist(counts, bins=bins, edgecolor="black")
    plt.xlabel(f"Number of {field} points per object")
    plt.ylabel("Number of objects")
    plt.title(f"Histogram of {field} point counts per {index} object")
    plt.grid(True, axis = "y")
    if log_y:
        plt.yscale("log")
        plt.ylabel("Number of objects, log scale")
    plt.tight_layout()
    if output:
        plt.savefig(output, dpi = 300)
        print(f"Wrote {output}")
    else:
        plt.show()

def print_summary(counts):
    counts_sorted = sorted(counts)
    n = len(counts_sorted)
    total_points = sum(counts_sorted)
    min_count = counts_sorted[0]
    max_count = counts_sorted[-1]
    mean_count = total_points / n
    def percentile(p):
        i = int(round((p / 100.0) * (n - 1)))
        return counts_sorted[i]
    print("Summary:")
    print(f"  objects:      {n}")
    print(f"  total points: {total_points}")
    print(f"  min points:   {min_count}")
    print(f"  max points:   {max_count}")
    print(f"  mean points:  {mean_count:.3f}")
    print(f"  median:       {percentile(50)}")
    print(f"  p90:          {percentile(90)}")
    print(f"  p99:          {percentile(99)}")

def main():
    parser = argparse.ArgumentParser(
        description="Draw histogram of number of RA/Dec geo_point values per SS object."
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
        "--batch-size",
        type = int,
        default = 1000,
        help = "Elasticsearch scroll batch size, default: 1000",
    )
    parser.add_argument(
        "--max-bin",
        type = int,
        help = "Maximum x-bin to draw. Useful if there are a few very large outliers.",
    )
    parser.add_argument(
        "--log-y",
        action = "store_true",
        help="Use logarithmic y-axis.",
    )
    parser.add_argument(
        "--output",
        help = "Output PNG file. If omitted, show interactive plot.",
    )
    args = parser.parse_args()
    counts = collect_point_counts(
        es_url = args.es_url,
        index = args.index,
        field = args.field,
        batch_size = args.batch_size,
    )
    print_summary(counts)
    if args.max_bin is not None:
        plot_counts = [c for c in counts if c <= args.max_bin]
        bins = range(1, args.max_bin + 2)
        print(f"Plotting {len(plot_counts)} objects with <= {args.max_bin} points.")
    else:
        plot_counts = counts
        bins = None
    plot_histogram(
        counts = plot_counts,
        bins = bins,
        output = args.output,
        log_y = args.log_y,
        index = args.index,
        field = args.field
    )

if __name__ == "__main__":
    main()
        
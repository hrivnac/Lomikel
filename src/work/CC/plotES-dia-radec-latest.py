#!/usr/bin/env python3

import argparse
import requests
import matplotlib.pyplot as plt

DEFAULT_ES_URL = "http://134.158.243.139:20200"
DEFAULT_RADEC_INDEX = "dia_radec"
DEFAULT_MJD_INDEX = "dia_mjd"
DEFAULT_LOCATION_FIELD = "location"
DEFAULT_MJD_FIELD = "mjd"

def normalize_locations(value):
    """
    Return a list of geo_point-like values from _source[field].

    For dia_radec there should normally be exactly one point.
    """
    if value is None:
        return []

    if isinstance(value, list):
        # geo_point as [lon, lat]
        if len(value) == 2 and all(isinstance(x, (int, float)) for x in value):
            return [value]

        # multiple points, just in case
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


def max_mjd(value):
    """
    dia_mjd may contain either one MJD value or an array of MJD values.
    Return the maximum one.
    """
    if value is None:
        return None

    if isinstance(value, list):
        if not value:
            return None
        return max(float(v) for v in value)

    return float(value)


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


def find_last_dia_ids_by_mjd(es_url, mjd_index, mjd_field, n=100000, batch_size=2000):
    """
    Find the last N DIA objects according to their maximum MJD.

    This scans dia_mjd and keeps the top N documents by max(mjd).
    This is robust whether mjd is a scalar or an array.
    """
    body = {
        "_source": [mjd_field],
        "size": batch_size,
        "query": {
            "exists": {
                "field": mjd_field
            }
        }
    }

    data = es_search(es_url, mjd_index, body, scroll="2m")
    scroll_id = data.get("_scroll_id")

    # Min-heap of (max_mjd, doc_id).
    # The smallest MJD among the kept objects is at heap[0].
    import heapq
    heap = []

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

                try:
                    mjd = max_mjd(source.get(mjd_field))
                except Exception as e:
                    n_bad += 1
                    print(f"Skipping bad MJD for {doc_id}: {source.get(mjd_field)!r} ({e})")
                    continue

                if mjd is None:
                    continue

                item = (mjd, doc_id)

                if len(heap) < n:
                    heapq.heappush(heap, item)
                elif mjd > heap[0][0]:
                    heapq.heapreplace(heap, item)

            data = es_scroll(es_url, scroll_id, scroll="2m")
            scroll_id = data.get("_scroll_id", scroll_id)

    finally:
        es_clear_scroll(es_url, scroll_id)

    result = sorted(heap, key=lambda x: x[0], reverse=True)

    print(f"Scanned dia_mjd documents: {n_docs}")
    print(f"Selected latest objects:   {len(result)}")
    if n_bad:
        print(f"Bad MJD values:            {n_bad}")

    if result:
        print(f"Newest MJD:                {result[0][0]}")
        print(f"Oldest selected MJD:       {result[-1][0]}")

    return result


def mget_radec_points(es_url, radec_index, location_field, mjd_id_pairs, chunk_size=1000):
    """
    Fetch radec locations for selected DIA object ids.

    Returns a list of:
      (doc_id, mjd, ra, dec)
    """
    points = []
    n_missing = 0
    n_bad = 0

    mjd_by_id = {doc_id: mjd for mjd, doc_id in mjd_id_pairs}
    ids = list(mjd_by_id.keys())

    for i in range(0, len(ids), chunk_size):
        chunk = ids[i:i + chunk_size]

        body = {
            "docs": [
                {
                    "_id": doc_id,
                    "_source": [location_field],
                }
                for doc_id in chunk
            ]
        }

        url = f"{es_url}/{radec_index}/_mget"
        r = requests.post(url, json=body)

        if not r.ok:
            print("Bad _mget request:")
            print(r.text)
            r.raise_for_status()

        data = r.json()

        for doc in data.get("docs", []):
            doc_id = doc["_id"]

            if not doc.get("found", False):
                n_missing += 1
                continue

            source = doc.get("_source", {})
            locations = normalize_locations(source.get(location_field))

            if not locations:
                n_missing += 1
                continue

            # DIA should have one point. If several are present, plot all.
            for location in locations:
                try:
                    ra, dec = parse_point(location)
                    points.append((doc_id, mjd_by_id[doc_id], ra, dec))
                except Exception as e:
                    n_bad += 1
                    print(f"Skipping bad location for {doc_id}: {location!r} ({e})")

    print(f"Fetched ra/dec points:     {len(points)}")
    if n_missing:
        print(f"Missing ra/dec documents:  {n_missing}")
    if n_bad:
        print(f"Bad ra/dec locations:      {n_bad}")

    return points
    
def plot_points(points, output=None, invert_ra=False, marker_size=1.0, alpha=0.6):
    if not points:
        raise RuntimeError("No dia ra/dec points to plot.")

    ra_values = [p[2] for p in points]
    dec_values = [p[3] for p in points]

    mjds = [p[1] for p in points]
    min_mjd = min(mjds)
    max_mjd = max(mjds)

    plt.figure(figsize=(10, 6))
    plt.scatter(ra_values, dec_values, s=marker_size, alpha=alpha)

    plt.xlabel("ra [deg]")
    plt.ylabel("dec [deg]")
    plt.title(
        f"Latest dia object positions, {len(points)} points\n"
        f"mjd range: {min_mjd:.5f} – {max_mjd:.5f}"
    )
    plt.grid(True)

    if invert_ra:
        plt.gca().invert_xaxis()

    plt.tight_layout()

    if output:
        plt.savefig(output, dpi=300)
        print(f"Wrote {output}")
    else:
        plt.show()


def main():
    parser = argparse.ArgumentParser(
        description="Plot latest DIA object RA/Dec positions selected by dia_mjd."
    )

    parser.add_argument(
        "--es-url",
        default=DEFAULT_ES_URL,
        help=f"Elasticsearch URL, default: {DEFAULT_ES_URL}",
    )

    parser.add_argument(
        "--radec-index",
        default=DEFAULT_RADEC_INDEX,
        help=f"RA/Dec index name, default: {DEFAULT_RADEC_INDEX}",
    )

    parser.add_argument(
        "--mjd-index",
        default=DEFAULT_MJD_INDEX,
        help=f"MJD index name, default: {DEFAULT_MJD_INDEX}",
    )

    parser.add_argument(
        "--location-field",
        default=DEFAULT_LOCATION_FIELD,
        help=f"geo_point field name, default: {DEFAULT_LOCATION_FIELD}",
    )

    parser.add_argument(
        "--mjd-field",
        default=DEFAULT_MJD_FIELD,
        help=f"MJD field name, default: {DEFAULT_MJD_FIELD}",
    )

    parser.add_argument(
        "-n",
        "--number",
        type=int,
        default=100000,
        help="Number of latest DIA objects to plot, default: 100000",
    )

    parser.add_argument(
        "--batch-size",
        type=int,
        default=2000,
        help="Elasticsearch scroll batch size for dia_mjd scan, default: 2000",
    )

    parser.add_argument(
        "--mget-chunk-size",
        type=int,
        default=1000,
        help="Number of ids per _mget request, default: 1000",
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

    latest_ids = find_last_dia_ids_by_mjd(
        es_url=args.es_url,
        mjd_index=args.mjd_index,
        mjd_field=args.mjd_field,
        n=args.number,
        batch_size=args.batch_size,
    )

    points = mget_radec_points(
        es_url=args.es_url,
        radec_index=args.radec_index,
        location_field=args.location_field,
        mjd_id_pairs=latest_ids,
        chunk_size=args.mget_chunk_size,
    )

    plot_points(
        points=points,
        output=args.output,
        invert_ra=args.invert_ra,
        marker_size=args.marker_size,
        alpha=args.alpha,
    )


if __name__ == "__main__":
    main()
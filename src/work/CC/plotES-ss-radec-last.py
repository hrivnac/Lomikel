#!/usr/bin/env python3

import argparse
import heapq
import requests
import matplotlib.pyplot as plt


DEFAULT_ES_URL = "http://134.158.243.139:20200"

DEFAULT_RADEC_INDEX = "ss_radec"
DEFAULT_MJD_INDEX = "ss_mjd"

DEFAULT_LOCATION_FIELD = "location"
DEFAULT_MJD_FIELD = "mjd"


def normalize_locations(value):
    """Return a list of geo_point-like values from _source[field]."""
    if value is None:
        return []

    if isinstance(value, list):
        # Single geo_point encoded as [lon, lat]
        if len(value) == 2 and all(isinstance(x, (int, float)) for x in value):
            return [value]

        # Multiple geo_points
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


def max_mjd(value):
    """Return maximum MJD from scalar or array."""
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

    if not r.ok:
        print("Bad _search request:")
        print(r.text)
        r.raise_for_status()

    return r.json()


def es_scroll(es_url, scroll_id, scroll="2m"):
    url = f"{es_url}/_search/scroll"
    body = {
        "scroll": scroll,
        "scroll_id": scroll_id,
    }

    r = requests.post(url, json=body)

    if not r.ok:
        print("Bad _scroll request:")
        print(r.text)
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


def find_latest_ss_ids_by_mjd(es_url, mjd_index, mjd_field, n=10, batch_size=2000):
    """
    Scan ss_mjd and keep N SS objects with largest max(mjd).

    Returns list of:
      (max_mjd, doc_id)
    sorted newest first.
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
                doc_id = str(hit["_id"]).strip()
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

    print(f"Scanned ss_mjd documents: {n_docs}")
    print(f"Selected SS objects:      {len(result)}")

    if n_bad:
        print(f"Bad MJD values:           {n_bad}")

    if result:
        print(f"Newest MJD:               {result[0][0]}")
        print(f"Oldest selected MJD:      {result[-1][0]}")

    return result


def mget_ss_radec(es_url, radec_index, location_field, mjd_id_pairs, chunk_size=100):
    """
    Fetch ss_radec locations for selected SS object ids.

    Returns list of:
      (doc_id, mjd, points)

    where points is:
      [(ra, dec), ...]
    """
    mjd_by_id = {doc_id: mjd for mjd, doc_id in mjd_id_pairs}
    ids = list(mjd_by_id.keys())

    objects = []
    n_missing = 0
    n_bad = 0

    for i in range(0, len(ids), chunk_size):
        chunk = ids[i:i + chunk_size]
        chunk = [str(x).strip() for x in chunk]

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
            doc_id = str(doc["_id"]).strip()

            if not doc.get("found", False):
                n_missing += 1
                continue

            source = doc.get("_source", {})
            locations = normalize_locations(source.get(location_field))

            if not locations:
                n_missing += 1
                continue

            points = []

            for location in locations:
                try:
                    points.append(parse_point(location))
                except Exception as e:
                    n_bad += 1
                    print(f"Skipping bad location for {doc_id}: {location!r} ({e})")

            if points:
                objects.append((doc_id, mjd_by_id[doc_id], points))

    print(f"Fetched SS objects:        {len(objects)}")

    if n_missing:
        print(f"Missing ss_radec docs:     {n_missing}")

    if n_bad:
        print(f"Bad RA/Dec locations:      {n_bad}")

    return objects


def plot_objects(objects, output=None, invert_ra=False, marker_size=20.0):
    if not objects:
        raise RuntimeError("No SS RA/Dec points to plot.")

    plt.figure(figsize=(10, 7))

    for doc_id, mjd, points in objects:
        ra_values = [p[0] for p in points]
        dec_values = [p[1] for p in points]

        label = f"{doc_id}, {len(points)} pts, mjd={mjd:.5f}"
        plt.scatter(ra_values, dec_values, s=marker_size, label=label)

        # Optional: connect points of each moving object.
        if len(points) > 1:
            plt.plot(ra_values, dec_values, linewidth=1, alpha=0.7)

    plt.xlabel("RA [deg]")
    plt.ylabel("Dec [deg]")
    plt.title(f"Latest SS objects by ss_mjd, {len(objects)} objects")
    plt.grid(True)
    plt.legend(title="Object, points, latest MJD", fontsize="small")

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
        description="Plot SS objects selected by latest hits from ss_mjd."
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
        default=10,
        help="Number of latest SS objects to plot, default: 10",
    )

    parser.add_argument(
        "--batch-size",
        type=int,
        default=2000,
        help="Elasticsearch scroll batch size for ss_mjd scan, default: 2000",
    )

    parser.add_argument(
        "--mget-chunk-size",
        type=int,
        default=100,
        help="Number of ids per _mget request, default: 100",
    )

    parser.add_argument(
        "--marker-size",
        type=float,
        default=20.0,
        help="Scatter marker size, default: 20.0",
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

    latest_ids = find_latest_ss_ids_by_mjd(
        es_url=args.es_url,
        mjd_index=args.mjd_index,
        mjd_field=args.mjd_field,
        n=args.number,
        batch_size=args.batch_size,
    )

    objects = mget_ss_radec(
        es_url=args.es_url,
        radec_index=args.radec_index,
        location_field=args.location_field,
        mjd_id_pairs=latest_ids,
        chunk_size=args.mget_chunk_size,
    )

    plot_objects(
        objects=objects,
        output=args.output,
        invert_ra=args.invert_ra,
        marker_size=args.marker_size,
    )


if __name__ == "__main__":
    main()
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


def find_latest_ss_ids_by_mjd(es_url, mjd_index, mjd_field, n_candidates=100, batch_size=2000):
    """
    Scan ss_mjd and keep n_candidates SS objects with largest max(mjd).

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

                if len(heap) < n_candidates:
                    heapq.heappush(heap, item)
                elif mjd > heap[0][0]:
                    heapq.heapreplace(heap, item)

            data = es_scroll(es_url, scroll_id, scroll="2m")
            scroll_id = data.get("_scroll_id", scroll_id)

    finally:
        es_clear_scroll(es_url, scroll_id)

    result = sorted(heap, key=lambda x: x[0], reverse=True)

    print(f"Scanned ss_mjd documents: {n_docs}")
    print(f"Selected MJD candidates:  {len(result)}")

    if n_bad:
        print(f"Bad MJD values:           {n_bad}")

    if result:
        print(f"Newest MJD candidate:     {result[0][0]}")
        print(f"Oldest MJD candidate:     {result[-1][0]}")

    return result


def mget_ss_radec(es_url, radec_index, location_field, mjd_id_pairs, min_points=10, chunk_size=100):
    """
    Fetch ss_radec locations for selected SS object ids.

    Keeps only objects with at least min_points.

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
    n_too_short = 0

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

            if len(points) < min_points:
                n_too_short += 1
                continue

            objects.append((doc_id, mjd_by_id[doc_id], points))

    print(f"Fetched SS objects:        {len(objects)}")
    print(f"Required min points:       {min_points}")

    if n_too_short:
        print(f"Rejected short curves:     {n_too_short}")

    if n_missing:
        print(f"Missing ss_radec docs:     {n_missing}")

    if n_bad:
        print(f"Bad RA/Dec locations:      {n_bad}")

    return objects


def find_latest_ss_objects_with_min_points(
    es_url,
    mjd_index,
    radec_index,
    mjd_field,
    location_field,
    number=10,
    min_points=10,
    batch_size=2000,
    mget_chunk_size=100,
    candidate_factor=20,
):
    """
    Select the latest SS objects, but keep only curves with at least min_points.

    Since the min-points condition lives in ss_radec, while latest selection
    lives in ss_mjd, we first select more MJD candidates, then filter them.
    """
    n_candidates = max(number * candidate_factor, number)

    while True:
        latest_ids = find_latest_ss_ids_by_mjd(
            es_url=es_url,
            mjd_index=mjd_index,
            mjd_field=mjd_field,
            n_candidates=n_candidates,
            batch_size=batch_size,
        )

        objects = mget_ss_radec(
            es_url=es_url,
            radec_index=radec_index,
            location_field=location_field,
            mjd_id_pairs=latest_ids,
            min_points=min_points,
            chunk_size=mget_chunk_size,
        )

        objects = sorted(objects, key=lambda x: x[1], reverse=True)

        if len(objects) >= number:
            return objects[:number]

        if len(latest_ids) < n_candidates:
            # We already reached the available data.
            print(
                f"Only found {len(objects)} objects with at least "
                f"{min_points} points."
            )
            return objects

        n_candidates *= 2
        print(
            f"Only found {len(objects)} good objects; "
            f"increasing MJD candidates to {n_candidates}."
        )


def plot_objects(
    objects,
    output=None,
    invert_ra=False,
    marker_size=1.0,
    line_width=1.0,
    line_alpha=0.7,
):
    if not objects:
        raise RuntimeError("No SS RA/Dec points to plot.")

    plt.figure(figsize=(10, 7))

    for doc_id, mjd, points in objects:
        ra_values = [p[0] for p in points]
        dec_values = [p[1] for p in points]

        label = f"{doc_id}, {len(points)} pts, mjd={mjd:.5f}"

        scatter = plt.scatter(
            ra_values,
            dec_values,
            s=marker_size,
            label=label,
        )

        # Use exactly the same color for the connecting line as for the points.
        #color = scatter.get_facecolor()[0]
        #
        #if len(points) > 1:
        #    plt.plot(
        #        ra_values,
        #        dec_values,
        #        linewidth=line_width,
        #        alpha=line_alpha,
        #        color=color,
        #    )

    plt.xlabel("ra [deg]")
    plt.ylabel("dec [deg]")
    plt.title(f"Latest ss objects by ss_mjd, {len(objects)} objects")
    plt.grid(True)
    plt.legend(title="Object, points, latest mjd", fontsize="small")

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
        description="Plot latest SS objects from ss_mjd with at least N RA/Dec points."
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
        "--min-points",
        type=int,
        default=10,
        help="Minimum number of RA/Dec points per SS curve, default: 10",
    )

    parser.add_argument(
        "--candidate-factor",
        type=int,
        default=20,
        help=(
            "How many more latest-MJD candidates to inspect before filtering "
            "by min-points, default: 20"
        ),
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
        default=4.0,
        help="Scatter marker size, default: 20.0",
    )

    parser.add_argument(
        "--line-width",
        type=float,
        default=1.0,
        help="Connecting line width, default: 1.0",
    )

    parser.add_argument(
        "--line-alpha",
        type=float,
        default=0.7,
        help="Connecting line transparency, default: 0.7",
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

    objects = find_latest_ss_objects_with_min_points(
        es_url=args.es_url,
        mjd_index=args.mjd_index,
        radec_index=args.radec_index,
        mjd_field=args.mjd_field,
        location_field=args.location_field,
        number=args.number,
        min_points=args.min_points,
        batch_size=args.batch_size,
        mget_chunk_size=args.mget_chunk_size,
        candidate_factor=args.candidate_factor,
    )

    plot_objects(
        objects=objects,
        output=args.output,
        invert_ra=args.invert_ra,
        marker_size=args.marker_size,
        line_width=args.line_width,
        line_alpha=args.line_alpha,
    )


if __name__ == "__main__":
    main()
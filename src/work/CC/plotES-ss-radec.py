#!/usr/bin/env python3

import sys
import requests
import matplotlib.pyplot as plt

ES_URL = "http://134.158.243.139:20200"
INDEX = "ss_radec"
FIELD = "location"

def get_document(ss_id):
    url = f"{ES_URL}/{INDEX}/_doc/{ss_id}"
    r = requests.get(url)
    r.raise_for_status()
    data = r.json()
    if not data.get("found", False):
        raise RuntimeError(f"Document not found: {ss_id}")
    return data["_source"]

def normalize_locations(value):
    """Return list of geo_point-like values."""
    if value is None:
        return []
    if isinstance(value, list):
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
    ra = lon + 180.0
    dec = lat
    return ra, dec

def main():
    if len(sys.argv) != 2:
        print(f"Usage: {sys.argv[0]} SS_OBJECT_ID")
        sys.exit(1)
    ss_id = sys.argv[1]
    source = get_document(ss_id)
    locations = normalize_locations(source.get(FIELD))
    points = [parse_point(p) for p in locations]
    if not points:
        raise RuntimeError(f"No points found in field '{FIELD}' for {ss_id}")
    ra_values = [p[0] for p in points]
    dec_values = [p[1] for p in points]
    plt.figure(figsize = (8, 5))
    plt.scatter(ra_values, dec_values, s = 4)
    plt.xlabel("ra [deg]")
    plt.ylabel("dec [deg]")
    plt.title(f"ra/dec points for ss object {ss_id}")
    plt.grid(True)
    # Optional astronomical convention: RA increases to the left.
    plt.gca().invert_xaxis()
    plt.tight_layout()
    #plt.show()
    plt.savefig(f"{ss_id}.png", dpi=300)

if __name__ == "__main__":
    main()
    
"""
curl -X GET 'http://134.158.243.139:20200/ss_radec/_search?pretty=true' \
  -H 'Content-Type: application/json' \
  -d '{
    "query": {
      "script": {
        "script": {
          "lang": "painless",
          "source": "doc[\"location\"].size() > 50"
        }
      }
    }
  }'

scp almalinux@134.158.243.139:/home/almalinux/tmp/21163611358705234.png ./
"""

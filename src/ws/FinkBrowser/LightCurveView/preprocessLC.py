import pandas as pd
import numpy as np
import json
from pathlib import Path

# Input LSST lightcurves CSV
input_file = "lc.csv"
output_dir = Path("sn_lightcurves")
output_dir.mkdir(exist_ok=True)

# Load CSV
df = pd.read_csv(input_file)

# Clean BAND column (turn "b'LSST-r    '" into "r")
df["BAND"] = df["BAND"].str.extract(r"LSST-(\w)").iloc[:, 0]

# Keep only needed columns
df = df[["SNID", "MJD", "BAND", "magnitude"]].dropna()

# List of expected bands
bands = ["Y", "z", "g", "i", "u", "r"]

def unify_lightcurve(group, bands):
    """
    Given one SNID group, return dict in generateDemoData format:
    {
      "Y": { "times": [...], "values": [...] },
      "z": { "times": [...], "values": [...] },
      ...
    }
    """
    # Collect union of all MJDs across all bands
    mjd_union = sorted(group["MJD"].unique())

    unified = {}
    for b in bands:
        sub = group[group["BAND"] == b]
        if sub.empty:
            # band completely missing
            unified[b] = {"times": [float(m) for m in mjd_union],
                          "values": [0.0]*len(mjd_union)}
            continue

        xs = sub["MJD"].values
        ys = sub["magnitude"].values

        values = []
        for mjd in mjd_union:
            if mjd < xs.min() or mjd > xs.max():
                values.append(0.0)  # outside observed range
            else:
                values.append(float(np.interp(mjd, xs, ys)))

        unified[b] = {"times": [float(m) for m in mjd_union],
                      "values": values}

    return unified

# Process each SNID
for snid, group in df.groupby("SNID"):
    unified = unify_lightcurve(group, bands)

    with open(output_dir / f"{snid}.json", "w") as f:
        json.dump(unified, f, indent=2)

print(f"Saved unified per-SNID files into {output_dir}/")

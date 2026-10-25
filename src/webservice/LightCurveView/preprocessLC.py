#!/usr/bin/env python3
"""Convert simulated LSST light curves to the browser's sparse JSON format."""

import argparse
import json
from pathlib import Path

import pandas as pd

BANDS = ["Y", "z", "g", "i", "u", "r"]


def unify_lightcurve(group, bands=BANDS):
    """Return sorted, duplicate-averaged observations for every requested band."""
    unified = {}
    for band in bands:
        samples = group.loc[group["BAND"] == band, ["MJD", "magnitude"]].copy()
        samples["MJD"] = pd.to_numeric(samples["MJD"], errors="coerce")
        samples["magnitude"] = pd.to_numeric(samples["magnitude"], errors="coerce")
        samples = samples.dropna()
        samples = samples.groupby("MJD", as_index=False, sort=True)["magnitude"].mean()
        unified[band] = {
            "times": [float(value) for value in samples["MJD"]],
            "values": [float(value) for value in samples["magnitude"]],
        }
    return unified


def preprocess(input_file, output_dir):
    """Read a simulation CSV and write one sparse JSON light curve per SNID."""
    frame = pd.read_csv(input_file)
    required = {"SNID", "MJD", "BAND", "magnitude"}
    missing = required.difference(frame.columns)
    if missing:
        raise ValueError(f"Missing required columns: {', '.join(sorted(missing))}")

    frame = frame[["SNID", "MJD", "BAND", "magnitude"]].copy()
    frame["BAND"] = frame["BAND"].astype(str).str.extract(r"LSST-(\w)", expand=False).fillna(frame["BAND"])
    frame = frame.dropna(subset=["SNID", "MJD", "BAND", "magnitude"])

    output_dir.mkdir(parents=True, exist_ok=True)
    count = 0
    for snid, group in frame.groupby("SNID", sort=True):
        with (output_dir / f"{snid}.json").open("w", encoding="utf-8") as stream:
            json.dump(unify_lightcurve(group), stream, indent=2, allow_nan=False)
        count += 1
    return count


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", nargs="?", default="lc.csv", type=Path)
    parser.add_argument("--output-dir", default=Path("sn_lightcurves"), type=Path)
    args = parser.parse_args()
    count = preprocess(args.input, args.output_dir)
    print(f"Saved {count} sparse per-SNID files into {args.output_dir}/")


if __name__ == "__main__":
    main()

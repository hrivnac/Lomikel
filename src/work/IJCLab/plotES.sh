#!/usr/bin/env bash
set -euo pipefail

# ZTF Elasticsearch plots handled by IJCLab. ZTF has one data type, so there
# is no ss/dia split. The radec index currently has one location per object,
# so top-location and location-count histogram plots are intentionally omitted.
python3 ../src/work/IJCLab/plotES-ztf-radec.py              --max-points 100000              --output z_ztf_radec.png
python3 ../src/work/IJCLab/plotES-ztf-radec-latest.py       --number 100000                  --output z_ztf_radec_latest.png
python3 ../src/work/IJCLab/plotES-ztf-hist.py --index mjd   --field mjd      --log-y         --output z_h_ztf_mjd.png

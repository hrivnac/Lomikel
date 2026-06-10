python3 ../src/work/CC/plotES-dia-radec.py    --max-points 100000 --output dia_radec.png
python3 ../src/work/CC/plotES-ss-radec-top.py                     --output ss_radec.png
python3 ../src/work/CC/plotES-hist.py --index ss_radec --field location --log-y --output ss_radec.png
python3 ../src/work/CC/plotES-hist.py --index ss_mjd   --field mjd      --log-y --output ss_mjd.png
python3 ../src/work/CC/plotES-hist.py --index dia_mjd  --field mjd      --log-y --output dia_mjd.png

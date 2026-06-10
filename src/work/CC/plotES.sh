python3 ../src/work/CC/plotES-dia-radec.py    --max-points 100000 --output s_dia_radec.png
python3 ../src/work/CC/plotES-ss-radec-top.py                     --output s_ss_radec.png
python3 ../src/work/CC/plotES-hist.py --index ss_radec --field location --log-y --output h_ss_radec.png
python3 ../src/work/CC/plotES-hist.py --index ss_mjd   --field mjd      --log-y --output h_ss_mjd.png
python3 ../src/work/CC/plotES-hist.py --index dia_mjd  --field mjd      --log-y --output h_dia_mjd.png
echo "scp almalinux@134.158.243.139:/home/almalinux/Lomikel/ant/'*'.png ./"
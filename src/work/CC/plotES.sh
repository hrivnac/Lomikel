python3 ../src/work/CC/plotES-dia-radec.py      --max-points 100000             --output s_dia_radec.png
python3 ../src/work/CC/plotES-dia-radec-last.py --number 100000                 --output s_dia_radec_last.png
python3 ../src/work/CC/plotES-ss-radec-top.py                                   --output s_ss_radec_top.png
python3 ../src/work/CC/plotES-ss-radec-last.py        --number 10      --min-points 10                      --output s_ss_radec_last.png
python3 ../src/work/CC/plotES-hist.py --index ss_radec --field location --log-y --output h_ss_radec.png
python3 ../src/work/CC/plotES-hist.py --index ss_mjd   --field mjd      --log-y --output h_ss_mjd.png
python3 ../src/work/CC/plotES-hist.py --index dia_mjd  --field mjd      --log-y --output h_dia_mjd.png
echo "scp almalinux@134.158.243.139:/home/almalinux/Lomikel/ant/'*'.png ./"

python3 plot_ss_radec_latest.py -n 10 --output latest_ss_radec.png
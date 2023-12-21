#!/usr/bin/bash -l
Y=${1}
M=${2}
D=${3}
LOG=/tmp/upload.${Y}.${M}.${D}
cd ~/Lomikel/ant
source ./setup.sh
for X in 0 1 2 3 4 5 6 7 8 9; do
  /bin/rm -f ${LOG}${X}.log
  ../src/sh/importAvroHDFS-IJCLab.sh /user/julien.peloton/archive_avro/science/year=${Y}/month=${M}/day=${D}${X} alert | tee ${LOG}${X}.log 2>&1
  done

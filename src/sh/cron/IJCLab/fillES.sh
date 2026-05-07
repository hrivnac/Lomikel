#!/usr/bin/bash -l
NOW=`date +"%Y%m%d%H%M%s"`
LOG=/tmp/fillES-radec-${NOW}.log
LOCK=/tmp/fillES-radec.lock 
if [[ -e ${LOCK} ]]; then
  echo "Already filling ES-radec with ${LOCK}"
  exit
  fi
PID=$$
echo ${PID} > ${LOCK}
cd ~/Lomikel/ant
source ./setup.sh
java --add-opens=java.base/java.lang=ALL-UNNAMED -jar ~/Lomikel/lib/Lomikel-All-${version}.exe.jar -b -s ~/Lomikel/src/work/IJCLab/fillES.groovy | tee -a ${LOG} 2>&1
/bin/rm -f ${LOCK} 
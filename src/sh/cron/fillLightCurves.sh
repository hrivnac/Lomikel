#!/usr/bin/bash -l
NOW=`date +"%Y%m%d%H%M%s"`
LOG=/tmp/fillLC-${NOW}.log
LOCK=/tmp/fillLC.lock 
if [[ -e ${LOCK} ]]; then
  echo "Already filling LightCurves with ${LOCK}"
  exit
  fi
PID=$$
echo ${PID} > ${LOCK}
cd ~/Lomikel/ant
source ./setup.sh
java -jar ~/Lomikel/lib/Lomikel-HBase.exe.jar -b -s ~/Lomikel/src/work/fillLightCurves.groovy | tee -a ${LOG} 2>&1
/bin/rm -f ${LOCK} 
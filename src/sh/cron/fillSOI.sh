#!/usr/bin/bash -l
NOW=`date +"%Y%m%d%H%M%s"`
LOG=/tmp/fillSOI-${NOW}.log
LOCK=/tmp/fillSOI.lock 
if [[ -e ${LOCK} ]]; then
  echo "Already filling SoI with ${LOCK}"
  exit
  fi
PID=$$
echo ${PID} > ${LOCK}
cd ~/Lomikel/ant
source ./setup.sh
java -jar ~/Lomikel/lib/Lomikel-Janus-${version}.exe.jar -b -s ~/Lomikel/src/work/fillSOI.groovy | tee -a ${LOG} 2>&1
/bin/rm -f ${LOCK}

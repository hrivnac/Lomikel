#!/usr/bin/bash -l
NOW=`date +"%Y%m%d%H%M%s"`
LOG=/tmp/fillES-${NOW}.log
LOCK=/tmp/fillES.lock 
if [[ -e ${LOCK} ]]; then
  echo "Already filling ES with ${LOCK}"
  exit
  fi
PID=$$
echo ${PID} > ${LOCK}
cd ~/Lomikel/ant
source ./setup.sh
java -jar ~/Lomikel/lib/Lomikel-HBase-${version}.exe.jar -b -s ~/Lomikel/src/work/fillES.groovy | tee -a ${LOG} 2>&1
/bin/rm -f ${LOCK} 
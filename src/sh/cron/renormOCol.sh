#!/usr/bin/bash -l                                                                                                                                                                                                                                                
NOW=`date +"%Y%m%d%H%M%s"`
LOG=/tmp/renormOCol-${NOW}.log
LOCK=/tmp/renormOCol.lock
if [[ -e ${LOCK} ]]; then
  echo "Already renorming ${LOCK}"
  exit
  fi
PID=$$
echo ${PID} > ${LOCK}
cd ~/Lomikel/ant
source ./setup.sh
java -jar ~/Lomikel/lib/Lomikel-Janus-${version}.exe.jar -b -s ~/Lomikel/src/work/renormOCol.groovy | tee -a ${LOG} 2>&1
/bin/rm -f ${LOCK}


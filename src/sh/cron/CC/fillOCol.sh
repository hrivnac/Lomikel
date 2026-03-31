#!/usr/bin/bash -l
NOW=`date +"%Y%m%d%H%M%s"`
LOG=/tmp/fillOCol-${NOW}.log
LOCK=/tmp/fillOCol.lock 
if [[ -e ${LOCK} ]]; then
  echo "Already filling OCol with ${LOCK}"
  exit
  fi
PID=$$
echo ${PID} > ${LOCK}
cd ~/Lomikel/ant
source ./setup.sh
java --add-opens=java.base/java.lang=ALL-UNNAMED -jar ~/Lomikel/lib/Lomikel-All-${version}.exe.jar -b -s ~/Lomikel/src/work/CC/fillOCol.groovy | tee -a ${LOG} 2>&1
/bin/rm -f ${LOCK} 
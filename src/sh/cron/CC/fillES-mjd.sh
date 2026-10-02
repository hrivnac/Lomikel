#!/usr/bin/bash -l
set -eo pipefail
NOW=`date +"%Y%m%d%H%M%s"`
LOG=/tmp/fillES-mjd-${NOW}.log
LOCK=/tmp/fillES-mjd.lock 
if [[ -e ${LOCK} ]]; then
  echo "Already filling ES-mjd with ${LOCK}"
  exit
  fi
PID=$$
echo ${PID} > ${LOCK}

exec > >(tee -a "${LOG}") 2>&1 || exit 1
cd ~/Lomikel/ant
source ./setup.sh
java --add-opens=java.base/java.lang=ALL-UNNAMED -jar ~/Lomikel/lib/Lomikel-All-${version}.exe.jar -b -s ~/Lomikel/src/work/CC/fillES-mjd.groovy
/bin/rm -f ${LOCK} 


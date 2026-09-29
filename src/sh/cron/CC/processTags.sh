#!/usr/bin/bash -l
set -eo pipefail
LOG=/tmp/processTags-${NOW}-XXXXXXXX.log
LOCK=/tmp/processTags.lock 
if [[ -e ${LOCK} ]]; then
  echo "Already filling ES-radec with ${LOCK}"
  exit
  fi
PID=$$
echo ${PID} > ${LOCK}

exec > >(tee -a "${LOG}") 2>&1 || exit 1
cd ~/Lomikel/ant
source ./setup.sh
java -jar ~/Lomikel/lib/Lomikel-Janus-${version}.exe.jar -b -s ~/Lomikel/src/work/CC/processTags.groovy
/bin/rm -f ${LOCK} 
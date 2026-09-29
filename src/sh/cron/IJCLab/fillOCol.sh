#!/usr/bin/bash -l
set -eo pipefail
LOG=/tmp/fillOCol-${NOW}-XXXXXXXX.log
LOCK=/tmp/fillOCol.lock 
if [[ -e ${LOCK} ]]; then
  echo "Already filling OCol with ${LOCK}"
  exit
  fi
PID=$$
echo ${PID} > ${LOCK}

exec > >(tee -a "${LOG}") 2>&1 || exit 1
cd ~/Lomikel/ant || exit 1
source ./setup.sh || exit 1
exec java -jar ~/Lomikel/lib/Lomikel-Janus-${version}.exe.jar -b -s ~/Lomikel/src/work/IJCLab/fillOCol.groovy
ll ${LOCK}
/bin/rm -f ${LOCK} 
ll ${LOCK}
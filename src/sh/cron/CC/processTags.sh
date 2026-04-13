#!/usr/bin/bash -l
NOW=`date +"%Y%m%d%H%M%s"`
LOG=/tmp/processTags-${NOW}.log
LOCK=/tmp/processTags.lock 
if [[ -e ${LOCK} ]]; then
  echo "Already processing OCol with ${LOCK}"
  exit
  fi
PID=$$
echo ${PID} > ${LOCK}
cd ~/Lomikel/ant
source ./setup.sh
java -jar ~/Lomikel/lib/Lomikel-Janus-${version}.exe.jar -b -s ~/Lomikel/src/work/CC/processTags.groovy | tee -a ${LOG} 2>&1
/bin/rm -f ${LOCK} 
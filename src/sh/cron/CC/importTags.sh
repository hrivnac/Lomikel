#!/usr/bin/bash -l
NOW=`date +"%Y%m%d%H%M%s"`
LOG=/tmp/importTags-${NOW}.log
LOCK=/tmp/importTags.lock 
if [[ -e ${LOCK} ]]; then
  echo "Already filling OCol with ${LOCK}"
  exit
  fi
PID=$$
echo ${PID} > ${LOCK}
cd ~/Lomikel/ant
source ./setup.sh
java -jar ~/Lomikel/lib/Lomikel-Janus-${version}.exe.jar -b -s ~/Lomikel/src/work/CC/importTags.groovy -o "cls='rubin.tag_early_snia_candidate'" | tee -a ${LOG} 2>&1
/bin/rm -f ${LOCK} 
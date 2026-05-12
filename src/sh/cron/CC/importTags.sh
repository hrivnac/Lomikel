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
java -jar ~/Lomikel/lib/Lomikel-Janus-${version}.exe.jar -b -s ~/Lomikel/src/work/CC/cleanTags.groovy | tee -a ${LOG} 2>&1
for T in rubin.tag_early_snia_candidate \
         rubin.tag_extragalactic_lt20mag_candidate \
         rubin.tag_extragalactic_new_candidate \
         rubin.tag_good_quality \
         rubin.tag_hostless_candidate \
         rubin.tag_in_tns \
         rubin.tag_sn_near_galaxy_candidate; do
  java -jar ~/Lomikel/lib/Lomikel-Janus-${version}.exe.jar -b -s ~/Lomikel/src/work/CC/importTags.groovy -o "cls='${T}',delay=2" | tee -a ${LOG} 2>&1
  done
for T in rubin.tag_uniform_sample; do
  java -jar ~/Lomikel/lib/Lomikel-Janus-${version}.exe.jar -b -s ~/Lomikel/src/work/CC/importTags.groovy -o "cls='${T}',delay=200" | tee -a ${LOG} 2>&1
  done
/bin/rm -f ${LOCK} 

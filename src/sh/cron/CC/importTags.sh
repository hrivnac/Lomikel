#!/usr/bin/bash -l
set -eo pipefail
NOW=`date +"%Y%m%d%H%M%s"`
LOG=/tmp/importTags-${NOW}.log
LOCK=/tmp/importTags.lock 
if [[ -e ${LOCK} ]]; then
  echo "Already importing Tags with ${LOCK}"
  exit
  fi
PID=$$
echo ${PID} > ${LOCK}

exec > >(tee -a "${LOG}") 2>&1 || exit 1
cd ~/Lomikel/ant
source ./setup.sh
for T in rubin.tag_extragalactic_svom \
         rubin.tag_faint_trails \
         rubin.tag_fast_transient_gvom \
         rubin.tag_most_likely_sn \
         rubin.tag_remove_unlikely_transients \
         rubin.tag_uniform_sample \
         rubin.tag_extragalactic_lt20mag_candidate \
         rubin.tag_extragalactic_new_candidate \
         rubin.tag_good_quality \
         rubin.tag_hostless_candidate \
         rubin.tag_in_tns \
         rubin.tag_sn_near_galaxy_candidate \
         rubin.tag_uniform_sample; do
  java -jar ~/Lomikel/lib/Lomikel-Janus-${version}.exe.jar -b -s ~/Lomikel/src/work/CC/importTags.groovy -o "cls='${T}',delay=1"
  java -jar ~/Lomikel/lib/Lomikel-Janus-${version}.exe.jar -b -s ~/Lomikel/src/work/CC/importTags.groovy -o "cls='${T}',delay=2"
  done
java -jar ~/Lomikel/lib/Lomikel-Janus-${version}.exe.jar -b -s ~/Lomikel/src/work/CC/processTags.groovy
java -jar ~/Lomikel/lib/Lomikel-Janus-${version}.exe.jar -b -s ~/Lomikel/src/work/CC/cleanTags.groovy
/bin/rm -f ${LOCK} 


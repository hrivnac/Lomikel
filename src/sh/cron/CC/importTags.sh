#!/usr/bin/bash -l
set -eo pipefail
TAGS_LIST=~/Lomikel/src/sh/cron/CC/LSSTTags.lst
if [[ ! -s "${TAGS_LIST}" ]]; then
  echo "Missing or empty LSST tag table list: ${TAGS_LIST}" >&2
  exit 1
  fi
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
mapfile -t TAGS < "${TAGS_LIST}"
if ((${#TAGS[@]} == 0)); then
  echo "Empty LSST tag table list: ${TAGS_LIST}" >&2
  exit 1
  fi
declare -A SEEN=()
for T in "${TAGS[@]}"; do
  if [[ ! "${T}" =~ ^rubin\.tag_[a-z0-9_]+$ ]]; then
    echo "Invalid LSST tag table entry in ${TAGS_LIST}" >&2
    exit 1
    fi
  if [[ -n ${SEEN[$T]+x} ]]; then
    echo "Repeated LSST tag table entry in ${TAGS_LIST}" >&2
    exit 1
    fi
  SEEN[$T]=1
  done
for T in "${TAGS[@]}"; do
  java -jar ~/Lomikel/lib/Lomikel-Janus-${version}.exe.jar -b -s ~/Lomikel/src/work/CC/importTags.groovy -o "cls='${T}',delay=1"
  java -jar ~/Lomikel/lib/Lomikel-Janus-${version}.exe.jar -b -s ~/Lomikel/src/work/CC/importTags.groovy -o "cls='${T}',delay=2"
  done
java -jar ~/Lomikel/lib/Lomikel-Janus-${version}.exe.jar -b -s ~/Lomikel/src/work/CC/processTags.groovy
java -jar ~/Lomikel/lib/Lomikel-Janus-${version}.exe.jar -b -s ~/Lomikel/src/work/CC/cleanTags.groovy
/bin/rm -f ${LOCK} 
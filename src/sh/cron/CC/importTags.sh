#!/usr/bin/bash -l
set -eo pipefail
NOW=$(date +"%Y%m%d%H%M%s")
LOG=$(mktemp "${TMPDIR:-/tmp}/importTags-${NOW}-XXXXXXXX.log")
LOCK_DIR="/tmp"
mkdir -p -m 700 "${LOCK_DIR}"
LOCK="${LOCK_DIR}/importTags.lock"

# Keep the lock file: removing a flock file permits two different inodes/owners.
exec 9>"${LOCK}"
if ! flock -n 9; then
  echo "Already importing tags with ${LOCK}" >&2
  exit 75
  fi
  
exec > >(tee -a "${LOG}") 2>&1 || exit 1
cd ~/Lomikel/ant
source ./setup.sh
java -jar ~/Lomikel/lib/Lomikel-Janus-${version}.exe.jar -b -s ~/Lomikel/src/work/CC/cleanTags.groovy 2>&1 | tee -a "${LOG}"
for T in rubin.tag_early_snia_candidate \
         rubin.tag_extragalactic_lt20mag_candidate \
         rubin.tag_extragalactic_new_candidate \
         rubin.tag_good_quality \
         rubin.tag_hostless_candidate \
         rubin.tag_in_tns \
         rubin.tag_sn_near_galaxy_candidate \
         rubin.tag_uniform_sample; do
  java -jar ~/Lomikel/lib/Lomikel-Janus-${version}.exe.jar -b -s ~/Lomikel/src/work/CC/importTags.groovy -o "cls='${T}',delay=2"
  done

#!/usr/bin/bash -l
set -eo pipefail
NOW=$(date +"%Y%m%d%H%M%s")
LOG=$(mktemp "${TMPDIR:-/tmp}/processTags-${NOW}-XXXXXXXX.log")
LOCK_DIR="/tmp"
mkdir -p -m 700 "${LOCK_DIR}"
LOCK="${LOCK_DIR}/processTags.lock"

# Keep the lock file: removing a flock file permits two different inodes/owners.
exec 9>"${LOCK}"
if ! flock -n 9; then
  echo "Already processing tags with ${LOCK}" >&2
  exit 75
  fi
  
exec > >(tee -a "${LOG}") 2>&1 || exit 1
cd ~/Lomikel/ant
source ./setup.sh
java -jar ~/Lomikel/lib/Lomikel-Janus-${version}.exe.jar -b -s ~/Lomikel/src/work/CC/processTags.groovy

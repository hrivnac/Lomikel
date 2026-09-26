#!/usr/bin/bash -l
set -eo pipefail
NOW=$(date +"%Y%m%d%H%M%s")
LOG=$(mktemp "${TMPDIR:-/tmp}/fillOCol-${NOW}-XXXXXXXX.log") || exit 1
LOCK_DIR="/tmp"
mkdir -p -m 700 "${LOCK_DIR}" || exit 1
LOCK="${LOCK_DIR}/fillOCol.lock"

# Keep the lock file: removing a flock file permits two different inodes/owners.
exec 9>"${LOCK}" || exit 1
if ! flock -n 9; then
  echo "Already filling OCol with ${LOCK}" >&2
  exit 75
  fi

exec > >(tee -a "${LOG}") 2>&1 || exit 1
cd ~/Lomikel/ant || exit 1
source ./setup.sh || exit 1
exec java -jar ~/Lomikel/lib/Lomikel-Janus-${version}.exe.jar -b -s ~/Lomikel/src/work/IJCLab/fillOCol.groovy
/bin/rm -f ${LOCK} 
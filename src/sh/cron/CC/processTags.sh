#!/usr/bin/bash -l
set -eo pipefail
NOW=$(date +"%Y%m%d%H%M%S")
LOG=$(mktemp "${TMPDIR:-/tmp}/processTags-${NOW}-XXXXXXXX.log")
LOCK_DIR="${HOME}/.cache/Lomikel/cron"
mkdir -p -m 700 "${LOCK_DIR}"
LOCK="${LOCK_DIR}/processTags.lock"
exec 9>"${LOCK}"
if ! flock -n 9; then
  echo "Already processing tags (${LOCK})" >&2
  exit 75
fi
cd ~/Lomikel/ant
source ./setup.sh
java -jar ~/Lomikel/lib/Lomikel-Janus-${version}.exe.jar -b -s ~/Lomikel/src/work/CC/processTags.groovy 2>&1 | tee -a "${LOG}"

#!/bin/bash
# Export labeled LSST alerts from HBase (read-only) and evaluate the PCA+K-means
# classifier against the configured rubin.tag_* classes.
#
#   src/work/Clusters/LSST/run_tag_evaluation.sh [CONFIG] [RUN_DIR]
#
# CONFIG   JSON config (default: tag_classes.json beside this script)
# RUN_DIR  fresh output directory (default: run/lsst-tag-eval/<UTC timestamp>)
# PYTHON   interpreter with NumPy, scikit-learn and (optional) matplotlib
#          (default: python3)
# EXPORT   reuse an existing export directory instead of reading HBase
#
# Results: RUN_DIR/export (alerts, labels, summary) and RUN_DIR/eval
# (report.md, metrics.json, CSV tables, PNG figures, model.json).
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../../../.." && pwd)
config=$(realpath "${1:-$here/tag_classes.json}")
run=${2:-$root/run/lsst-tag-eval/$(date -u +%Y%m%dT%H%M%SZ)}
python=${PYTHON:-python3}
[ -e "$run" ] && { echo "error: $run exists; choose a fresh directory" >&2; exit 1; }
mkdir -p "$(dirname "$run")"
run=$(realpath -m "$run")
mkdir "$run"
if [ -n "${EXPORT:-}" ]; then
  ln -s "$(realpath "$EXPORT")" "$run/export"
else
  (
    cd "$root/ant"
    set +eu   # setup.sh is not written for strict mode
    shopt -s expand_aliases
    # shellcheck disable=SC1091
    source setup.sh > /dev/null 2>&1
    java -jar "../lib/Lomikel-HBase-${version}.exe.jar" -b -q \
      -s "$here/export_tag_alerts.groovy" \
      -o "config='$config',output='$run/export'"
  ) 2>&1 | tee "$run/export.log" | grep -E '^(rubin\.|Exported)|Exception' || true
  [ -f "$run/export/summary.json" ] || { echo "error: export failed, see $run/export.log" >&2; exit 1; }
fi
cp "$config" "$run/config.json"
"$python" -I "$here/evaluate_tags.py" train --config "$run/config.json" --input "$run/export" --output "$run/eval" 2>&1 | tee "$run/eval.log"
echo "Report: $run/eval/report.md"

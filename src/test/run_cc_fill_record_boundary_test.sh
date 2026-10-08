#!/usr/bin/env bash
# Run from any directory; all output is local and no production CC setup runs.
set -euo pipefail
cd "$(dirname "$0")/../.."
M2="${HOME}/.m2/repository"
JANUS="/disk2/opt/janusgraph-full-1.0.0/lib/*"
for artifact in parquet-hadoop parquet-column parquet-common parquet-encoding parquet-format-structures parquet-jackson; do
  if [[ ! -f "$M2/org/apache/parquet/$artifact/1.14.1/$artifact-1.14.1.jar" ]]; then
    mvn -q dependency:get -Dartifact="org.apache.parquet:$artifact:1.14.1" -Dtransitive=false
  fi
done
# Do not mix Groovy 4.0.9 with Maven's 4.0.21, or Parquet 1.13 with 1.14.
CP=$(python3 - "$M2" <<'PY'
import glob, sys
print(':'.join(p for p in glob.glob(sys.argv[1] + '/**/*.jar', recursive=True)
               if '/org/apache/groovy/' not in p
               and '/org/codehaus/groovy/' not in p
               and ('/org/apache/parquet/' not in p or '/1.14.1/' in p)))
PY
)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
javac -proc:none -encoding UTF-8 -cp "$CP:$JANUS:extlib/*" -sourcepath src/java -d "$TMP" \
  src/java/com/Lomikel/Parquet/ParquetReader.java \
  src/java/com/Lomikel/ElasticSearcher/ESClient.java \
  src/java/com/Lomikel/Utils/Timer.java
java -cp "$TMP:$CP:$JANUS:extlib/*" groovy.ui.GroovyMain \
  src/test/cc_fill_record_boundary_test.groovy

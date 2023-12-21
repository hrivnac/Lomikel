#!/usr/bin/bash -l
F=${1}
LOG=/tmp/process.`basename ${F}`
cd ~/Lomikel/ant
source ./setup.sh
/bin/rm -f ${LOG}.log
CLASSPATH="${GREMLIN_CLASSPATH}" ${janusgraph_dir}/bin/gremlin.sh -i "${lomikel_src}/gremlin/start_console.gremlin ${janusgraph_dir}/conf/gremlin-server/Local-IJCLab.properties ${home}" -i "../src/gremlin/start2_console.gremlin" < ${F} | tee ${LOG}.log 2>&1
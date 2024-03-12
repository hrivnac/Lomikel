#!/usr/bin/bash -l
NOW=`date +"%Y%m%d%H%M%s"`
LOG=/tmp/fillSOI-${NOW}.log
LOCK=/tmp/fillSOI.lock 
if [[ -e ${LOCK} ]]; then
  echo "Already filling SOI with ${LOCK}"
  exit
  fi
PID=$$
echo ${PID} > ${LOCK}
cd ~/Lomikel/ant
source ./setup.sh
echo "new com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipies(g).fillSourcesOfInterest('hbase-1.lal.in2p3.fr:2183:ztf:schema_2.2_2.0.0',   50, 10000, null)"  | CLASSPATH="${GREMLIN_CLASSPATH}" ${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_console.gremlin ${janusgraph_dir}/conf/gremlin-server/Local-IJCLab.properties ${home}" | tee -a ${LOG} 2>&1
echo "new com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipies(g).generateAlertsOfInterestCorrelations()"                                                      | CLASSPATH="${GREMLIN_CLASSPATH}" ${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_console.gremlin ${janusgraph_dir}/conf/gremlin-server/Local-IJCLab.properties ${home}" | tee -a ${LOG} 2>&1
/bin/rm -f ${LOCK} 

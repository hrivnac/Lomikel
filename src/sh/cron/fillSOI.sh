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
echo "new com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipies(g).processSourcesOfInterest('hbase-1.lal.in2p3.fr:2183:ztf:', 1000, 1000, null, false, null)" | CLASSPATH="${GREMLIN_CLASSPATH}" ${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_console.gremlin ${janusgraph_dir}/conf/gremlin-server/IJCLab.properties ${home}" | tee -a ${LOG} 2>&1
echo "new com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipies(g).processSourcesOfInterest('hbase-1.lal.in2p3.fr:2183:ztf:', 1000, 1000, new String[]{'SN candidate', 'Kilonova candidate', 'Early SN Ia candidate', 'Ambiguous', '*'},  false, null)" | CLASSPATH="${GREMLIN_CLASSPATH}" ${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_console.gremlin ${janusgraph_dir}/conf/gremlin-server/IJCLab.properties ${home}" | tee -a ${LOG} 2>&1
/bin/rm -f ${LOCK} 

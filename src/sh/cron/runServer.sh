#!/usr/bin/bash -l
NOW=`date +"%Y%m%d%H%M%s"`
LOG=/tmp/runServer-${NOW}.log
LOCK=/tmp/runServer.lock
if [[ -e ${LOCK} ]]; then
  echo "Running Server with ${LOCK}"
  kill `cat ${LOCK}`
  kill -9 `cat ${LOCK}`
  fi
if [[ -e ${LOCK} ]]; then
  echo "  not killed ${LOCK}"
  exit
  fi
/bin/rm -f ${PID}
PID=$$
echo ${PID} > ${LOCK}
cd ~/Lomikel/ant
source ./setup.sh
java -jar /localhome/janusgraph/Lomikel/extlib/jetty-runner-9.4.54.v20240208.jar --path /FinkBrowser --port 8888 --stop-port 8889 --stop-key stop --log FinkBrowserServer.log --out FinkBrowserServer.out ../lib/FinkBrowser.war
/bin/rm -f ${LOCK}

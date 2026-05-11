#!/usr/bin/bash
cd /localhome/janusgraph/Lomikel/ant
source setup.sh
exec ${JAVA_HOME}/bin/java \
-jar ${jetty_runner} \
--path '/FinkBrowser' \
--port ${jetty_port} \
../lib/FinkBrowser.war

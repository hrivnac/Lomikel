#!/usr/bin/bash
source /localhome/janusgraph/Lomikel/ant/setup.sh
exec ${JAVA_HOME}/bin/java \
-jar '/localhome/janusgraph/Lomikel/extlib/jetty-runner-9.4.54.v20240208.jar' \
--path '/FinkBrowser' \
--port 8888 \
/localhome/janusgraph/Lomikel/lib/FinkBrowser.war

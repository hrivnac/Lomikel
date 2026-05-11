#!/usr/bin/bash
source /home/almalinux/Lomikel/ant/setup.sh
exec ${JAVA_HOME}/bin/java \
-jar /home/almalinux/Lomikel/extlib/jetty-runner-9.4.54.v20240208.jar \
--path '/FinkBrowser' \
--port 8888 \
/home/almalinux/Lomikel/lib/FinkBrowser.war

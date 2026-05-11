#!/usr/bin/bash
source /localhome/janusgraph/Lomikel/ant/setup.sh
exec ${JAVA_HOME}/bin/java \
-jar /localhome/janusgraph/Lomikel/lib/Lomikel-server-${version}.exe.jar

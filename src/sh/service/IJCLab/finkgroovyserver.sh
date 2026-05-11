#!/usr/bin/bash
cd /localhome/janusgraph/Lomikel/ant
source setup.sh
exec ${JAVA_HOME}/bin/java \
-jar /localhome/janusgraph/Lomikel/lib/Lomikel-server-${version}.exe.jar

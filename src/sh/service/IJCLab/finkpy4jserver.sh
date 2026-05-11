#!/usr/bin/bash
cd /localhome/janusgraph/Lomikel/ant
source setup.sh
exec ${JAVA_HOME}/bin/java \
-jar ../lib/Lomikel-py4j-${version}.exe.jar

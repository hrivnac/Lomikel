#!/usr/bin/bash
cd /home/almalinux/Lomikel/ant
source setup.sh
exec ${JAVA_HOME}/bin/java \
-jar ../lib/Lomikel-server-${version}.exe.jar

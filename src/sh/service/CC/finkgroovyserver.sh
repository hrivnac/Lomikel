#!/usr/bin/bash
source /home/almalinux/Lomikel/ant/setup.sh
exec ${JAVA_HOME}/bin/java \
-jar '/home/almalinux/Lomikel/lib/Lomikel-server-${version}.exe.jar'

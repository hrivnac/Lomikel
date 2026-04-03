#!/usr/bin/bash
exec /usr/lib/jvm/java-17-openjdk-17.0.18.0.8-1.el9.alma.1.x86_64/bin/java \
-jar '/home/almalinux/Lomikel/extlib/jetty-runner-9.4.54.v20240208.jar' \
--path '/FinkBrowser' \
--port 8888 \
/home/almalinux/Lomikel/lib/FinkBrowser.war

#!/usr/bin/bash
/usr/lib/jvm/java-17-openjdk-17.0.14.0.7-2.el9.alma.1.x86_64/bin/java \
-jar '/localhome/janusgraph/Lomikel/extlib/jetty-runner-9.4.54.v20240208.jar' \
--path '/FinkBrowser' \
--port 8888 \
/localhome/janusgraph/Lomikel/lib/FinkBrowser.war

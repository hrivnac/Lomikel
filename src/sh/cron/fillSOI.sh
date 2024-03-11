#!/usr/bin/bash -l
NOW=`date +"%Y%m%d%H%M%s"`
LOG=/tmp/fillSOI-${NOW}.log
cd ~/Lomikel/ant
source ./setup.sh
echo "new com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipies(g).fillSourcesOfInterest('hbase-1.lal.in2p3.fr:2183:ztf:schema_2.2_2.0.0', 50, 10000, null)"  | gremlin_console_IJCLab | tee ${LOG} 2>&1

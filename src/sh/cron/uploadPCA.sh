#!/usr/bin/bash -l
LOG=/tmp/uploadPCA
cd ~/FinkBrowser/ant
source ./setup.sh
/bin/rm -f ${LOG}.log
../src/sh/importAvroHDFS-IJCLab.sh /user/julien.peloton/archive_avro/pca pca | tee ${LOG}.log 2>&1

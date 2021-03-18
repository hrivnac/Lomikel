#!/bin/sh
PID=`jps | grep Elasticsearch | awk '{print $1}'`
kill ${PID}

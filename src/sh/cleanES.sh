#!/bin/sh
X = ${1}
if [[ "${X}" == "" ]]; then
   curl "http://${elasticsearch_hostname}/_cat/indices?v"
elif [[ "${X}" == "janus" ]]; then
  curl -X DELETE "http://${elasticsearch_hostname}/janusgraph*"
elif [[ "${X}" == "hbase" ]]; then
  for Y in jd radec; do
    curl -X DELETE "http://${elasticsearch_hostname}/${Y}"
    done
  fi


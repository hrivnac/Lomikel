#!/bin/sh
X = ${1}
if [[ "${X}" == "" ]]; then
   curl 'http://${elascticsearch_hostname}/_cat/indices?v'
else if [[ "${X}" == "janus" ]]; then
 
 
curl -X DELETE 'http://${elascticsearch_hostname}/*'

echo "Logging to ../run/janusgraph.log"
${janusgraph_dir}/elasticsearch/bin/elasticsearch -d
${janusgraph_dir}/bin/gremlin-server.sh ${janusgraph_dir}/conf/gremlin-server/jhtools.yaml > ../run/janusgrasp.log 2>&1 &

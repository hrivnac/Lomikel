${janusgraph_dir}/elasticsearch/bin/elasticsearch -d
/bin/rm -f ../run/janusgraph.log
${janusgraph_dir}/bin/gremlin-server.sh ${janusgraph_dir}/conf/gremlin-server/jhtools.yaml > ../run/janusgraph.log 2>&1 &
tail -f ../run/janusgraph.log

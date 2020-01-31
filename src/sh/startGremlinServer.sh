${hbase_dir}/bin/start-hbase.sh
${janusgraph_dir}/bin/gremlin-server.sh ${janusgraph_dir}/conf/gremlin-server/jhtools.yaml > ../run/janusgrasp.log 2>&1 &

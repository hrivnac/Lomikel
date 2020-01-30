source build-global.properties
source build-local.properties

if [[ ! "x" = "x${ant_home}" ]]; then
  export ANT_HOME="${ant_home}"
  export PATH="${ANT_HOME}/bin:${PATH}"
  fi
if [[ ! "x" = "x${java_home}" ]]; then
  export JAVA_HOME="${java_home}"
  export PATH="${JAVA_HOME}/bin:${PATH}"
  fi
  
export janusgraph
export zookeeper
export hbase_table

export home=`pwd`/..

alias gremlin='${janusgraph}/bin/gremlin.sh -i "../src/gremlin/start.gremlin"'
alias gremlin_local='${janusgraph}/bin/gremlin.sh -i "../src/gremlin/start_local.gremlin ${zookeeper} ${hbase_table} ${home}" -i "../src/gremlin/functions.gremlin" -i "../src/gremlin/describe.gremlin"'

echo "commands: gremlin, gremlin_local"

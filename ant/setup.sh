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
if [[ ! "x" = "x${hbase_classpath}" ]]; then
  export CLASSPATH="${hbase_classpath}"
  fi
  
export janusgraph_dir
export zookeeper
export hbase_table
export hbase_dir

export home=`pwd`/..

alias gremlin='${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start.gremlin"'
alias gremlin_local='${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_local.gremlin ${zookeeper} ${hbase_table} ${home}"'

echo "commands: gremlin, gremlin_local"

